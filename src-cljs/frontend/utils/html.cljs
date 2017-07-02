(ns frontend.utils.html
  ;; need to require runtime for the macro to work :/
  (:require [hiccups.runtime :as hiccupsrt])
  (:require-macros [hiccups.core :as hiccups]))

(defn hiccup->html-str [hiccup-form]
  (hiccups/html hiccup-form))
