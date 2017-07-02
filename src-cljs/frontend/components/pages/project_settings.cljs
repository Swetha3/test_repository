(ns frontend.components.pages.project-settings
  (:require [frontend.components.aside :as aside]
            [frontend.components.project-settings :as project-settings]
            [frontend.components.templates.main :as main-template]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :refer-macros [html]]
            [om.core :as om :include-macros true]))

(defn page [app owner]
  (reify
    om/IRender
    (render [_]
      (main-template/template
       {:app app
        :sidebar (om/build aside/project-settings-menu app)
        :main-content (om/build project-settings/project-settings app)
        :header-actions (let [project (get-in app state/project-path)
                              project-name (:reponame project)
                              project-user (:username project)
                              vcs-type (:vcs-type project)]
                          (html
                           [:a.header-settings-link {:href (routes/v1-dashboard-path {:org project-user
                                                                                      :repo project-name
                                                                                      :vcs_type vcs-type})}
                            (str "View " project-name " »")]))}))))
