(ns frontend.components.placeholder
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.utils :as utils :refer-macros [html]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(defn placeholder [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:section "You navigated to build " (pr-str (get-in app [:inspected-project]))]))))
