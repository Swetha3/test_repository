(ns frontend.zendesk
  (:require [frontend.models.user :as user]))

(defn get-widget
  "This is necessary because the global variable zE only exists once the page loads."
  []
  (aget js/window "zE"))

(def identified (atom false))

(defn identify! [widget]
  (let [zd-user (some-> js/window
                        (aget "zdUser"))]
    (when (and zd-user (not @identified))
      (.identify widget zd-user)
      (reset! identified true))))

(defn show! []
  (when-let [widget (get-widget)]
    (identify! widget)
    (.activate widget)))
