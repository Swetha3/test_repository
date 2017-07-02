(ns frontend.controllers.ws
  "Websocket controllers"
  (:require [clojure.set]
            [frontend.api :as api]
            [frontend.favicon]
            [frontend.models.action :as action-model]
            [frontend.models.build :as build-model]
            [frontend.pusher :as pusher]
            [frontend.utils.seq :refer [find-index]]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true :refer [mlog]])
  (:require-macros [frontend.controllers.ws :refer [with-swallow-ignored-build-channels]]))

;; To subscribe to a channel, put a subscribe message in the websocket channel
;; with the channel name and the messages you want to listen to. That will be
;; handled in the post-ws controller.
;; Example: (put! ws-ch [:subscribe {:channel-name "my-channel" :messages [:my-message]}])
;;
;; Unsubscribe by putting an unsubscribe message in the channel with the channel name
;; Example: (put! ws-ch [:unsubscribe "my-channel"])
;; the api-post-controller can do any other actions

(defn fresh-channels
  "Returns all of the channels that a user should not be unsubscribed from."
  [state]
  (let [user (get-in state state/user-path)
        build (get-in state state/build-path)]
    (cond-> #{}
      user (conj (pusher/user-channel user))
      build (into (pusher/build-channels build (state/current-container-id state))))))

(defn ignore-build-channel?
  "Returns true if we should ignore pusher updates for the given channel-name. This will be
  true if the channel is stale or if the build hasn't finished loading."
  [state channel-name]
  (if-let [build (get-in state state/build-path)]
    (not-any? #{channel-name} (pusher/build-channels build (state/current-container-id state)))
    true))

(defn usage-queue-build-index-from-channel-name [state channel-name]
  "If the channel-name matches a build we're monitoring in the usage
  queue, find it in the application state and return its index in the
  usage queue build collection."
  (when-let [builds (seq (get-in state state/usage-queue-path))]
    (find-index #(some #{channel-name} (pusher/build-channels %)) builds)))

;; --- Navigation Multimethod Declarations ---

(defmulti ws-event
  (fn [pusher-imp message args state] message))

(defmulti post-ws-event!
  (fn [pusher-imp message args previous-state current-state comms] message))

;; --- Navigation Multimethod Implementations ---

(defmethod ws-event :default
  [pusher-imp message args state]
  (utils/mlog "No ws-event for: " (pr-str message))
  state)

(defmethod post-ws-event! :default
  [pusher-imp message args previous-state current-state comms]
  (utils/mlog "No post-ws for: " message))

;; TODO once the backend change associated with the migration to empty
;; :build/update payload has rolled out across .com production and all
;; enterprise customers, this method can be removed, along with the
;; conditional handling in the post-ws-event! handler below.
(defmethod ws-event :build/update
  [pusher-imp message {:keys [data channel-name]} state]
  (let [data (utils/js->clj-kw data)]
    (if (empty? data)
      state
      (if-not (ignore-build-channel? state channel-name)
        (update-in state state/build-path merge data)
        (if-let [index (usage-queue-build-index-from-channel-name state channel-name)]
          (update-in state (state/usage-queue-build-path index) merge data)
          state)))))

(defmethod post-ws-event! :build/update
  [pusher-imp message {:keys [data channel-name]} previous-state current-state comms]
  (let [data (utils/js->clj-kw data)]
    (if (empty? data)
      ;; the new path
      (api/get-build-observables (pusher/build-parts-from-channel channel-name)
                                 (:api comms))
      ;; the old path
      (when-not (ignore-build-channel? current-state channel-name)
        (frontend.favicon/set-color! (build-model/favicon-color data))
        (let [build (get-in current-state state/build-path)]
          (when (and (build-model/finished? build)
                     (empty? (get-in current-state state/tests-path)))
            (api/get-build-tests build (:api comms))))))))


(defmethod ws-event :build/new-action
  [pusher-imp message {:keys [data channel-name]} state]
  (with-swallow-ignored-build-channels state channel-name
    (reduce (fn [state {action-index :step container-index :index action-log :log}]
              (-> state
                  (build-model/fill-containers container-index action-index)
                  (assoc-in (state/action-path container-index action-index) action-log)
                  (update-in (state/action-path container-index action-index) action-model/format-latest-output)))
            state (utils/js->clj-kw data))))


(defmethod ws-event :build/update-action
  [pusher-imp message {:keys [data channel-name]} state]
  (with-swallow-ignored-build-channels state channel-name
    (reduce (fn [state {action-index :step container-index :index action-log :log}]
              (-> state
                  (build-model/fill-containers container-index action-index)
                  (update-in (state/action-path container-index action-index) merge action-log)))
            state (utils/js->clj-kw data))))


(defmethod ws-event :build/append-action
  [pusher-imp message {:keys [data channel-name]} state]
  (with-swallow-ignored-build-channels state channel-name
    (reduce (fn [state data]
              (let [container-index (aget data "index")
                    action-index (aget data "step")]
                (let [output (utils/js->clj-kw (aget data "out"))]
                  (-> state
                      (build-model/fill-containers container-index action-index)
                      (update-in (state/action-output-path container-index action-index) vec)
                      (update-in (state/action-output-path container-index action-index) conj output)
                      (update-in (state/action-path container-index action-index) action-model/format-latest-output)))))
            state data)))


(defmethod ws-event :build/add-messages
  [pusher-imp message {:keys [data channel-name]} state]
  (let [build (get-in state state/build-path)
        new-messages (set (utils/js->clj-kw data))]
    (with-swallow-ignored-build-channels state channel-name
      (update-in state (conj state/build-path :messages)
                 (fn [messages] (-> messages
                                    set ;; careful not to add the same message twice
                                    (clojure.set/union new-messages)))))))

(defmethod post-ws-event! :build/test-results
  [pusher-imp message {:keys [data channel-name]} previous-state current-state comms]
  ;; this event forces a fetch on all of the test results, which is a little weird.
  ;; but test results are large and we can't fit them all in pusher messages,
  ;; and we get them all at once anyway.
  (let [build (get-in current-state state/build-path)]
    (with-swallow-ignored-build-channels current-state channel-name
      (api/get-build-tests build (:api comms)))))

(defmethod post-ws-event! :subscribe
  [pusher-imp message {:keys [channel-name messages context]} previous-state current-state comms]
  (let [ws-ch (:ws comms)]
    (utils/mlog "subscribing to " channel-name)
    (pusher/subscribe pusher-imp channel-name ws-ch :messages messages :context context)))


(defmethod post-ws-event! :unsubscribe
  [pusher-imp message channel-name previous-state current-state comms]
  (utils/mlog "unsubscribing from " channel-name)
  (pusher/unsubscribe pusher-imp channel-name))

(defmethod post-ws-event! :unsubscribe-stale-channels
  [pusher-imp message _ previous-state current-state comms]
  (doseq [channel-name (clojure.set/difference (pusher/subscribed-channels pusher-imp)
                                               (fresh-channels current-state))]
    (utils/mlog "unsubscribing from " channel-name)
    (pusher/unsubscribe pusher-imp channel-name)))

(defmethod post-ws-event! :subscription-error
  [pusher-imp message {:keys [channel-name status]} _ _]
  (utils/mlog "subscription-error " channel-name status))

(defmethod post-ws-event! :refresh
  [pusher-imp message _ previous-state current-state comms refresh-fn]
  (let [navigation-point (:navigation-point current-state)
        api-ch (:api comms)]
    (utils/mlog "ws refresh navigation-point" navigation-point)
    (utils/mlog "contains?" state/workflows-routes (contains? state/workflows-routes navigation-point))
    (api/get-projects api-ch)
    (condp contains? navigation-point
      #{:build} (when (get-in current-state state/show-usage-queue-path)
                  (api/get-usage-queue (get-in current-state state/build-path) api-ch))
      #{:dashboard} (api/get-dashboard-builds (assoc (:navigation-data current-state)
                                                     :builds-per-page (:builds-per-page current-state)
                                                     :all? (get-in current-state state/show-all-builds-path))
                                              api-ch)
      state/workflows-routes (refresh-fn)
      nil)))
