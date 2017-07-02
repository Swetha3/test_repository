(ns frontend.utils.launchdarkly
    (:refer-clojure :exclude [exists?]))

(defn feature-on?
  "True if the named feature is on in LaunchDarkly.

  Do not use this function directly; instead use
  `frontend.models.feature/enabled?`."
  ([feature-name default]
   (when (and (aget js/window "ldclient") js/ldclient.toggle)
     (.toggle js/ldclient feature-name default)))
  ([feature-name]
   (feature-on? feature-name nil)))

(defn identify [user]
  (when (and (aget js/window "ldclient") js/ldclient.identify)
   (.identify js/ldclient (clj->js (merge {:key (aget js/ldUser "key")} user)))))

(def local-lduser-state (atom nil))

(defn load-lduser! []
  (swap! local-lduser-state
         (fn [v]
           (if v
             v
             (or (js->clj js/ldUser) {})))))

(defn reidentify! [f]
  (load-lduser!)
  (swap! local-lduser-state f)
  (identify @local-lduser-state))

(defn merge-custom-properties! [m]
  (reidentify! #(update-in % ["custom"] merge m)))

(defn exists? [feature-name]
  (not (nil? (feature-on? feature-name nil))))
