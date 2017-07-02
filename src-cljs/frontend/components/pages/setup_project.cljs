(ns frontend.components.pages.setup-project
  (:require [frontend.async :refer [raise!]]
            [frontend.components.setup-project :as setup-project]
            [frontend.components.templates.main :as main-template]
            [om.core :as om :include-macros true]))

(defn page [app owner]
  (reify
    om/IRender
    (render [_]
      (main-template/template
       (merge {:app app
               :main-content (om/build setup-project/setup-project app)})))))
