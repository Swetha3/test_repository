(ns frontend.components.pages.workflow
  (:require [frontend.analytics :as analytics]
            [frontend.api :as api]
            [frontend.components.aside :as aside]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.pieces.run-row :as run-row]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.components.templates.main :as main-template]
            [frontend.models.organization :as organization]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :refer [set-page-title!] :refer-macros [component html]]
            [frontend.utils.legacy :refer [build-legacy]]
            [om.core :as om]
            [om.next :as om-next :refer-macros [defui]]))

(defn- a-or-span [flag href & children]
  (if flag
    [:a {:href href} children]
    [:span children]))

(defui ^:once RunList
  static om-next/IQuery
  (query [this]
    [:connection/total-count
     :connection/offset
     {:connection/edges [{:edge/node (om-next/get-query run-row/RunRow)}]}])
  Object
  (render [this]
    (component
      (let [{:keys [connection/total-count connection/offset connection/edges]} (om-next/props this)
            {:keys [empty-state prev-page-href next-page-href]} (om-next/get-computed this)]
        (cond
          (not (contains? (om-next/props this) :connection/total-count))
          (html
           [:div
            [:.page-info]
            (card/collection (repeatedly 10 run-row/loading-run-row))])

          (not (pos? total-count))
          empty-state

          :else
          (html
           [:div
            [:.page-info "Showing " [:span.run-numbers (inc offset) "–" (+ offset (count edges))]]
            (card/collection
             (map #(if % (run-row/run-row (:edge/node %)) (run-row/loading-run-row)) edges))
            [:.list-pager
             (a-or-span (pos? offset)
                        prev-page-href
                        "← Newer workflow runs")
             (a-or-span (> total-count (+ offset (count edges)))
                        next-page-href
                        "Older workflow runs →")]]))))))

(def run-list (om-next/factory RunList))

(defui ^:once ProjectWorkflowRuns
  static om-next/IQuery
  (query [this]
    [:project/name
     {:project/organization [:organization/vcs-type :organization/name]}
     `{(:routed/page {:page/connection :project/workflow-runs
                      :page/count 30})
       ~(om-next/get-query RunList)}
     {'[:app/route-params _] [:page/number]}])
  Object
  (render [this]
    (component
      (let [props (om-next/props this)
            page-num (get-in props [:app/route-params :page/number])
            {project-name :project/name
             {vcs-type :organization/vcs-type
              org-name :organization/name} :project/organization} props]
        (run-list (om-next/computed
                   (:routed/page props)
                   {:prev-page-href
                    (routes/v1-project-workflows-path vcs-type org-name project-name (dec page-num))

                    :next-page-href
                    (routes/v1-project-workflows-path vcs-type org-name project-name (inc page-num))

                    :empty-state
                    (card/basic
                     (empty-state/empty-state
                      {:icon (icon/workflows)
                       :heading (html [:span (empty-state/important (str org-name "/" project-name)) " has no workflows configured"])
                       :subheading (html
                                    [:span
                                     "To orchestrate multiple jobs, add the workflows key to your config.yml file."
                                     [:br]
                                     " See "
                                     [:a {:href "https://circleci.com/docs/2.0/workflows"}
                                      "the workflows documentation"]
                                     " for examples and instructions."])}))}))))))

(def project-workflow-runs (om-next/factory ProjectWorkflowRuns))

(defn- settings-link
  "An Om Next compatible version of frontend.components.header/settings-link"
  [vcs-type org repo]
  (html
   [:div.btn-icon
    [:a.header-settings-link.project-settings
     {:href (routes/v1-project-settings-path {:vcs_type vcs-type
                                              :org org
                                              :repo repo})
      :title "Project settings"}
     [:i.material-icons "settings"]]]))

(defn- legacy-branch-picker
  "Wraps the branch picker in a legacy component which can load the project data
  on mount."
  [{:keys [app org]} owner {:keys [om-next-parent]}]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [projects-loaded? (seq (get-in app state/projects-path))
            current-user (get-in app state/user-path)]
        (when (and (not projects-loaded?)
                   (not (empty? current-user)))
          (api/get-projects (om/get-shared owner [:comms :api])))))

    om/IRender
    (render [_]
      (om/build aside/branch-activity-list
                (assoc-in app state/selected-org-path (organization/modern-org->legacy-org org))
                {:opts {:workflows? true
                        :om-next-parent om-next-parent}}))))

(defui ^:once ProjectPage
  static om-next/IQuery
  (query [this]
    [{:legacy/state ['*]}
     {:app/current-user [:user/login]}
     {:routed-entity/organization [:organization/vcs-type
                                   :organization/name]}
     {:routed-entity/project [:project/name]}
     {'(:project-for-runs {:< :routed-entity/project}) (om-next/get-query ProjectWorkflowRuns)}])
  analytics/Properties
  (properties [this]
    (let [props (om-next/props this)]
      {:user (get-in props [:app/current-user :user/login])
       :view :route/project-workflows
       :org (get-in props [:routed-entity/organization :organization/name])
       :vcs-type (get-in props [:routed-entity/organization :organization/vcs-type])
       :repo (get-in props [:routed-entity/project :project/name])}))
  Object
  (componentDidMount [this]
    (set-page-title! "CircleCI"))
  (render [this]
    (let [{{org-name :organization/name
            vcs-type :organization/vcs-type} :routed-entity/organization
           {project-name :project/name} :routed-entity/project}
          (om-next/props this)]
      (main-template/template
       {:app (:legacy/state (om-next/props this))
        :crumbs [{:type :workflows}
                 {:type :org-workflows
                  :username org-name
                  :vcs_type vcs-type}
                 {:type :project-workflows
                  :username org-name
                  :project project-name
                  :vcs_type vcs-type}]
        :header-actions (settings-link vcs-type org-name project-name)
        :sidebar (build-legacy legacy-branch-picker
                               {:app (:legacy/state (om-next/props this))
                                :org (:routed-entity/organization (om-next/props this))}
                               {:opts {:om-next-parent this}})
        :main-content (if-let [project (:project-for-runs (om-next/props this))]
                        (project-workflow-runs project)
                        (spinner))}))))

(defui ^:once BranchWorkflowRuns
  static om-next/IQuery
  (query [this]
    [:branch/name
     {:branch/project [:project/name
                       {:project/organization [:organization/vcs-type
                                               :organization/name]}]}
     `{(:routed/page {:page/connection :branch/workflow-runs
                      :page/count 30})
       ~(om-next/get-query RunList)}
     {'[:app/route-params _] [:page/number]}])
  Object
  (render [this]
    (component
      (let [props (om-next/props this)
            {branch-name :branch/name
             {project-name :project/name
              {vcs-type :organization/vcs-type
               org-name :organization/name} :project/organization} :branch/project} props
            page (:routed/page props)
            page-num (get-in props [:app/route-params :page/number])]
        (run-list (om-next/computed
                   page
                   {:prev-page-href
                    (routes/v1-project-branch-workflows-path vcs-type org-name project-name branch-name (dec page-num))

                    :next-page-href
                    (routes/v1-project-branch-workflows-path vcs-type org-name project-name branch-name (inc page-num))

                    :empty-state
                    (card/basic
                     (empty-state/empty-state
                      {:icon (icon/workflows)
                       :heading (html [:span (empty-state/important (str project-name "/" branch-name)) " has no workflows configured"])
                       :subheading (html
                                     [:span
                                      "To orchestrate multiple jobs, add the workflows key to your config.yml file."
                                      [:br]
                                      "See "
                                      [:a {:href "https://circleci.com/docs/2.0/workflows"}
                                       "the workflows documentation"]
                                      " for examples and instructions."])}))}))))))

(def branch-workflow-runs (om-next/factory BranchWorkflowRuns))

(defui ^:once BranchPage
  static om-next/IQuery
  (query [this]
    [{:legacy/state ['*]}
     {:app/current-user [:user/login]}
     {:routed-entity/organization [:organization/vcs-type
                                   :organization/name]}
     {:routed-entity/project [:project/name]}
     {:routed-entity/branch [:branch/name]}
     {'(:branch-for-runs {:< :routed-entity/branch})
      (om-next/get-query BranchWorkflowRuns)}])
  analytics/Properties
  (properties [this]
    (let [props (om-next/props this)]
      {:user (get-in props [:app/current-user :user/login])
       :view :route/project-branch-workflows
       :org (get-in props [:routed-entity/organization :organization/name])
       :vcs-type (get-in props [:routed-entity/organization :organization/vcs-type])
       :repo (get-in props [:routed-entity/project :project/name])
       :branch (get-in props [:routed-entity/branch :branch/name])}))
  Object
  (componentDidMount [this]
    (set-page-title! "CircleCI"))
  (render [this]
    (let [{{org-name :organization/name
            vcs-type :organization/vcs-type} :routed-entity/organization
           {project-name :project/name} :routed-entity/project
           {branch-name :branch/name} :routed-entity/branch
           branch-for-runs :branch-for-runs}
          (om-next/props this)]
      (main-template/template
       {:app (:legacy/state (om-next/props this))
        :crumbs [{:type :workflows}
                 {:type :org-workflows
                  :username org-name
                  :vcs_type vcs-type}
                 {:type :project-workflows
                  :username org-name
                  :project project-name
                  :vcs_type vcs-type}
                 {:type :branch-workflows
                  :username org-name
                  :project project-name
                  :vcs_type vcs-type
                  :branch branch-name}]
        :header-actions (settings-link vcs-type org-name project-name)
        :sidebar (build-legacy legacy-branch-picker
                               {:app (:legacy/state (om-next/props this))
                                :org (:routed-entity/organization (om-next/props this))}
                               {:opts {:om-next-parent this}})
        :main-content (if-let [branch (:branch-for-runs (om-next/props this))]
                        (branch-workflow-runs branch)
                        (spinner))}))))

(defui ^:once OrgWorkflowRuns
  static om-next/IQuery
  (query [this]
    [:organization/name
     :organization/vcs-type
     `{(:routed/page {:page/connection :organization/workflow-runs
                      :page/count 30})
       ~(om-next/get-query RunList)}
     {'[:app/route-params _] [:page/number]}])
  Object
  (render [this]
    (component
      (let [props (om-next/props this)
            {vcs-type :organization/vcs-type
             org-name :organization/name} props
            page (:routed/page props)
            page-num (get-in props [:app/route-params :page/number])]
        (run-list (om-next/computed
                   page
                   {:prev-page-href
                    (routes/v1-org-workflows-path vcs-type org-name (dec page-num))

                    :next-page-href
                    (routes/v1-org-workflows-path vcs-type org-name (inc page-num))

                    :empty-state
                    (card/basic
                     (empty-state/empty-state
                      {:icon (icon/workflows)
                       :heading (html [:span (empty-state/important org-name) " has no workflows defined yet"])
                       :subheading (str "Add a workflow section to one of " org-name "'s project configs to start running workflows.")}))}))))))

(def org-workflow-runs (om-next/factory OrgWorkflowRuns))

(defui ^:once OrgPage
  static om-next/IQuery
  (query [this]
    ['{:legacy/state [*]}
     `{(:org-for-crumb {:< :routed-entity/organization})
       [:organization/vcs-type
        :organization/name]}
     `{(:org-for-runs {:< :routed-entity/organization})
       ~(om-next/get-query OrgWorkflowRuns)}])
  ;; TODO: Add the correct analytics properties.
  #_analytics/Properties
  #_(properties [this]
      (let [props (om-next/props this)]
        {:user (get-in props [:app/current-user :user/login])
         :view :projects
         :org (get-in props [:app/route-data :route-data/organization :organization/name])}))
  Object
  (componentDidMount [this]
    (set-page-title! "CircleCI"))
  (render [this]
    (let [{{org-name :organization/name
            vcs-type :organization/vcs-type} :org-for-crumb}
          (om-next/props this)]
      (main-template/template
       {:app (:legacy/state (om-next/props this))
        :crumbs [{:type :workflows}
                 {:type :org-workflows
                  :username org-name
                  :vcs_type vcs-type}]
        :sidebar (build-legacy legacy-branch-picker {:app (:legacy/state (om-next/props this))
                                                     :org (:org-for-crumb (om-next/props this))})
        :main-content (if-let [org (:org-for-runs (om-next/props this))]
                        (org-workflow-runs org)
                        (spinner))}))))

(defui ^:once SplashPage
  static om-next/IQuery
  (query [this]
    ['{:legacy/state [*]}
     {:routed-entity/organization [:organization/vcs-type
                                   :organization/name]}])
  ;; TODO: Add the correct analytics properties.
  #_analytics/Properties
  #_(properties [this]
      (let [props (om-next/props this)]
        {:user (get-in props [:app/current-user :user/login])
         :view :projects
         :org (get-in props [:app/route-data :route-data/organization :organization/name])}))
  Object
  (componentDidMount [this]
    (set-page-title! "CircleCI"))
  (render [this]
    (main-template/template
     {:app (:legacy/state (om-next/props this))
      :crumbs [{:type :workflows}]
      :sidebar (build-legacy legacy-branch-picker {:app (:legacy/state (om-next/props this))
                                                   :org (:routed-entity/organization (om-next/props this))})
      :main-content (card/basic
                     (empty-state/empty-state
                      {:icon (icon/workflows)
                       :heading (html [:span "Welcome to CircleCI " (empty-state/important "Workflows")])
                       :subheading "Select a project to get started."}))})))
