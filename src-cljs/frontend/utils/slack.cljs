(ns frontend.utils.slack
  (:require
   [frontend.utils :as utils]
   [cemerick.url :refer [url]]))

(def ^:private sign-in-scope "identity.basic,identity.email,identity.team,identity.avatar")
(def ^:private add-to-slack-scope "incoming-webhook,links:read,links:write,chat:write:bot,commands")

(defn- auth-url
  ([scope]
   (auth-url scope {}))
  ([scope additional-state]
   (let [auth-host (aget js/window "renderContext" "auth_host")
         state (merge {"return-to" (str js/window.location.pathname
                                        js/window.location.hash)
                       "token" (utils/oauth-csrf-token)}
                      (when (not= auth-host js/window.location.host)
                        {"delegate" (str js/window.location.protocol "//" js/window.location.host)})
                      additional-state)
         state-str (.stringify js/JSON (clj->js state))]
     (-> (url "https://slack.com/oauth/authorize")
         (assoc :query {"state" state-str
                        "scope" scope
                        "response_type" "code"
                        "client_id" (aget js/window "renderContext" "slackClientId")})
         str))))

(defn sign-in-url []
  (auth-url sign-in-scope))

(defn add-to-slack-url [vcs-url]
  (auth-url add-to-slack-scope {"vcs-url" vcs-url}))
