(ns frontend.core
  (:require [bodhi.aliasing :as aliasing]
            [bodhi.core :as bodhi]
            [bodhi.param-indexing :as param-indexing]
            [cljs.core.async :as async :refer [chan]]
            [compassus.core :as compassus]
            [figwheel.client.utils :as figwheel-utils]
            [frontend.analytics.core :as analytics]
            [frontend.api :as api]
            [frontend.async :refer [put!]]
            [frontend.browser-settings :as browser-settings]
            [frontend.components.app :as app]
            [frontend.components.app.legacy :as legacy]
            [frontend.config :as config]
            [frontend.controllers.api :as api-con]
            [frontend.controllers.controls :as controls-con]
            [frontend.controllers.errors :as errors-con]
            [frontend.controllers.navigation :as nav-con]
            [frontend.controllers.ws :as ws-con]
            [frontend.history :as history]
            [frontend.instrumentation :refer [wrap-api-instrumentation]]
            [frontend.models.organization :as org]
            [frontend.parser :as parser]
            [frontend.parser.connection :as connection]
            [frontend.pusher :as pusher]
            [frontend.routes :as routes]
            [frontend.send :as send]
            [frontend.state :as state]
            [frontend.utils.state :as state-utils]
            [frontend.support :as support]
            [frontend.timer :as timer]
            [frontend.utils :as utils :refer [mlog set-canonical!]]
            goog.dom
            [goog.events :as gevents]
            [goog.functions :as gfun]
            [om.next :as om-next]
            [schema.core :as s :include-macros true])
  (:require-macros
   [cljs.core.async.macros :as am :refer [alt! go]]
   [frontend.utils :refer [swallow-errors]]))

(when config/client-dev?
  (enable-console-print!)
  (s/set-fn-validation! true))

(defn initial-state
  "Builds the initial app state, including data that comes from the
  renderContext."
  []
  (assoc state/initial-state
         :current-user (-> js/window
                           (aget "renderContext")
                           (aget "current_user")
                           utils/js->clj-kw)
         :render-context (-> js/window
                             (aget "renderContext")
                             utils/js->clj-kw)))

(defn controls-handler
  [value state container comms]
  (mlog "Controls Verbose: " value)
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state]
       (swap! state (partial controls-con/control-event container (first value) (second value)))
       (controls-con/post-control-event! container (first value) (second value) previous-state @state comms)))))

(defn nav-handler
  [[navigation-point {:keys [inner? query-params] :as args} :as value] state history comms]
  (mlog "Navigation Verbose: " value)
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state]
       (swap! state (fn [state]
                      (->> (org/get-from-map args)
                           (state-utils/change-selected-org state)
                           (nav-con/navigated-to history navigation-point args))))
       (nav-con/post-navigated-to! history navigation-point args previous-state @state comms)
       (set-canonical! (:_canonical args))
       (when-not (= navigation-point :navigate!)
         (analytics/track {:event-type :pageview
                           :navigation-point navigation-point
                           :current-state @state}))
       (when-let [app-dominant (goog.dom.getElementByClass "app-dominant")]
         (set! (.-scrollTop app-dominant) 0))
       (when-let [main-body (goog.dom.getElementByClass "main-body")]
         (set! (.-scrollTop main-body) 0))))))

(defn api-handler
  [value state container comms]
  (mlog "API Verbose: " (first value) (second value) (utils/third value))
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state
           message (first value)
           status (second value)
           api-data (utils/third value)]
       (swap! state (wrap-api-instrumentation (partial api-con/api-event container message status api-data)
                                              api-data))
       (api-con/post-api-event! container message status api-data previous-state @state comms)))))

(defn- refresh [app]
  (om-next/transact!
   (compassus/get-reconciler app)
   (into (om-next/transform-reads
          (compassus/get-reconciler app)
          `[:compassus.core/route-data])
         (om-next/transform-reads
          (compassus/get-reconciler app)
          `[:compassus.core/mixin-data]))))

;;; refresh max every second
(def ^:private debounced-refresh (gfun/debounce refresh 1000))

(defn ws-handler
  [value state pusher comms app]
  (mlog "websocket Verbose: " (pr-str (first value)) (second value) (utils/third value))
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state]
       (swap! state (partial ws-con/ws-event pusher (first value) (second value)))
       (ws-con/post-ws-event! pusher (first value) (second value) previous-state @state comms (partial debounced-refresh app))))))

(defn errors-handler
  [value state container comms]
  (mlog "Errors Verbose: " value)
  (swallow-errors
   (binding [frontend.async/*uuid* (:uuid (meta value))]
     (let [previous-state @state]
       (swap! state (partial errors-con/error container (first value) (second value)))
       (errors-con/post-error! container (first value) (second value) previous-state @state comms)))))

(defn find-top-level-node []
  (.-body js/document))

(defn find-app-container []
  (goog.dom/getElement "app"))

(defn subscribe-to-user-channel [user ws-ch]
  (put! ws-ch [:subscribe {:channel-name (pusher/user-channel user)
                           :messages [:refresh]}]))

(defonce application nil)

;; Wraps an atom, but only exposes the portion of its value at path.
(deftype LensedAtom [atom path]
  IDeref
  (-deref [_] (get-in (deref atom) path))

  ISwap
  (-swap! [_ f] (swap! atom update-in path f))
  (-swap! [_ f a] (swap! atom update-in path f a))
  (-swap! [_ f a b] (swap! atom update-in path f a b))
  (-swap! [_ f a b xs] (apply swap! atom update-in path f a b xs))

  IWatchable
  ;; We don't need to notify watches, because our parent atom does that.
  (-notify-watches [_ _ _] nil)
  (-add-watch [this key f]
    ;; "Namespace" the key in the parent's watches with this object.
    (add-watch atom [this key]
               (fn [[_ key] _ old-state new-state]
                 (f key this
                    (get-in old-state path)
                    (get-in new-state path))))
    this)
  (-remove-watch [this key]
    (remove-watch atom [this key])))

(defn- force-update-recursive
  ".forceUpdate root-component and every component within it.

  This function uses React internals and should only be used for development
  tasks such as code reloading."
  [root-component]
  (letfn [(js-vals [o]
            (map #(aget o %) (js-keys o)))
          ;; Finds the children of a React internal instance of a component.
          ;; That could be a single _renderedComponent or several
          ;; _renderedChildren.
          (children [ic]
            (or (some-> (.-_renderedComponent ic) vector)
                (js-vals (.-_renderedChildren ic))))
          (descendant-components [c]
            ;; Walk the tree finding tall of the descendent internal instances...
            (->> (tree-seq #(seq (children %)) children (.-_reactInternalInstance c))
                 ;; ...map to the public component instances...
                 (map #(.-_instance %))
                 ;; ...and remove the nils, which are from DOM nodes.
                 (remove nil?)))]
    (doseq [c (descendant-components root-component)]
      (.forceUpdate c))))

(defn- compassus-page-queuing-merge
  "Merge middleware. Queues the entire Compassus page to re-read after merge. A
  subtler approach may be possible in the future, but for now, this works."
  [next-merge]
  (fn [env]
    (-> (next-merge env)
        (update :keys conj :compassus.core/route-data))))

(defn- patched-basic-merge
  "A patched version of bodhi/basic-merge. This should be swapped out
  for bodhi/basic-merge when its handling of unnormalized data is
  fixed."
  [{:keys [merge merger state path novelty ast] :as env}]
  (let [{:keys [key type]} ast]
    (case type
      :prop {:keys #{}
             :next (assoc-in state path novelty)}
      :join (cond
              ;; Note that we use `:merge` here and not `merger`, which would go
              ;; one level deeper into the query/ast. This is analogous to
              ;; `basic-read` using `:read` instead of `:parser` when processing
              ;; vectors.
              (vector? novelty)
              (reduce-kv
               (fn [result idx novelty']
                 (bodhi/update-merge-result result
                                            (merge (-> env
                                                       (update :path conj idx)
                                                       (assoc :novelty novelty'
                                                              :state (:next result))))))
               {:keys #{}
                :next (update-in state
                                 path
                                 (fn [old-vals]
                                   (subvec (vec old-vals)
                                           0
                                           (min (count novelty)
                                                ;; `(count nil)` is 0
                                                (count old-vals)))))}
               novelty)

              (nil? novelty)
              {:keys #{} :next (assoc-in state path nil)}

              :else
              (merger env)))))

(defn ^:export setup! []
  (let [legacy-state (initial-state)
        comms {:controls (chan)
               :api (chan)
               :errors (chan)
               :nav (chan)
               :ws (chan)}
        top-level-node (find-top-level-node)
        container (find-app-container)
        history-imp (history/new-history-imp top-level-node)
        pusher-imp (pusher/new-pusher-instance (config/pusher))
        state-atom (atom {:legacy/state legacy-state
                          :app/current-user (when-let [rc-user (-> js/window
                                                                   (aget "renderContext")
                                                                   (aget "current_user"))]
                                              {:user/login (aget rc-user "login")
                                               :user/bitbucket-authorized? (aget rc-user "bitbucket_authorized")
                                               :user/github-oauth-scopes (-> rc-user
                                                                             (aget "github_oauth_scopes")
                                                                             js->clj)
                                               :user/identities (let [legacy-format-identites
                                                                      (-> rc-user
                                                                          (aget "identities")
                                                                          (js->clj :keywordize-keys true)
                                                                          vals)]
                                                                  (mapv
                                                                   #(hash-map :identity/type (:type %)
                                                                              :identity/login (:login %))
                                                                   legacy-format-identites))})})

        ;; The legacy-state-atom is a LensedAtom which we can treat like a
        ;; normal atom but which presents only the legacy state.
        legacy-state-atom (LensedAtom. state-atom [:legacy/state])

        a (compassus/application
           {:routes app/routes
            :index-route app/index-route
            :mixins [(compassus/wrap-render app/Wrapper)]
            :reconciler (om-next/reconciler
                         {:state state-atom
                          :normalize true
                          :parser parser/parser
                          :send send/send

                          ;; Workaround for
                          ;; https://github.com/omcljs/om/issues/781
                          :merge-tree #(utils/deep-merge %1 %2)

                          :merge (bodhi/merge-fn
                                  (-> patched-basic-merge

                                      ;; bodhi/basic-merge
                                      ;; see docstring for patched-basic-merge


                                      ;; TODO: re-enable normalization
                                      ;; by uncommenting
                                      ;; `default-db/merge` when bodhi
                                      ;; basic-merge can work with it

                                      ;; default-db/merge
                                      param-indexing/merge
                                      connection/merge
                                      aliasing/merge
                                      compassus-page-queuing-merge))

                          :shared {:comms comms
                                   :timer-atom (timer/initialize)
                                   :track-event #(analytics/track (assoc % :current-state @legacy-state-atom))
                                   ;; Make the legacy-state-atom available to the legacy inputs system.
                                   :_app-state-do-not-use legacy-state-atom}})})]

    (support/enable-one! {:in-beta-program (get-in legacy-state state/user-in-beta-path)})
    (set! application a)

    (browser-settings/setup! legacy-state-atom)

    (routes/define-routes! (:current-user legacy-state) application (:nav comms))

    (compassus/mount! application (goog.dom/getElement "app"))

    (when config/client-dev?
      ;; Re-render when Figwheel reloads.
      (gevents/listen js/document.body
                      "figwheel.js-reload"
                      #(force-update-recursive (om-next/app-root (compassus/get-reconciler a)))))

    (go
      (while true
        (alt!
          (:controls comms) ([v] (controls-handler v legacy-state-atom container comms))
          (:nav comms) ([v] (nav-handler v legacy-state-atom history-imp comms))
          (:api comms) ([v] (api-handler v legacy-state-atom container comms))
          (:ws comms) ([v] (ws-handler v legacy-state-atom pusher-imp comms a))
          (:errors comms) ([v] (errors-handler v legacy-state-atom container comms)))))

    (when (config/enterprise?)
      (api/get-enterprise-site-status (:api comms)))

    (if-let [error-status (get-in legacy-state [:render-context :status])]
      ;; error codes from the server get passed as :status in the render-context
      (routes/open-to-inner! application (:nav comms) :error {:status error-status})
      (routes/dispatch! (str "/" (.getToken history-imp))))
    (when-let [user (:current-user legacy-state)]
      (subscribe-to-user-channel user (:ws comms)))
    (analytics/init! legacy-state-atom)
    (api/get-orgs (:api comms) :include-user? true)))

(defn ^:export toggle-admin []
  (swap! (om-next/app-state (compassus/get-reconciler application))
         update-in [:legacy/state :current-user :admin] not))

(defn ^:export toggle-dev-admin []
  (swap! (om-next/app-state (compassus/get-reconciler application))
         update-in [:legacy/state :current-user :dev-admin] not))

(defn ^:export explode []
  (swallow-errors
   (assoc [] :deliberate :exception)))


;; Figwheel offers an event when JS is reloaded but not when CSS is reloaded. A
;; PR is waiting to add this; until then fire that event from here.
;; See: https://github.com/bhauman/lein-figwheel/pull/463
(defn handle-css-reload [files]
  (figwheel-utils/dispatch-custom-event "figwheel.css-reload" files))
