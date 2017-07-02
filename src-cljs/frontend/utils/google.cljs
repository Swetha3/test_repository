(ns frontend.utils.google
  (:require [cemerick.url :refer [url]]))

(defn auth-url []
  (-> (url (str js/window.location.protocol "//" js/window.location.host "/google-login"))
      (assoc :query {"return-to" (str js/window.location.pathname
                                      js/window.location.hash)})
      str))
