(ns frontend.pusher
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [clojure.string :as string]
            [goog.dom.DomHelper]
            [goog.events]
            [goog.Uri]
            [om.core :as om :include-macros true]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.state :as state-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [secretary.core :as sec])

  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(defn endpoint-info [ws-endpoint]
  (let [uri (goog.Uri. ws-endpoint)]
    {:wsHost (.getDomain uri)
     ;; Pusher library is a bit quirky and we can submit both wsPort and wssPort :/
     ;; encrypted is what determines which to use
     :wsPort (.getPort uri)
     :wssPort (.getPort uri)
     :encrypted (= "wss" (.getScheme uri))
     :enabledTransports ["ws"]

     ;; If using a custom endpoint - don't send connection stats to pusher
     :disableStats true}))

(defn pusher-object-config [config]
  (as-> config c
    (dissoc c :key :ws_endpoint)
    (merge {:encrypted true
            :auth {:params {:CSRFToken (utils/csrf-token)}}
            :authEndpoint "/auth/pusher"}
           (when-let [endpoint (:ws_endpoint config)]
             (endpoint-info endpoint))
           c)))

(defn new-pusher-instance [config]
  (aset (aget js/window "Pusher") "channel_auth_endpoint" "/auth/pusher")
  (let [pusher-config (pusher-object-config config)]
    (js/Pusher. (:key config) (clj->js pusher-config))))

(defn user-channel [user]
  (str "private-" (:pusher_id user)))

(defn build-parts-from-channel [channel-name]
  (let [[_ username project build-num vcs-type]
        (re-find #"^(?:private-)?([^@]+?)@([^@]+?)@(\d+?)(?:@vcs-(github|bitbucket))?(?:@(?:\d+?|all))?$" channel-name)]
    (when (and username project build-num)
      {:username username
       :project project
       :build-num (js/parseInt build-num)
       :vcs-type (or vcs-type "github")})))

(defn build-channel-base
  [{:keys [username project build-num vcs-type]
    :or {vcs-type "github"}}]
  (let [project-prefix (str "private-" username "@" project)
        vcs-str (str "vcs-" (name vcs-type))]
    (str project-prefix "@" build-num "@" vcs-str)))

(def obsolete-build-channel build-channel-base)

(defn build-container-channel
  [{:keys [container-index] :as parts}]
  (str (build-channel-base parts) "@" (or container-index 0)))

(defn build-all-channel
  [parts]
  (str (build-channel-base parts) "@all"))

(defn build-channels-from-parts
  [parts]
  (let [obsolete-channel (obsolete-build-channel parts)
        container-channel (build-container-channel parts)
        all-channel (build-all-channel parts)]
    [obsolete-channel container-channel all-channel]))

(defn build-channels
  ([build container-index]
   (build-channels-from-parts (state-utils/build-parts build container-index)))
  ([build]
   (build-channels-from-parts (state-utils/build-parts build))))

(def container-messages [:build/new-action
                         :build/update-action
                         :build/append-action])

(def build-messages [:build/update
                     :build/add-messages
                     :build/test-results])

;; TODO: use the same event names on the backend as we do on the frontend
(def event-translations
  {:build/new-action "newAction"
   :build/update-action "updateAction"
   :build/append-action "appendAction"
   :build/update "updateObservables"
   :build/add-messages "maybeAddMessages"
   :build/test-results "fetchTestResults"
   ;; this is kind of special, it can call any function on the old window.VM
   ;; luckily, it only calls refreshBuildState
   :refresh "call"})

(defn subscribe
  "Subscribes to channel and binds to events. Takes a pusher-instance,
  a channel-name, a list of messages to subscribe to and a websocket
  channel. Will put data from the pusher events onto the websocket
  channel with the message. Returns the channel."
  [pusher-instance channel-name ws-ch & {:keys [messages context]}]
  (let [channel (.subscribe pusher-instance channel-name)]
    (doseq [message messages
            :let [pusher-event (get event-translations message)]]
      (.bind channel pusher-event #(put! ws-ch [message {:data %
                                                         :channel-name channel-name
                                                         :context context}])))
    (.bind channel "pusher:subscription_error"
           #(put! ws-ch [:subscription-error {:channel-name channel-name
                                              :status %}]))
    channel))

(defn unsubscribe [pusher-instance channel-name]
  (.unsubscribe pusher-instance channel-name))

(defn subscribed-channels [pusher-instance]
  (-> pusher-instance (aget "channels") (aget "channels") js-keys set))
