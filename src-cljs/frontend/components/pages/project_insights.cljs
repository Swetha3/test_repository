(ns frontend.components.pages.project-insights
  (:require [frontend.components.insights.project :as project-insights]
            [frontend.components.templates.main :as main-template]
            [om.core :as om :include-macros true]))

(defn page [app owner]
  (reify
    om/IRender
    (render [_]
      (main-template/template
       {:app app
        :main-content (om/build project-insights/project-insights app)
        :header-actions (om/build project-insights/header app)}))))
