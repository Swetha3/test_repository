(ns frontend.utils.github
  (:require [clojure.string :as string]
            [frontend.config :as config]
            [frontend.utils :as utils]
            [goog.string :as gstring]
            [goog.string.format]
            [cemerick.url :refer [url]]))

(defn http-endpoint []
  (config/github-endpoint))

;; TODO: except for the addition of signup, this behavior was buried in coffeescript.  figure out if it's still what we want for the rest.
(defn check-outer-pages
  [url]
  (if (re-find #"/(signup|docs|about|privacy|pricing|integrations|features|home|mobile)" url)
    "/"
    url))

(defn auth-url [& {:keys [scope]
                   :or {scope ["user:email" "repo"]}}]
  (let [;; auth-host and auth-protocol indicate the circle host that
        ;; will handle the oauth redirect
        auth-host (aget js/window "renderContext" "auth_host")
        auth-protocol (aget js/window "renderContext" "auth_protocol")
        redirect (-> (str auth-protocol "://" auth-host)
                     (url  "auth/github")
                     (assoc :query (merge {"return-to" (check-outer-pages
                                                        (str js/window.location.pathname
                                                             js/window.location.hash))}
                                          (when (not= auth-host js/window.location.host)
                                            ;; window.location.protocol includes the colon
                                            {"delegate" (str js/window.location.protocol "//" js/window.location.host)})))
                     (assoc :protocol (or (aget js/window "renderContext" "auth_protocol")
                                          "https"))
                     str)]
    (-> (url (http-endpoint) "login/oauth/authorize")
        (assoc :query {"redirect_uri" redirect
                       "state" (utils/oauth-csrf-token)
                       "scope" (string/join "," scope)
                       "client_id" (aget js/window "renderContext" "githubClientId")})
        str)))

(defn third-party-app-restrictions-url []
  ;; Tried to add (config/github-client-id), but dev.circlehost:3000
  ;; doesn't pick it up... not sure why? using aget directly for moment...
  (str (config/github-endpoint)
       "/settings/connections/applications/"
       (aget js/window "renderContext" "githubClientId")))

(defn make-avatar-url [{:keys [avatar_url gravatar_id login]} & {:keys [size] :or {size 200}}]
  "Takes a map of user/org data and returns a url for the desired size of the user's avatar

  Ideally the map contains an :avatar_url key with a github avatar url, but will fall back to the best that can be done with :gravatar_id and :login if not - this is intended for the the gap while the related backend is deploying"
  (if-not (string/blank? avatar_url)
    (-> (url avatar_url)
        (assoc-in [:query "s"] size)
        str)

    ;; default to gravatar defaulting to github identicon
    (-> (url "https://secure.gravatar.com/avatar/" gravatar_id)
        (assoc-in [:query "s"] size)
        (assoc-in [:query "d"] (str "https://identicons.github.com/" login ".png"))
        str)))

(defn pull-request-number [url]
  "Takes a URL and returns the pull request number"
  (let [[_ number] (re-find #"/(\d+)$" url)]
    (or number "?")))
