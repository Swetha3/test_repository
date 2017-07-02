(ns frontend.components.jira-modal
  (:require [frontend.components.common :as common]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.dropdown :as dropdown]
            [frontend.components.pieces.form :as form]
            [frontend.components.pieces.modal :as modal]
            [frontend.components.pieces.spinner :as spinner]
            [frontend.components.forms :as forms]
            [frontend.models.project :as project-model]
            [frontend.state :as state]
            [om.core :as om :include-macros true]
            [frontend.async :refer [navigate! raise!]]
            [frontend.utils :refer-macros [defrender html]]
            [frontend.utils.vcs-url :as vcs-url]))

(defn jira-modal [{:keys [project jira-data close-fn]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:jira-project nil
       :issue-type nil
       :summary nil
       :description (-> js/window .-location .-href (str "\n"))})
    om/IWillMount
    (will-mount [_]
      (let [project-name (vcs-url/project-name (:vcs_url project))
            vcs-type (project-model/vcs-type project)]
        (raise! owner [:load-jira-projects {:project-name project-name :vcs-type vcs-type}])
        (raise! owner [:load-jira-issue-types {:project-name project-name :vcs-type vcs-type}])))
    om/IDidMount
    (did-mount [_]
      ((om/get-shared owner :track-event) {:event-type :jira-modal-impression}))
    om/IRenderState
    (render-state [_ {:keys [jira-project issue-type summary description] :as state}]
      (let [project-name (vcs-url/project-name (:vcs_url project))
            vcs-type (project-model/vcs-type project)
            jira-projects (:projects jira-data)
            issue-types (:issue-types jira-data)
            first-jira-project (some-> jira-projects first)
            first-issue-type (some-> issue-types first)]
        (modal/modal-dialog
          {:title "Create an issue in JIRA"
           :body
           (html
            [:div
             (if-not (and jira-projects issue-types)
               (spinner/spinner)
               (form/form {}
                          (dropdown/dropdown {:label "Project name"
                                              ;; We can't make the first piece of data we
                                              ;; receive the default choice in the
                                              ;; dropdown. If the values of the dropdowns
                                              ;; remain as nil, they haven't been
                                              ;; interacted with, and so the first item
                                              ;; from the data we received is the currently
                                              ;; chosen item.
                                              :value (or jira-project first-jira-project)
                                              :options (or (some->> jira-projects
                                                                    (map #(into [% %] nil)))
                                                           [["No projects" "No projects"]])
                                              :on-change #(om/set-state! owner :jira-project %)})
                          (dropdown/dropdown {:label "Issue type"
                                              :value (or issue-type first-issue-type)
                                              :options (or (some->> issue-types
                                                                    (map #(into [% %] nil)))
                                                           [["No issue types" "No issue types"]])
                                              :on-change #(om/set-state! owner :issue-type %)})
                          (om/build form/text-field {:label "Issue summary"
                                                     :value summary
                                                     :on-change #(om/set-state! owner :summary (.. % -target -value))})
                          (om/build form/text-area {:label "Description"
                                                    :value description
                                                    :on-change #(om/set-state! owner :description (.. % -target -value))})))])
           :actions [(button/button {:on-click close-fn
                                     :kind :flat}
                                    "Cancel")
                     (button/managed-button
                       {:on-click #(raise! owner [:create-jira-issue {:project-name project-name
                                                                      :vcs-type vcs-type
                                                                      :jira-issue-data {:project (or jira-project first-jira-project)
                                                                                        :type (or issue-type first-issue-type)
                                                                                        :summary summary
                                                                                        :description description}
                                                                      :on-success close-fn}])
                        :primary? true
                        :loading-text "Creating..."
                        :success-text "Created"
                        :failed-text "Failed"}
                       "Create JIRA Issue")]
           :close-fn close-fn})))))

