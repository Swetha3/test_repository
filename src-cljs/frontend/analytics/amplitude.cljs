(ns frontend.analytics.amplitude
  (:require [goog.net.cookies :as cookies]
            [frontend.utils :as utils :refer-macros [swallow-errors]]))

(def session-cookie-name "amplitude-session-id")

(def session-expiry-delay (* 30 60))

(defn session-id []
  (swallow-errors
    (or (js/amplitude.getSessionId) -1)))

(defn set-session-id-cookie!
  "Cookies the user with their amplitude session-id. Set the cookie
  path to be root so that it is available anywhere under the circleci domain.

  Using an expiry delay of 30 minutes to mimic Amplitude's default session length:
  https://amplitude.zendesk.com/hc/en-us/articles/231275508-Amplitude-2-0-User-Sessions"
  []
  (cookies/set session-cookie-name (session-id) session-expiry-delay "/"))
