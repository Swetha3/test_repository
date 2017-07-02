(ns frontend.intercom
  (:require [frontend.utils :as utils :include-macros true]
            [goog.dom :as gdom]
            [goog.dom.classlist :as class-list]))

(defn raise-dialog [ch]
  (try
    (js/Intercom "show")
    (catch :default e
      (utils/notify-error ch "Uh-oh, our Help system isn't available. Please email us instead, at sayhi@circleci.com")
      (utils/merror e))))

(defn user-link []
  (let [path (.. js/window -location -pathname)
        re (js/RegExp. "/gh/([^/]+/[^/]+)")]
    (when-let [match (.match path re)]
      (str "https://www.intercom.io/apps/vnk4oztr/users"
           "?utf8=%E2%9C%93"
           "&filters%5B0%5D%5Battr%5D=custom_data.pr-followed"
           "&filters%5B0%5D%5Bcomparison%5D=contains&filters%5B0%5D%5Bvalue%5D="
           (second path)))))

(defn track [event & [metadata]]
  (utils/swallow-errors (js/Intercom "trackEvent" (name event) (clj->js metadata))))

(defn get-root []
  (gdom/getElement "intercom-container"))

(defn enable! []
  (class-list/add js/document.body "circle-intercom"))
