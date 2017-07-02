(ns frontend.controllers.controls
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [cljs.reader :as reader]
            [clojure.set :as set]
            [frontend.analytics.core :as analytics]
            [frontend.analytics.utils :as analytics-utils]
            [frontend.analytics.track :as analytics-track]
            [frontend.api :as api]
            [frontend.async :refer [put!]]
            [frontend.api.path :as api-path]
            [frontend.components.forms :refer [release-button!]]
            [frontend.models.action :as action-model]
            [frontend.models.project :as project-model]
            [frontend.models.build :as build-model]
            [frontend.models.feature :as feature]
            [frontend.models.plan :as plan]
            [frontend.intercom :as intercom]
            [frontend.support :as support]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.stripe :as stripe]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.build :as build-utils]
            [frontend.utils.map :as map-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.launchdarkly :as launchdarkly]
            [frontend.utils.seq :refer [dissoc-in]]
            [frontend.utils.state :as state-utils]
            [goog.dom]
            [goog.string :as gstring]
            [goog.labs.userAgent.engine :as engine]
            goog.style
            [frontend.models.user :as user-model]
            [frontend.pusher :as pusher])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]])
  (:import [goog.fx.dom.Scroll]))

;; --- Helper Methods ---

(defn container-id [container]
  (int (last (re-find #"container_(\d+)" (.-id container)))))


(defn extract-from
  "Extract data from a nested map. Returns a new nested map comprising only the
  nested keys from `path`.

  user=> (extract-from nil nil)
  nil
  user=> (extract-from nil [])
  nil
  user=> (extract-from nil [:a])
  nil
  user=> (extract-from {} [:a])
  nil
  user=> (extract-from {:a 1} [:a])
  {:a 1}
  user=> (extract-from {:a {:b {:c 1}}, :d 2} [:a :b])
  {:a {:b {:c 1}}}"
  [m path]
  (when (seq path)
    (let [sentinel (js-obj)
          value (get-in m path sentinel)]
      (when-not (identical? value sentinel)
        (assoc-in {} path value)))))

(defn merge-settings
  "Merge new settings from inputs over a subset of project settings."
  [paths project settings]
  (letfn []
    (if (not (seq paths))
      settings
      (utils/deep-merge (apply merge {} (map (partial extract-from project) paths))
                        settings))))

(defn button-ajax
  "An ajax/ajax wrapper that releases the current managed-button after the API
  request.  Exists to faciliate migration away from stateful-button."
  [method url message channel & opts]
  (let [uuid frontend.async/*uuid*
        c (chan)
        events (-> (apply hash-map opts)
                   :events)]
    (apply ajax/ajax method url message c opts)
    (go-loop []
      (when-let [[_ status _ :as event] (<! c)]
        (when-let [event-handler (-> events status)]
          (event-handler))
        (when (#{:success :failed} status)
          (release-button! uuid status))
        (>! channel event)
        (recur)))))

(defn toggle-project
  "Toggle follow and unfollow projects."
  [current-state comms vcs-url context control-event follow-path]
  (let [api-ch (:api comms)
        login (get-in current-state state/user-login-path)
        project (vcs-url/project-name vcs-url)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)
        vcs-type (vcs-url/vcs-type vcs-url)]

      (button-ajax :post
                   (follow-path vcs-type project)
                   control-event
                   api-ch
                   :context context)))

(defn set-error-banner!
  [comms value banner-key]
  (put! (:controls comms) [banner-key value]))

(defn maybe-set-org-settings-error-banner!
  [comms api-result]
  (let [status-code (:status-code api-result)]
    (cond
      (= status-code 402) (set-error-banner! comms true :toggle-stripe-error-banner)
      (= status-code 403) (set-error-banner! comms true :toggle-admin-error-banner))))

(defn steps-changed
  [steps inputs]
  (->> steps
       (map (fn [step]
              [(-> step name (str "_changed") keyword)
               (-> inputs step boolean)]))
       (into {})))

;; --- Navigation Multimethod Declarations ---

(defmulti control-event
  ;; target is the DOM node at the top level for the app
  ;; message is the dispatch method (1st arg in the channel vector)
  ;; state is current state of the app
  ;; return value is the new state
  (fn [target message args state] message))

(defmulti post-control-event!
  (fn [target message args previous-state current-state comms] message))

;; --- Navigation Multimethod Implementations ---

(defmethod control-event :default
  [target message args state]
  (utils/mlog "Unknown controls: " message)
  state)

(defmethod post-control-event! :default
  [target message args previous-state current-state comms]
  (utils/mlog "No post-control for: " message))


(defmethod control-event :user-menu-toggled
  [target message _ state]
  (update-in state [:settings :menus :user :open] not))

(defmethod control-event :show-all-branches-toggled
  [target message value state]
  (assoc-in state state/show-all-branches-path value))

(defmethod post-control-event! :show-all-branches-toggled
  [target message value previous-state current-state comms]
  (let [element-state (fn [state]
                        (condp = (get-in state state/show-all-branches-path)
                          true "All branches"
                          false "My branches"))]
    (analytics/track {:event-type :show-all-branches-toggled
                      :current-state current-state
                      :properties {:current-state (element-state current-state)
                                   :previous-state (element-state previous-state)}})))

(defmethod control-event :show-all-builds-toggled
  [target message value state]
  (assoc-in state state/show-all-builds-path value))

(defmethod post-control-event! :show-all-builds-toggled
  [target message value previous-state current-state comms]
  (let [element-state (fn [state]
                        (condp = (get-in state state/show-all-builds-path)
                          true "All builds"
                          false "My builds"))
        all? (get-in current-state state/show-all-builds-path)]
    (api/get-dashboard-builds (assoc (state/navigation-data current-state)
                                     :builds-per-page (:builds-per-page current-state)
                                     :all? all?)
                              (:api comms))
    (analytics/track {:event-type :show-all-builds-toggled
                      :current-state current-state
                      :properties {:current-state (element-state current-state)
                                   :previous-state (element-state previous-state)}})))

(defmethod control-event :expand-repo-toggled
  [target message {:keys [repo]} state]
  (update-in state state/expanded-repos-path (fn [expanded-repos]
                                               ((if (expanded-repos repo)
                                                  disj
                                                  conj)
                                                expanded-repos repo))))

(defmethod post-control-event! :expand-repo-toggled
  [target message {:keys [repo]} previous-state current-state comms]
  (let [expanded-state (fn [state repo]
                         (condp = (-> state
                                      (get-in state/expanded-repos-path)
                                      (contains? repo))
                           true "expanded"
                           false "unexpanded"))]
    (analytics/track {:event-type :expand-repo-toggled
                      :current-state current-state
                      :properties {:previous-state (expanded-state previous-state repo)
                                   :current-state (expanded-state current-state repo)}})))

(defmethod control-event :sort-branches-toggled
  [target message value state]
  (assoc-in state state/sort-branches-by-recency-path value))

(defmethod post-control-event! :sort-branches-toggled
  [target message value previous-state current-state comms]
  (let [element-state (fn [state]
                        (if (get-in state state/sort-branches-by-recency-path)
                          "Recent"
                          "By Project"))]
   (analytics/track {:event-type :sort-branches-toggled
                     :current-state current-state
                     :properties {:current-state (element-state current-state)
                                  :previous-state (element-state previous-state)}})))

(defmethod control-event :collapse-branches-toggled
  [target message {:keys [collapse-group-id]} state]
  ;; Lets us store this in localstorage without leaking info about the user
  (update-in state (state/project-branches-collapsed-path collapse-group-id) not))

(defmethod control-event :show-admin-panel-toggled
  [target message _ state]
  (update-in state state/show-admin-panel-path not))

(defmethod control-event :instrumentation-line-items-toggled
  [target message _ state]
  (update-in state state/show-instrumentation-line-items-path not))

(defmethod control-event :clear-instrumentation-data-clicked
  [target message _ state]
  (assoc-in state state/instrumentation-path []))

(defmethod control-event :show-inspector-toggled
  [target message _ state]
  (update-in state state/show-inspector-path not))

(defmethod control-event :usage-queue-why-toggled
  [target message {:keys [build-id]} state]
  (update-in state state/show-usage-queue-path not))

(defmethod post-control-event! :usage-queue-why-showed
  [target message {:keys [username reponame
                          build_num build-id]} previous-state current-state comms]
  (let [api-ch (:api comms)]
    (api/get-usage-queue (get-in current-state state/build-path) api-ch)))

(defmethod control-event :selected-add-projects-org
  [target message args state]
  (-> state
      (assoc-in [:settings :add-projects :selected-org] args)
      (assoc-in [:settings :add-projects :repo-filter-string] "")
      (state-utils/reset-current-org)))

(defmethod post-control-event! :selected-add-projects-org
  [target message {:keys [vcs_type login admin]} previous-state current-state comms]
  (let [api-ch (:api comms)]
    (when (and admin
            (user-model/has-org? (get-in current-state state/user-path) login vcs_type))
      (api/get-org-plan login vcs_type api-ch :org-plan)))
  (utils/scroll-to-id! "project-listing"))

(defmethod control-event :refreshed-user-orgs
  [target message args state]
  (let [current-user (get-in state state/user-path)]
    (-> state
        (assoc-in state/user-organizations-path nil)
        (assoc-in state/repos-path [])
        (assoc-in state/github-repos-loading-path (user-model/github-authorized? current-user))
        (assoc-in state/bitbucket-repos-loading-path (user-model/bitbucket-authorized? current-user)))))

(defmethod post-control-event! :refreshed-user-orgs [target message args previous-state current-state comms]
  (let [api-ch (:api comms)
        load-gh-repos? (get-in current-state state/github-repos-loading-path)
        load-bb-repos? (get-in current-state state/bitbucket-repos-loading-path)]
    (api/get-orgs api-ch :include-user? true)
    (when load-gh-repos?
      (api/get-github-repos api-ch))
    (when load-bb-repos?
      (api/get-bitbucket-repos api-ch))))

(defmethod post-control-event! :artifacts-showed
  [target message _ previous-state current-state comms]
  (let [api-ch (:api comms)
        build (get-in current-state state/build-path)
        vcs-type (vcs-url/vcs-type (:vcs_url build))
        project-name (vcs-url/project-name (:vcs_url build))
        build-num (:build_num build)]
    (ajax/ajax :get
               (api-path/artifacts vcs-type project-name build-num)
               :build-artifacts
               api-ch
               :context (build-model/id build))))

(defmethod post-control-event! :tests-showed
  [target message _ previous-state current-state comms]
  (let [api-ch (:api comms)
        build (get-in current-state state/build-path)]
    (when (empty? (get-in current-state state/tests-path))
      (api/get-build-tests build api-ch))))

(defmethod control-event :show-config-toggled
  [target message build-id state]
  (update-in state state/show-config-path not))

(defmethod control-event :container-paging-offset-changed
  [target message {:keys [paging-offset]} state]
  (assoc-in state state/container-paging-offset-path paging-offset))

(defmethod control-event :container-filter-changed
  [target message {:keys [new-filter containers]} state]
  (if (= new-filter (get-in state state/current-container-filter-path))
    state
    (-> state
        (assoc-in state/current-container-filter-path new-filter)
        ;; A nil paging-offset means "display whatever page the selected container is on".
        (assoc-in state/container-paging-offset-path nil))))

(defmethod post-control-event! :container-filter-changed
  [target message {:keys [new-filter containers]} previous-state current-state comms]
  (let [selected-container-id (state/current-container-id current-state)
        selected-container-in-containers? (some #(= selected-container-id (:index %)) containers)]
    (when-not (and selected-container-in-containers?
                   (seq containers))
      (let [nav-ch (:nav comms)
            build (get-in current-state [:current-build-data :build])
            {:keys [vcs_type username reponame build_num]} build
            current-or-default-tab (or (get-in current-state state/navigation-tab-path)
                                       (build-utils/default-tab build (get-in current-state state/project-scopes-path)))]
        (put! nav-ch [:navigate! {:path (routes/v1-build-path
                                          vcs_type
                                          username
                                          reponame
                                          nil
                                          build_num
                                          current-or-default-tab
                                          (-> containers first :index))}]))))
  (analytics/track {:event-type :container-filter-changed
                    :current-state current-state
                    :properties {:new-filter new-filter}}))

(defmethod control-event :action-log-output-toggled
  [target message {:keys [index step value]} state]
  (assoc-in state (state/show-action-output-path index step) value))

(defmethod post-control-event! :action-log-output-toggled
  [target message {:keys [index step] :as args} previous-state current-state comms]
  (let [action (get-in current-state (state/action-path index step))
        build (get-in current-state state/build-path)]
    (when (and (action-model/visible? action (state/current-container-id current-state))
               (not (:output action)))
      (api/get-action-output {:vcs-url (:vcs_url build)
                              :build-num (:build_num build)
                              :step step
                              :index index}
                             (:api comms)))))


(defmethod control-event :selected-project-parallelism
  [target message {:keys [project-id parallelism]} state]
  (assoc-in state (conj state/project-path :parallel) parallelism))

(defmethod post-control-event! :selected-project-parallelism
  [target message {:keys [project-id parallelism]} previous-state current-state comms]
  (when (not= (get-in previous-state state/project-path)
              (get-in current-state state/project-path))
    (let [previous-project (get-in previous-state state/project-path)
          new-project (get-in current-state state/project-path)
          api-ch (:api comms)
          project-name (vcs-url/project-name project-id)
          org-name (vcs-url/org-name project-id)
          repo-name (vcs-url/repo-name project-id)
          vcs-type (vcs-url/vcs-type project-id)]
      ;; TODO: edit project settings api call should respond with updated project settings
      (ajax/ajax :put
                 (api-path/project-settings vcs-type org-name repo-name)
                 :update-project-parallelism
                 api-ch
                 :params {:parallel parallelism}
                 :context {:project-id project-id})
      (analytics/track {:event-type :update-parallelism-clicked
                        :current-state current-state
                        :properties {:previous-parallelism (project-model/parallelism previous-project)
                                     :new-parallelism (project-model/parallelism new-project)
                                     :plan-type (analytics-utils/canonical-plan-type :paid)
                                     :vcs-type vcs-type}}))))

(defmethod post-control-event! :clear-cache
  [target message {:keys [type project-id]} previous-state current-state comms]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (:api comms)
        uuid frontend.async/*uuid*]
    (ajax/ajax :delete
               (api-path/project-cache vcs-type project-name type)
               (case type
                 "build" :clear-build-cache
                 "source" :clear-source-cache)
               api-ch
               :context {:project-id project-id
                         :uuid uuid})))

(defmethod control-event :dismiss-result
  [target message result-path state]
  (update-in state (butlast result-path) dissoc (last result-path)))

(defmethod control-event :dismiss-invite-form
  [target message _ state]
  (assoc-in state state/dismiss-invite-form-path true))

(defmethod post-control-event! :dismiss-invite-form
  [_ _ _ _ current-state comms]
  (analytics/track {:event-type :invite-teammates-dismissed
                    :current-state current-state}))

(defmethod control-event :invite-selected-all
  [_ _ _ state]
  (update-in state
             state/build-invite-members-path
             (fn [users]
               (mapv #(if (utils/valid-email? (:email %))
                        (assoc % :checked true)
                        %)
                     users))))

(defmethod post-control-event! :invite-selected-all
  [_ _ _ _ current-state comms]
  (let [teammates (get-in current-state state/build-invite-members-path)]
    (analytics/track {:event-type :invite-teammates-select-all-clicked
                      :current-state current-state
                      :properties {:teammate-count (count teammates)}})))

(defmethod control-event :invite-selected-none
  [_ _ _ state]
  (update-in state
             state/build-invite-members-path
             (fn [users]
               (vec (map #(assoc % :checked false) users)))))

(defmethod post-control-event! :invite-selected-none
  [_ _ _ _ current-state comms]
  (let [teammates (get-in current-state state/build-invite-members-path)]
    (analytics/track {:event-type :invite-teammates-select-none-clicked
                      :current-state current-state
                      :properties {:teammate-count (count teammates)}})))

(defmethod control-event :dismiss-config-errors
  [target message _ state]
  (assoc-in state state/dismiss-config-errors-path true))


(defmethod control-event :edited-input
  [target message {:keys [value path]} state]
  (assoc-in state path value))


(defmethod control-event :toggled-input
  [target message {:keys [path]} state]
  (update-in state path not))

(defmethod control-event :clear-inputs
  ;; assumes that paths are relative to inputs, e.g. [:new-env-var], not [:inputs :new-env-var]
  [target message {:keys [paths]} state]
  (reduce (fn [state path]
            (dissoc-in state (concat state/inputs-path path)))
          state paths))


(defmethod post-control-event! :support-dialog-raised
  [target message _ previous-state current-state comms]
  (support/raise-dialog (:errors comms)))


(defmethod post-control-event! :intercom-user-inspected
  [target message criteria previous-state current-state comms]
  (if-let [url (intercom/user-link)]
    (js/window.open url)
    (print "No matching url could be found from current window.location.pathname")))


(defn retry-build
  [api-ch {:keys [vcs-url build-num reponame ref-name no-cache? ssh?]}]
  (let [vcs-type (vcs-url/vcs-type vcs-url)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)
        target-url (api-path/build-retry vcs-type org-name repo-name build-num ssh?)]
    (ajax/ajax :post target-url :retry-build api-ch
               :params (when no-cache? {:no-cache true})
               :context {:no-cache? no-cache?
                         :ssh? ssh?
                         :reponame reponame
                         :ref-name ref-name
                         :button-uuid frontend.async/*uuid*})))

(defmethod post-control-event! :retry-build-clicked
  [target message args previous-state current-state comms]
  (retry-build (:api comms) (select-keys args [:vcs-url :build-num :reponame :ref-name :no-cache?]))
  (let [vcs-url (:vcs-url args)]
    (analytics-track/rebuild-clicked {:current-state current-state
                                      :vcs-type (vcs-url/vcs-type vcs-url)
                                      :org-name (vcs-url/org-name vcs-url)
                                      :repo-name (vcs-url/repo-name vcs-url)
                                      :component (:component args)
                                      :ssh? false
                                      :no-cache? (:no-cache? args)})))

(defmethod post-control-event! :ssh-build-clicked
  [target message args previous-state current-state comms]
  (retry-build (:api comms) (assoc (select-keys args [:vcs-url :build-num :reponame :ref-name])
                              :ssh? true))
  (let [vcs-url (:vcs-url args)]
    (analytics-track/rebuild-clicked {:current-state current-state
                                      :vcs-type (vcs-url/vcs-type vcs-url)
                                      :org-name (vcs-url/org-name vcs-url)
                                      :repo-name (vcs-url/repo-name vcs-url)
                                      :component (:component args)
                                      :ssh? true
                                      :no-cache? false})))

(defmethod post-control-event! :ssh-current-build-clicked
  [target message {:keys [build-num vcs-url]} previous-state current-state comms]
  (let [api-ch (:api comms)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)
        vcs-type (vcs-url/vcs-type vcs-url)
        uuid frontend.async/*uuid*]
    (go
      (let [api-result (<! (ajax/managed-ajax :post (gstring/format "/api/v1.1/project/%s/%s/%s/%s/ssh-users" vcs-type org-name repo-name build-num)))]
        (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :followed-repo
  [target message repo previous-state current-state comms]
  (toggle-project current-state comms (:vcs_url repo) repo
                  :follow-repo api-path/project-follow))

(defmethod control-event :inaccessible-org-toggled
  [target message {:keys [org-name value]} state]
  (assoc-in state [:settings :add-projects :inaccessible-orgs org-name :visible?] value))


(defmethod post-control-event! :followed-project
  [target message {:keys [vcs-url project-id]} previous-state current-state comms]
  (toggle-project current-state comms vcs-url {:project-id project-id}
                  :follow-project api-path/project-follow))


(defmethod post-control-event! :unfollowed-repo
  [target message repo previous-state current-state comms]
  (toggle-project current-state comms (:vcs_url repo) repo
                  :unfollow-repo api-path/project-unfollow))

(defmethod post-control-event! :unfollowed-repos
  [target message repos previous-state current-state comms]
  (let [controls-ch (:controls comms)]
    (do
      (doseq [repo repos]
        (toggle-project current-state comms (:vcs_url repo) repo
                        :unfollow-repo api-path/project-unfollow))
      (put! controls-ch [:refreshed-user-orgs {}]))))


(defmethod post-control-event! :unfollowed-project
  [target message {:keys [vcs-url project-id] :as repo} previous-state current-state comms]
  (toggle-project current-state comms vcs-url {:project-id project-id}
                  :unfollow-project api-path/project-unfollow))

(defmethod post-control-event! :stopped-building-project
  [target message {:keys [vcs-url project-id] :as args} previous-state current-state comms]
  (let [api-ch (:api comms)
        login (get-in current-state state/user-login-path)
        vcs-type (vcs-url/vcs-type vcs-url)
        project (vcs-url/project-name vcs-url)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)]
    (button-ajax :delete
                 (api-path/project-enable vcs-type project)
                 :stop-building-project
                 api-ch
                 :context (select-keys args [:project-id :on-success])
                 :events {:success #(analytics/track {:event-type :project-builds-stopped
                                                      :current-state current-state
                                                      :properties {:org org-name
                                                                   :repo repo-name}})})))

;; XXX: clean this up
(defmethod post-control-event! :container-parent-scroll
  [target message _ previous-state current-state comms]
  (let [controls-ch (:controls comms)
        current-container-id (state/current-container-id current-state)
        parent (goog.dom/getElement "container_parent")
        parent-scroll-left (.-scrollLeft parent)
        current-container (goog.dom/getElement (str "container_" current-container-id))
        current-container-scroll-left (int (.-x (goog.style.getContainerOffsetToScrollInto current-container parent)))
        ;; XXX stop making (count containers) queries on each scroll
        containers (sort-by (fn [c] (Math/abs (- parent-scroll-left (.-x (goog.style.getContainerOffsetToScrollInto c parent)))))
                            (utils/node-list->seqable (goog.dom/getElementsByClass "container-view" parent)))
        new-scrolled-container-id (if (= parent-scroll-left current-container-scroll-left)
                                    current-container-id
                                    (if-not (engine/isGecko)
                                      ;; Safari and Chrome scroll the found content to the center of the page
                                      (container-id (first containers))
                                      ;; Firefox scrolls the content just into view
                                      ;; if we're scrolling left, then we want the container whose rightmost portion is showing
                                      ;; if we're scrolling right, then we want the container whose leftmost portion is showing
                                      (if (< parent-scroll-left current-container-scroll-left)
                                        (apply min (map container-id (take 2 containers)))
                                        (apply max (map container-id (take 2 containers))))))]
    ;; This is kind of dangerous, we could end up with an infinite loop. Might want to
    ;; do a swap here (or find a better way to structure this!)
    (when (not= current-container-id new-scrolled-container-id)
      (put! controls-ch [:container-selected {:container-id new-scrolled-container-id
                                              :animate? false}]))))


(defmethod post-control-event! :started-edit-settings-build
  [target message {:keys [project-id]} previous-state current-state comms]
  (let [repo-name (vcs-url/repo-name project-id)
        org-name (vcs-url/org-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        uuid frontend.async/*uuid*
        default-branch (get-in current-state state/project-default-branch-path)
        branch (get-in current-state state/input-settings-branch-path default-branch)]
    ;; TODO: edit project settings api call should respond with updated project settings
    (go
     (let [api-result (<! (ajax/managed-ajax :post (api-path/branch-path vcs-type org-name repo-name branch)))]
       (put! (:api comms) [:start-build (:status api-result) api-result])
       (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :created-env-var
  [target message {:keys [project-id on-success]} previous-state current-state comms]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (:api comms)]
    (button-ajax :post
                 (gstring/format "/api/v1.1/project/%s/%s/envvar" vcs-type project-name)
                 :create-env-var
                 api-ch
                 :params {:name (get-in current-state (conj state/inputs-path :new-env-var-name))
                          :value (get-in current-state (conj state/inputs-path :new-env-var-value))}
                 :context {:project-id project-id
                           :on-success on-success})))

(defmethod post-control-event! :import-env-vars
  [target message {:keys [src-project-vcs-url dest-project-vcs-url env-vars on-success]} previous-state current-state comms]
  (let [api-ch (:api comms)
        vcs-type (vcs-url/vcs-type src-project-vcs-url)
        project-name (vcs-url/project-name src-project-vcs-url)]
    (button-ajax :post
      (gstring/format "/api/v1.1/project/%s/%s/info/export-environment" vcs-type project-name)
      :import-env-vars
      api-ch
      :params {:projects [dest-project-vcs-url]
               :env-vars env-vars}
      :context {:dest-vcs-url dest-project-vcs-url
                :src-vcs-url src-project-vcs-url
                :on-success on-success})))

(defmethod post-control-event! :deleted-env-var
  [target message {:keys [project-id env-var-name]} previous-state current-state comms]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (:api comms)]
    (ajax/ajax :delete
               (gstring/format "/api/v1.1/project/%s/%s/envvar/%s" vcs-type project-name env-var-name)
               :delete-env-var
               api-ch
               :context {:project-id project-id
                         :env-var-name env-var-name})))


(defmethod post-control-event! :saved-dependencies-commands
  [target message {:keys [project-id]} previous-state current-state comms]
  (let [project-name (vcs-url/project-name project-id)
        project (get-in current-state state/project-path)
        inputs (get-in current-state state/inputs-path)
        dependency-steps [:setup :dependencies :post_dependencies]
        settings (state-utils/merge-inputs project
                                           inputs
                                           dependency-steps)
        org (vcs-url/org-name project-id)
        repo (vcs-url/repo-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        uuid frontend.async/*uuid*]
    (go
     (let [api-result (<! (ajax/managed-ajax
                            :put (api-path/project-settings vcs-type org repo)
                            :params settings))
           status (:status api-result)
           steps-changed (steps-changed dependency-steps inputs)
           track-properties (merge {:outcome status} steps-changed)]
       (analytics/track {:event-type :dependency-commands-saved
                         :current-state current-state
                         :properties track-properties})
       (if (= :success status)
         (let [settings-api-result (<! (ajax/managed-ajax :get (api-path/project-settings vcs-type org repo)))]
           (put! (:api comms) [:project-settings (:status settings-api-result) (assoc settings-api-result :context {:project-name project-name})])
           (put! (:controls comms) [:clear-inputs {:paths (map vector (keys settings))}])
           (put! (:nav comms) [:navigate! {:path (routes/v1-project-settings-path {:org org :repo repo :vcs_type vcs-type :_fragment "tests"})}]))
         (put! (:errors comms) [:api-error api-result]))
       (release-button! uuid status)))))


(defmethod post-control-event! :saved-test-commands
  [target message {:keys [project-id start-build?]} previous-state current-state comms]
  (let [project-name (vcs-url/project-name project-id)
        project (get-in current-state state/project-path)
        inputs (get-in current-state state/inputs-path)
        test-steps [:test :extra]
        settings (state-utils/merge-inputs project inputs test-steps)
        branch (get inputs :settings-branch (:default_branch project))
        vcs-type (vcs-url/vcs-type project-id)
        org (vcs-url/org-name project-id)
        repo (vcs-url/repo-name project-id)
        uuid frontend.async/*uuid*]
    (go
     (let [api-result (<! (ajax/managed-ajax :put (api-path/project-settings vcs-type org repo) :params settings))
           status (:status api-result)
           steps-changed (steps-changed test-steps inputs)
           track-properties (merge {:outcome status
                                    :start-build (boolean start-build?)}
                                   steps-changed)]
       (analytics/track {:event-type :test-commands-saved
                         :current-state current-state
                         :properties track-properties})
       (if (= :success status)
         (let [settings-api-result (<! (ajax/managed-ajax :get (api-path/project-settings vcs-type org repo)))]
           (put! (:api comms) [:project-settings (:status settings-api-result) (assoc settings-api-result :context {:project-name project-name})])
           (put! (:controls comms) [:clear-inputs {:paths (map vector (keys settings))}])
           (when start-build?
             (let [build-api-result (<! (ajax/managed-ajax :post (api-path/branch-path vcs-type org repo branch)))]
               (put! (:api comms) [:start-build (:status build-api-result) build-api-result]))))
         (put! (:errors comms) [:api-error api-result]))
       (release-button! uuid status)))))

(defn save-project-settings
  "Takes the state of project settings inputs and PUTs the new settings to
  /api/v1/project/:project/settings.

  `merge-paths` is a list of paths into the nested project data-structure.
  When a merge-path is non-nil the part of the project data-structure at
  that path is used as the base values for the settings. The new settings
  from the inputs state are merged on top.

  This allows all the settings on a page to be submitted, even if the user only
  modifies one.

  E.g.
  project is
    {:github-info { ... }
     :aws {:keypair {:access_key_id \"access key\"
                     :secret_access_key \"secret key\"}}}

  The user sets a new access key ID so inputs is
    {:aws {:keypair {:access_key_id \"new key id\"}}}

  :merge-paths is [[:aws :keypair]]

  The settings posted to the settings API will be:
    {:aws {:keypair {:access_key_id \"new key id\"
                     :secret_access_key \"secret key\"}}}"
  [project-id merge-paths current-state comms & {:keys [flash-context]}]
  (let [project-name (vcs-url/project-name project-id)
        inputs (get-in current-state state/inputs-path)
        project (get-in current-state state/project-path)
        settings (merge-settings merge-paths project inputs)
        org-name (vcs-url/org-name project-id)
        repo-name (vcs-url/repo-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        put-in-api-chan! (fn [api-result]
                           (put! (:api comms)
                                 [:project-settings
                                  (:status api-result)
                                  (assoc api-result
                                         :context
                                         {:project-name project-name
                                          ;; supplied to API
                                          ;; controller to indicate
                                          ;; which project setting was
                                          ;; updated, and what message
                                          ;; to show in flash
                                          ;; notification
                                          :flash flash-context})]))]
    (go
      (let [put-api-result (<! (ajax/managed-ajax :put (api-path/project-settings vcs-type org-name repo-name) :params settings))]
        (if (= :success (:status put-api-result))
          (let [get-api-result (<! (ajax/managed-ajax :get (api-path/project-settings vcs-type org-name repo-name)))]
            (put-in-api-chan! get-api-result)
            (put! (:controls comms) [:clear-inputs {:paths (map vector (keys settings))}]))
          (do
            (put-in-api-chan! put-api-result)
            (put! (:errors comms) [:api-error put-api-result])))
        put-api-result))))

(defmethod control-event :selected-piggieback-orgs-updated
  [_ _ {:keys [org selected?]} state]
  (update-in state
             state/selected-piggieback-orgs-path
             (if selected? conj disj)
             org))

(defmethod control-event :selected-transfer-org-updated
  [_ _ {:keys [org]} state]
  (assoc-in state state/selected-transfer-org-path org))

(defmethod control-event :set-project-api-token-scope
  [_ _ {:keys [scope]} state]
  (assoc-in state
            (conj state/project-data-path
                  :new-api-token
                  :scope)
            scope))

(defmethod post-control-event! :saved-project-settings
  [target message {:keys [project-id merge-paths on-success flash-context]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*]
    (go
      (let [api-result (<! (save-project-settings project-id
                                                  merge-paths
                                                  current-state
                                                  comms
                                                  :flash-context flash-context))]
        (release-button! uuid (:status api-result))
        (when (fn? on-success)
          (on-success))))))

(defmethod control-event :new-codedeploy-app-name-entered
  [target message _ state]
  (let [app-name (get-in state (conj state/inputs-path :project-settings-codedeploy-app-name))]
    (if (seq app-name)
      (-> state
          (update-in (conj state/project-path :aws :services :codedeploy) assoc app-name {})
          (assoc (conj state/inputs-path :project-settings-codedeploy-app-name) nil))
      state)))


(defmethod post-control-event! :saved-ssh-key
  [target message {:keys [project-id ssh-key on-success]} previous-state current-state comms]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (:api comms)]
    (button-ajax :post
                 (api-path/project-ssh-key vcs-type project-name)
                 :save-ssh-key
                 api-ch
                 :params ssh-key
                 :context {:project-id project-id
                           :on-success on-success})))


(defmethod post-control-event! :deleted-ssh-key
  [target message {:keys [project-id hostname fingerprint]} previous-state current-state comms]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (:api comms)]
    (ajax/ajax :delete
               (api-path/project-ssh-key vcs-type project-name)
               :delete-ssh-key
               api-ch
               :params {:fingerprint fingerprint
                        :hostname (str hostname)} ; coerce nil to ""
               :context {:project-id project-id
                         :hostname hostname
                         :fingerprint fingerprint})))


(defmethod post-control-event! :test-hook
  [target message {:keys [project-id merge-paths service]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)]
    (go
      (let [save-result (<! (save-project-settings project-id merge-paths current-state comms))
            test-result (if (= (:status save-result) :success)
                          (let [test-result
                                (<! (ajax/managed-ajax
                                      :post (api-path/project-hook-test vcs-type project-name service)
                                      :params {:project-id project-id}))]
                            (when (not= (:status test-result) :success)
                              (put! (:errors comms) [:api-error test-result]))
                            test-result)
                          save-result)]
        (release-button! uuid (:status test-result))))))


(defmethod post-control-event! :saved-project-api-token
  [target message {:keys [project-id api-token on-success]} previous-state current-state comms]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (:api comms)
        uuid frontend.async/*uuid*]
    (button-ajax :post
                 (api-path/project-tokens vcs-type project-name)
                 :save-project-api-token
                 api-ch
                 :params api-token
                 :context {:project-id project-id
                           :on-success on-success
                           :uuid uuid})))


(defmethod post-control-event! :deleted-project-api-token
  [target message {:keys [project-id token]} previous-state current-state comms]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (:api comms)]
    (ajax/ajax :delete
               (api-path/project-token vcs-type project-name token)
               :delete-project-api-token
               api-ch
               :context {:project-id project-id
                         :token token})))


(defmethod post-control-event! :set-heroku-deploy-user
  [target message {:keys [project-id login]} previous-state current-state comms]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (:api comms)]
    (button-ajax :post
                 (api-path/heroku-deploy-user vcs-type project-name)
                 :set-heroku-deploy-user
                 api-ch
                 :context {:project-id project-id
                           :login login})))


(defmethod post-control-event! :removed-heroku-deploy-user
  [target message {:keys [project-id]} previous-state current-state comms]
  (let [project-name (vcs-url/project-name project-id)
        vcs-type (vcs-url/vcs-type project-id)
        api-ch (:api comms)]
    (button-ajax :delete
                 (api-path/heroku-deploy-user vcs-type project-name)
                 :remove-heroku-deploy-user
                 api-ch
                 :context {:project-id project-id})))


(defmethod post-control-event! :set-user-session-setting
  [target message {:keys [setting value]} previous-state current-state comms]
  (set! (.. js/window -location -search) (str "?" (name setting) "=" value)))


(defmethod post-control-event! :load-first-green-build-github-users
  [target message {:keys [vcs_type project-name]} previous-state current-state comms]
  (if (or (nil? vcs_type) (= "github" vcs_type))
    (ajax/ajax :get
               (api-path/project-users vcs_type project-name)
               :first-green-build-github-users
               (:api comms)
               :context {:project-name project-name
                         :vcs-type vcs_type})))

(defmethod post-control-event! :invited-team-members
  [target message {:keys [vcs_type project-name org-name invitees]} previous-state current-state comms]
  (let [project-vcs-type (or vcs_type "github")
        org-vcs-type (or vcs_type "github")
        context (if project-name
                  ;; TODO: non-hackish way to indicate the type of invite
                  {:project project-name :first_green_build true}
                  {:org org-name})]
    (button-ajax :post
                 (if project-name
                   (api-path/project-users-invite project-vcs-type project-name)
                   (api-path/organization-invite org-vcs-type org-name))
                 :invite-team-members
                 (:api comms)
                 :context context
                 :params invitees
                 :events {:success #(analytics/track {:event-type :teammates-invited
                                                      :current-state current-state
                                                      :properties {:vcs-type vcs_type
                                                                   :invitees invitees
                                                                   :invitee-count (count invitees)}})})))

(defmethod post-control-event! :report-build-clicked
  [target message {:keys [build-url]} previous-state current-state comms]
  (support/raise-dialog (:errors comms)))

(defmethod post-control-event! :cancel-build-clicked
  [target message {:keys [vcs-url build-num build-id]} previous-state current-state comms]
  (let [api-ch (:api comms)
        vcs-type (vcs-url/vcs-type vcs-url)
        org-name (vcs-url/org-name vcs-url)
        repo-name (vcs-url/repo-name vcs-url)]
    (button-ajax :post
                 (api-path/build-cancel vcs-type org-name repo-name build-num)
                 :cancel-build
                 api-ch
                 :context {:build-id build-id})
    (analytics/track {:event-type :build-canceled
                      :current-state current-state
                      :properties {:vcs-type vcs-type
                                   :org-name org-name
                                   :repo-name repo-name}})))

(defmethod post-control-event! :enabled-project
  [target message {:keys [vcs-url project-name project-id]} previous-state current-state comms]
  (button-ajax :post
               (api-path/project-enable (vcs-url/vcs-type vcs-url) project-name)
               :enable-project
               (:api comms)
               :context {:project-name project-name
                         :project-id project-id})
  (analytics/track {:event-type :project-enabled
                    :current-state current-state}))

(defmethod control-event :toggle-stripe-error-banner
  [_ _ value state]
  (assoc-in state state/show-stripe-error-banner-path value))

(defmethod control-event :toggle-admin-error-banner
  [_ _ value state]
  (assoc-in state state/show-admin-error-banner-path value))

(defmethod post-control-event! :new-plan-clicked
  [target message {:keys [containers price description linux]} previous-state current-state comms]
  (utils/mlog "handling new-plan-clicked")
  (let [stripe-ch (chan)
        uuid frontend.async/*uuid*
        api-ch (:api comms)
        {org-name :name, vcs-type :vcs_type} (get-in current-state state/org-data-path)]
    (utils/mlog "calling stripe/open-checkout")
    (stripe/open-checkout {:price price :description description} stripe-ch)
    (go (let [[message data] (<! stripe-ch)]
          (case message
            :stripe-checkout-closed
            (do (analytics-track/stripe-checkout-closed {:current-state current-state
                                                         :action :new-linux-plan-clicked})
                (release-button! uuid :idle))

            :stripe-checkout-succeeded
            (let [card-info (:card data)]
              (analytics-track/stripe-checkout-succeeded {:current-state current-state
                                                          :action :new-linux-plan-clicked})
              (put! api-ch [:plan-card :success {:resp card-info
                                                 :context {:org-name org-name}}])
              (let [api-result (<! (ajax/managed-ajax
                                    :post
                                    (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                                    vcs-type
                                                    org-name
                                                    "plan")
                                    :params {:token data
                                             :containers containers
                                             :billing-name org-name
                                             :billing-email (get-in current-state (conj state/user-path :selected_email))
                                             :paid linux}))]
                (maybe-set-org-settings-error-banner! comms api-result)
                (put! api-ch [:create-plan
                              (:status api-result)
                              (assoc api-result :context {:org-name org-name
                                                          :vcs-type vcs-type})])
                (release-button! uuid (:status api-result))))

            nil)))))

(defmethod post-control-event! :new-osx-plan-clicked
  [target message {:keys [plan-type price description]} previous-state current-state comms]
  (let [stripe-ch (chan)
        uuid frontend.async/*uuid*
        api-ch (:api comms)
        {org-name :name, vcs-type :vcs_type} (get-in current-state state/org-data-path)]

    (utils/mlog "calling stripe/open-checkout")
    (stripe/open-checkout {:price price :description description} stripe-ch)
    (go (let [[message data] (<! stripe-ch)]
          (case message
            :stripe-checkout-closed
            (do (analytics-track/stripe-checkout-closed {:current-state current-state
                                                         :action :new-osx-plan-clicked})
                (release-button! uuid :idle))

            :stripe-checkout-succeeded
            (let [card-info (:card data)]
              (analytics-track/stripe-checkout-succeeded {:current-state current-state
                                                          :action :new-osx-plan-clicked})
              (put! api-ch [:plan-card :success {:resp card-info
                                                 :context {:org-name org-name}}])
              (let [api-result (<! (ajax/managed-ajax
                                     :post
                                     (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                                     vcs-type
                                                     org-name)
                                     :params {:token data
                                              :billing-name org-name
                                              :billing-email (get-in current-state (conj state/user-path :selected_email))
                                              :osx plan-type}))]
                (maybe-set-org-settings-error-banner! comms api-result)
                (put! api-ch [:create-plan
                              (:status api-result)
                              (assoc api-result :context {:org-name org-name
                                                          :vcs-type vcs-type})])
                (release-button! uuid (:status api-result))))

            nil)))))

(defmethod post-control-event! :new-checkout-key-clicked
  [target message {:keys [project-id project-name key-type]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)
        err-ch (:errors comms)
        vcs-type (vcs-url/vcs-type project-id)]
    (go (let [api-resp (<! (ajax/managed-ajax
                             :post
                             (api-path/project-checkout-keys vcs-type project-name)
                             :params {:type key-type}))]
          (if (= :success (:status api-resp))
            (let [api-resp (<! (ajax/managed-ajax :get (api-path/project-checkout-keys vcs-type project-name)))]
              (put! api-ch [:project-checkout-key (:status api-resp) (assoc api-resp :context {:project-name project-name})]))
            (put! err-ch [:api-error api-resp]))
          (release-button! uuid (:status api-resp))))))

(defmethod post-control-event! :delete-checkout-key-clicked
  [target message {:keys [project-id project-name fingerprint]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)
        err-ch (:errors comms)
        vcs-type (vcs-url/vcs-type project-id)]
    (go (let [api-resp (<! (ajax/managed-ajax
                             :delete
                             (api-path/project-checkout-key vcs-type project-name fingerprint)))]
          (if (= :success (:status api-resp))
            (let [api-resp (<! (ajax/managed-ajax :get (api-path/project-checkout-keys vcs-type project-name)))]
              (put! api-ch [:project-checkout-key (:status api-resp) (assoc api-resp :context {:project-name project-name})]))
            (put! err-ch [:api-error api-resp]))
          (release-button! uuid (:status api-resp))))))

(defmethod post-control-event! :update-containers-clicked
  [target message {:keys [containers]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)
        {org-name :name, vcs-type :vcs_type} (get-in current-state state/org-data-path)
        login (get-in current-state state/user-login-path)]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :put
                           (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                           vcs-type
                                           org-name)
                           :params {:containers containers}))]
       (put! api-ch [:update-plan
                     (:status api-result)
                     (assoc api-result :context {:org-name org-name
                                                 :vcs-type vcs-type})])
       (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :update-osx-plan-clicked
  [target message {:keys [plan-type]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)
        {org-name :name, vcs-type :vcs_type} (get-in current-state state/org-data-path)]
    (go
      (let [api-result (<! (ajax/managed-ajax
                             :put
                             (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                             vcs-type
                                             org-name)
                             :params {:osx plan-type}))]
        (put! api-ch [:update-plan
                      (:status api-result)
                      (assoc api-result :context {:org-name org-name
                                                  :vcs-type vcs-type})])
        (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :activate-plan-trial
  [target message {:keys [plan-type template org]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        {org-name :name vcs-type :vcs_type} org
        api-ch (:api comms)
        nav-ch (:nav comms)]
    (analytics/track {:event-type :start-trial-clicked
                      :current-state current-state
                      :properties {:org org-name
                                   :vcs-type vcs-type
                                   :plan-type (analytics-utils/canonical-plan-type plan-type)
                                   :template template}})
    (go
      (let [api-result (<! (ajax/managed-ajax
                             :post
                             (gstring/format "/api/v1.1/organization/%s/%s/plan/trial"
                                             vcs-type
                                             org-name)
                             :params {plan-type {:template template}}))]
        (put! api-ch [:update-plan
                      (:status api-result)
                      (assoc api-result :context {:org-name org-name
                                                  :vcs-type vcs-type})])
        (if (and (= :build (get-in current-state state/current-view-path))
                 (= :success (:status api-result)))
         (put! nav-ch [:navigate! {:path (routes/v1-project-settings-path {:vcs_type "github"
                                                                           :org org-name
                                                                           :repo (get-in current-state state/project-repo-path)
                                                                           :_fragment "parallel-builds"})}]))
        (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :save-piggieback-orgs-clicked
  [target message {:keys [selected-piggieback-orgs org-name vcs-type]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)
        piggieback-org-maps (map #(set/rename-keys % {:vcs_type :vcs-type})
                                 selected-piggieback-orgs)]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :put
                           (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                           vcs-type
                                           org-name)
                           :params {:piggieback-org-maps piggieback-org-maps}))]
       (put! api-ch [:update-plan
                     (:status api-result)
                     (assoc api-result :context {:org-name org-name
                                                 :vcs-type vcs-type})])
       (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :transfer-plan-clicked
  [target message {{:keys [org-name vcs-type]} :from-org :keys [to-org]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)
        errors-ch (:errors comms)
        nav-ch (:nav comms)]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :put
                           (gstring/format "/api/v1.1/organization/%s/%s/transfer-plan"
                                           vcs-type
                                           org-name)
                           :params to-org))]
       (if-not (= :success (:status api-result))
         (put! errors-ch [:api-error api-result])
         (let [plan-api-result (<! (ajax/managed-ajax :get (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                                                           vcs-type
                                                                           org-name)))]
           (put! api-ch [:org-plan
                         (:status plan-api-result)
                         (assoc plan-api-result
                                :context
                                {:org-name org-name
                                 :vcs-type vcs-type})])
           (put! nav-ch [:navigate! {:path (routes/v1-org-settings-path {:org org-name
                                                                         :vcs_type vcs-type})}])))
       (release-button! uuid (:status api-result))))))

(defn- maybe-add-message-for-beta
  "Adds a message if a user changes their beta status.  Must be run
  before actually updating state."
  [state args]

  (if-not (contains? args state/user-in-beta-key)
    state
    (let [before (boolean (get-in state state/user-in-beta-path))
          after (boolean (args state/user-in-beta-key))]
      (case [before after]
        [true false] (assoc-in state state/general-message-path "You have left the beta program! Come back any time!")
        [false true] (assoc-in state state/general-message-path "You have joined the beta program! Thanks!")
        state))))

(defmethod control-event :preferences-updated
  [target message args state]
  (-> state
      (maybe-add-message-for-beta args)
      (update-in state/user-path merge args)))

(defmethod post-control-event! :preferences-updated
  [target message args previous-state current-state comms]
  (let [beta-params {state/user-in-beta-key         (get-in current-state state/user-in-beta-path)
                     state/user-betas-key           (get-in current-state state/user-betas-path)}
        email-params {state/user-email-prefs-key    (get-in current-state state/user-email-prefs-path)
                      state/user-selected-email-key (get-in current-state state/user-selected-email-path)}
        api-ch (:api comms)]
    (ajax/ajax
     :put
     "/api/v1/user/save-preferences"
     :update-preferences
     api-ch
     :params (merge beta-params email-params))
    (launchdarkly/merge-custom-properties! beta-params)))

(defmethod control-event :project-preferences-updated
  [target message args state]
  (update-in state (conj state/user-path :projects)
             (partial merge-with merge)
             ;; The keys of the projects map are unfortunately keywords, despite being URLs.
             (into {} (for [[vcs-url prefs] args]
                        [(keyword vcs-url) prefs]))))

(defmethod post-control-event! :project-preferences-updated
  [target message args previous-state current-state comms]
  (ajax/ajax
   :put
   "/api/v1/user/save-preferences"
   :update-preferences
   (:api comms)
   :params {:projects args}))

(defmethod control-event :org-preferences-updated
  [target message {:keys [org prefs]} state]
  ;; org is expected to be a vector [vcs_type username] where both are
  ;; keywords
  (update-in state
             (into state/user-org-prefs-path org)
             merge
             prefs))

(defmethod post-control-event! :org-preferences-updated
  [target message {:keys [org prefs]} previous-state current-state comms]
  (ajax/ajax
   :put
   "/api/v1/user/save-preferences"
   :update-preferences
   (:api comms)
   :params {:organization_prefs (assoc-in {} org prefs)}))

(defmethod control-event :org-settings-normalized
  [target message {:keys [org-name vcs-type]} state]
  (update-in state [:organization/by-vcs-type-and-name [vcs-type org-name]] dissoc :users))

(defmethod control-event :clear-build-data
  [target message args state]
  (dissoc state :current-build-data))

(defmethod post-control-event! :org-settings-normalized
  [target message {:keys [org-name vcs-type]} previous-state current-state comms]
  (let [previous-users (:users (get-in previous-state [:organization/by-vcs-type-and-name [vcs-type org-name]]))]
    (api/get-org-settings-normalized org-name vcs-type (:api comms) {:refresh true} previous-users)))

(defmethod post-control-event! :heroku-key-add-attempted
  [target message args previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :post
                           "/api/v1/user/heroku-key"
                           :params {:apikey (:heroku_api_key args)}))]
       (if (= :success (:status api-result))
         (let [me-result (<! (ajax/managed-ajax :get "/api/v1/me"))]
           (put! api-ch [:update-heroku-key :success api-result])
           (put! api-ch [:me (:status me-result) (assoc me-result :context {})]))
         (put! (:errors comms) [:api-error api-result]))
       (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :api-token-revocation-attempted
  [target message {:keys [token]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :delete
                           (gstring/format "/api/v1/user/token/%s" (:token token))
                           :params {}))]
       (put! api-ch [:delete-api-token (:status api-result) (assoc api-result :context {:token token})])
       (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :api-token-creation-attempted
  [target message {:keys [label on-success]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :post
                           "/api/v1/user/token"
                           :params {:label label}))]
       (put! api-ch [:create-api-token (:status api-result) (assoc api-result :context {:label label
                                                                                        :on-success on-success})])
       (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :update-card-clicked
  [target message {:keys [containers price description base-template-id]} previous-state current-state comms]
  (let [stripe-ch (chan)
        uuid frontend.async/*uuid*
        api-ch (:api comms)
        {org-name :name, vcs-type :vcs_type} (get-in current-state state/org-data-path)]
    (stripe/open-checkout {:panelLabel "Update card"} stripe-ch)
    (go (let [[message data] (<! stripe-ch)]
          (case message
            :stripe-checkout-closed
            (do (analytics-track/stripe-checkout-closed {:current-state current-state
                                                         :action :update-card-clicked})
                (release-button! uuid :idle))

            :stripe-checkout-succeeded
            (let [token-id (:id data)]
              (analytics-track/stripe-checkout-succeeded {:current-state current-state
                                                          :action :update-card-clicked})
              (let [api-result (<! (ajax/managed-ajax
                                    :put
                                    (gstring/format "/api/v1.1/organization/%s/%s/card"
                                                    vcs-type
                                                    org-name)
                                    :params {:token token-id}))]
                (maybe-set-org-settings-error-banner! comms api-result)
                (put! api-ch [:plan-card (:status api-result) (assoc api-result :context {:vcs-type vcs-type
                                                                                          :org-name org-name})])
                (release-button! uuid (:status api-result))))

            nil)))))

(defmethod post-control-event! :save-invoice-data-clicked
  [target message _ previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)
        {org-name :name, vcs-type :vcs_type} (get-in current-state state/org-data-path)
        settings (state-utils/merge-inputs (get-in current-state state/org-plan-path)
                                           (get-in current-state state/inputs-path)
                                           [:billing_email :billing_name :extra_billing_data])]
    (go
      (let [api-result (<! (ajax/managed-ajax
                              :put
                              (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                              vcs-type
                                              org-name)
                              :params {:billing-email (:billing_email settings)
                                       :billing-name (:billing_name settings)
                                       :extra-billing-data (:extra_billing_data settings)}))]
        (when (= :success (:status api-result))
          (put! (:controls comms) [:clear-inputs (map vector (keys settings))]))
        (put! api-ch [:update-plan
                      (:status api-result)
                      (assoc api-result :context {:org-name org-name
                                                  :vcs-type vcs-type})])
        (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :resend-invoice-clicked
  [target message {:keys [invoice-id]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)
        {org-name :name, vcs-type :vcs_type} (get-in current-state state/org-data-path)]
    (go
      (let [api-result (<! (ajax/managed-ajax
                              :post
                              (gstring/format "/api/v1.1/organization/%s/%s/invoice/resend"
                                              vcs-type
                                              org-name)
                              :params {:id invoice-id}))]
        ;; TODO Handle this message in the API channel
        (put! api-ch [:resend-invoice
                      (:status api-result)
                      (assoc api-result :context {:org-name org-name
                                                  :vcs-type vcs-type})])
        (release-button! uuid (:status api-result))))))

(defmethod post-control-event! :cancel-plan-clicked
  [target message {:keys [org-name vcs_type cancel-reasons cancel-notes plan-template plan-id plan-type plan-type-key on-success]} previous-state current-state comms]
  (analytics-track/cancel-plan-clicked {:current-state current-state
                                        :vcs_type vcs_type
                                        :plan-template plan-template
                                        :plan-id plan-id
                                        :plan-type plan-type
                                        :cancel-reasons cancel-reasons
                                        :cancel-notes cancel-notes})
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)
        nav-ch (:nav comms)
        errors-ch (:errors comms)]
    (go
     (let [api-result (<! (ajax/managed-ajax
                           :delete
                           (gstring/format "/api/v1.1/organization/%s/%s/plan"
                                           vcs_type
                                           org-name)
                           :params {:cancel-reasons cancel-reasons :cancel-notes cancel-notes :plan-type plan-type-key}))]
       (if-not (= :success (:status api-result))
         (put! errors-ch [:api-error api-result])
         (do
           (put! api-ch [:org-plan
                         (:status api-result)
                         (assoc api-result :context {:org-name org-name
                                                     :vcs-type vcs_type})])
           (put! nav-ch [:navigate! {:path (routes/v1-org-settings {:org org-name
                                                                    :vcs_type vcs_type})
                                     :replace-token? true}])))
       (release-button! uuid (:status api-result))
       (when (fn? on-success)
         (on-success))))))

(defn track-and-redirect [event properties owner path]
  (let [redirect #(set! js/window.location.href path)
        track-ch (analytics/track {:event-type :external-click
                                   :event event
                                   :owner owner
                                   :properties properties})]
    (if track-ch
      (go (alt!
            track-ch ([v] (do (utils/mlog "tracked" v "... redirecting")
                              (redirect)))
            (async/timeout 1000) (do (utils/mlog "timing out waiting for analytics. redirecting.")
                                     (redirect))))
      (redirect))))

(defmethod post-control-event! :track-external-link-clicked
  [_ _ {:keys [event properties owner path]} _ _]
  (track-and-redirect event properties owner path))

(defmethod control-event :project-feature-flag-checked
  [target message {:keys [project-id flag value]} state]
  (assoc-in state (conj state/feature-flags-path flag) value))

(defmethod post-control-event! :project-feature-flag-checked
  [target message {:keys [project-id flag value]} previous-state current-state comms]
  (analytics-track/project-image-change {:previous-state previous-state
                                         :current-state current-state
                                         :flag flag
                                         :value value})
  (let [project-name (vcs-url/project-name project-id)
        api-ch (:api comms)
        error-ch (:errors comms)
        org-name (vcs-url/org-name project-id)
        repo-name (vcs-url/repo-name project-id)
        vcs-type (vcs-url/vcs-type project-id)]
    (go (let [api-result (<! (ajax/managed-ajax :put (api-path/project-settings vcs-type org-name repo-name)
                                                :params {:feature_flags {flag value}}))]
          (when (not= :success (:status api-result))
            (put! error-ch [:api-error api-result]))
          (ajax/ajax :get (api-path/project-settings vcs-type org-name repo-name) :project-settings api-ch :context {:project-name project-name})))))

(defmethod post-control-event! :project-experiments-feedback-clicked
  [target message _ previous-state current-state comms]
  (support/raise-dialog (:errors comms)))

(defmethod control-event :refresh-admin-build-state-clicked
  [target message _ state]
  (assoc-in state state/build-state-path nil))

(defmethod post-control-event! :refresh-admin-build-state-clicked
  [target message _ previous-state current-state comms]
  (api/get-build-state (:api comms)))

(defmethod control-event :refresh-admin-fleet-state-clicked
  [target message _ state]
  (assoc-in state state/fleet-state-path nil))

(defmethod post-control-event! :refresh-admin-fleet-state-clicked
  [target message _ previous-state current-state comms]
  (api/get-fleet-state (:api comms)))

(defmethod control-event :clear-error-message-clicked
  [target message _ state]
  (assoc-in state state/error-message-path nil))

(defmethod control-event :refresh-admin-build-list
  [_ _ _ state]
  (assoc-in state state/recent-builds-path nil))

(defmethod post-control-event! :refresh-admin-build-list
  [target _ {:keys [tab]} _ current-state comms]
  (api/get-admin-dashboard-builds tab (:api comms)))

(defmethod control-event :show-all-commits-toggled
  [target message _ state]
  (update-in state (conj state/build-data-path :show-all-commits?) not))

(defmethod control-event :show-test-message-toggled
  [_ _ {:keys [test-index]} state]
  (let [test-path (concat state/tests-path [test-index :show-message])]
    (update-in state test-path not)))

(defmethod control-event :pricing-parallelism-clicked
  [target message {:keys [p]} state]
  (assoc-in state state/pricing-parallelism-path p))

(defmethod control-event :play-video
  [_ _ video-id state]
  (assoc-in state state/modal-video-id-path video-id))

(defmethod control-event :close-video
  [_ _ _ state]
  (assoc-in state state/modal-video-id-path nil))

(defmethod control-event :change-hamburger-state
  [_ _ _ state]
  (let [hamburger-state (get-in state state/hamburger-menu-path)]
    (if (= "closed" hamburger-state)
      (assoc-in state state/hamburger-menu-path "open")
      (assoc-in state state/hamburger-menu-path "closed"))))

(defmethod post-control-event! :suspend-user
  [_ _ {:keys [login]} _ _ {api-ch :api}]
  (api/set-user-suspension login true api-ch))

(defmethod post-control-event! :unsuspend-user
  [_ _ {:keys [login]} _ _ {api-ch :api}]
  (api/set-user-suspension login false api-ch))

(defmethod post-control-event! :set-admin-scope
  [_ _ {:keys [login scope]} _ _ {api-ch :api}]
  (api/set-user-admin-scope login scope api-ch))

(defmethod control-event :system-setting-changed
  [_ _ {:keys [name]} state]
  (update-in state state/system-settings-path
             (fn [settings]
               (mapv #(if (= name (:name %))
                        (assoc % :updating true)
                        %)
                     settings))))

(defmethod post-control-event! :system-setting-changed
  [_ _ {:keys [name value]} _ _ {api-ch :api}]
  (api/set-system-setting name value api-ch))

(defmethod control-event :insights-sorting-changed
  [_ _ {:keys [new-sorting]} state]
  (assoc-in state state/insights-sorting-path new-sorting))

(defmethod control-event :insights-filter-changed
  [_ _ {:keys [new-filter]} state]
  (assoc-in state state/insights-filter-path new-filter))

(defmethod control-event :dismiss-statuspage
  [_ _ {:keys [status]} state]
  (assoc-in state state/statuspage-dismissed-update-path status))

(defmethod post-control-event! :upload-p12
  [_ _ {:keys [project-name vcs-type file-content file-name password description on-success]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)]
    (api/set-project-code-signing-keys project-name vcs-type file-content file-name password description api-ch uuid on-success)))

(defmethod post-control-event! :delete-p12
  [_ _ {:keys [project-name vcs-type id on-success]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)]
    (api/delete-project-code-signing-key project-name vcs-type id api-ch on-success uuid)))

(defmethod post-control-event! :upload-provisioning-profile
  [_ _ {:keys [project-name vcs-type file-content file-name description on-success]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)]
    (api/set-project-provisioning-profiles project-name vcs-type file-content file-name description api-ch uuid on-success)))

(defmethod post-control-event! :delete-provisioning-profile
  [_ _ {:keys [project-name vcs-type uuid on-success]} previous-state current-state comms]
  (let [button-uuid frontend.async/*uuid*
        api-ch (:api comms)]
    (api/delete-project-provisioning-profile project-name vcs-type uuid api-ch on-success button-uuid)))

(defmethod post-control-event! :create-jira-issue
  [_ _ {:keys [project-name vcs-type jira-issue-data on-success]} previous-state current-state comms]
  (let [uuid frontend.async/*uuid*
        api-ch (:api comms)]
    (api/create-jira-issue project-name vcs-type jira-issue-data api-ch uuid on-success))
  (analytics/track {:event-type :create-jira-issue-clicked
                    :current-state current-state
                    :properties {:issue-type (:type jira-issue-data)}}))

(defmethod post-control-event! :load-jira-projects
  [_ _ {:keys [project-name vcs-type]} previous-state current-state comms]
  (let [api-ch (:api comms)]
    (api/get-jira-projects project-name vcs-type api-ch)))

(defmethod post-control-event! :load-jira-issue-types
  [_ _ {:keys [project-name vcs-type]} previous-state current-state comms]
  (let [api-ch (:api comms)]
    (api/get-jira-issue-types project-name vcs-type api-ch)))

(defmethod post-control-event! :project-insights-branch-changed
  [target message {:keys [new-branch]} _ current-state comms]
  (let [nav-data (get-in current-state [:navigation-data])]
    (put! (:nav comms) [:navigate! {:path (routes/v1-project-insights-path (assoc nav-data :branch new-branch))}])
    (analytics/track {:event-type :project-branch-changed
                      :current-state current-state
                      :properties {:new-branch new-branch}})))

(defmethod control-event :dismiss-osx-usage-banner
  [_ _ {:keys [current-usage]} current-state comms]
  (cond
    (>= current-usage plan/third-warning-threshold)
    (assoc-in current-state state/dismissed-osx-usage-level (+ current-usage plan/future-warning-threshold-increment))

    (>= current-usage plan/second-warning-threshold)
    (assoc-in current-state state/dismissed-osx-usage-level plan/third-warning-threshold)

    (>= current-usage plan/first-warning-threshold)
    (assoc-in current-state state/dismissed-osx-usage-level plan/second-warning-threshold)

    :else
    current-state))

(defmethod control-event :dismiss-trial-offer-banner
  [_ _ _ state]
  (assoc-in state state/dismissed-trial-offer-banner true))

(defmethod post-control-event! :dismiss-trial-offer-banner
  [_ _ {:keys [org plan-type template]} _ current-state comms]
  (let [{org-name :name vcs-type :vcs_type} org]
    (analytics/track {:event-type :dismiss-trial-offer-banner-clicked
                      :current-state current-state
                      :properties {:org org-name
                                   :vcs-type vcs-type
                                   :plan-type (analytics-utils/canonical-plan-type plan-type)
                                   :template template}})))

(defmethod control-event :dismiss-trial-update-banner
  [_ _ _ state]
  (assoc-in state state/dismissed-trial-update-banner true))

(defmethod control-event :dismiss-web-notifications-permissions-banner
  [_ _ _ state]
  (-> state
      (assoc-in state/remove-web-notification-banner-path true)
      (assoc-in state/remove-web-notification-confirmation-banner-path true)))

(defmethod post-control-event! :dismiss-web-notifications-permissions-banner
  [_ _ {:keys [response]} _ current-state comms]
  (analytics/track {:event-type :web-notifications-permissions-banner-dismissed
                    :current-state current-state
                    :properties {:response response}}))

(defmethod control-event :set-web-notifications-permissions
  [_ _ {:keys [enabled?]} state]
  (-> state
      (assoc-in state/remove-web-notification-banner-path true)
      (assoc-in state/web-notifications-enabled-path enabled?)))

(defmethod post-control-event! :set-web-notifications-permissions
  [_ _ {:keys [enabled? response]} _ current-state comms]
  (analytics/track {:event-type :web-notifications-permissions-set
                    :current-state current-state
                    :properties {:notifications-enabled enabled?
                                 :response response}}))

(defmethod control-event :dismiss-web-notifications-confirmation-banner
  [_ _ _ state]
  (assoc-in state state/remove-web-notification-confirmation-banner-path true))

(defmethod control-event :web-notifications-confirmation-account-settings-clicked
  [_ _ _ state]
  (assoc-in state state/remove-web-notification-confirmation-banner-path true))

(defmethod post-control-event! :web-notifications-confirmation-account-settings-clicked
  [_ _ {:keys [response]} _ current-state comms]
  (analytics/track {:event-type :account-settings-clicked
                    :current-state current-state
                    :properties {:response response
                                 :component "web-notifications-confirmation-banner"}})
  (let [nav-ch (:nav comms)]
    (put! nav-ch [:navigate! {:path (routes/v1-account-subpage {:subpage "notifications"})}])))

(defmethod post-control-event! :followed-projects
  [_ _ {:keys [on-success]} previous-state current-state comms]
  (let [api-ch (:api comms)
        vcs-urls (->> (concat (get-in current-state state/repos-building-path))
                      vals
                      (filter :checked)
                      (map :vcs_url))
        uuid frontend.async/*uuid*]
    (button-ajax :post
                 (api/follow-projects vcs-urls api-ch uuid on-success)
                 :follow-projects
                 api-ch)))

(defmethod control-event :check-all-activity-repos
  [_ _ {:keys [org-name checked]} state]
  (update-in state state/repos-building-path (fn [current-vals]
                                               (map-utils/map-vals (fn [project]
                                                                     (if (= org-name (project-model/org-name project))
                                                                       (assoc project :checked checked)
                                                                       project))
                                                                   current-vals))))

(defmethod control-event :nux-bootstrap
  [target message args state]
  (let [state (assoc-in state state/repos-path nil)]
    (reduce (fn [state vcs]
              (-> state
                  (assoc-in (state/all-repos-loaded-path vcs) false)
                  (assoc-in state/setup-project-projects-path {})
                  (assoc-in state/repos-building-path {})))
            state
            [:github :bitbucket])))

(defmethod post-control-event! :nux-bootstrap
  [target message args previous-state current-state comms]
  (api/get-orgs (:api comms) :include-user? true)
  (api/get-all-repos (:api comms)))

(defmethod control-event :setup-project-select-project
  [target message repo-id state]
  (assoc-in state
            state/setup-project-selected-project-path
            (get-in state (conj state/setup-project-projects-path repo-id))))
