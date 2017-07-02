(ns frontend.components.pages.projects
  (:require [frontend.analytics :as analytics]
            [frontend.async :refer [navigate!]]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.pieces.org-picker :as org-picker]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.components.pieces.table :as table]
            [frontend.components.templates.main :as main-template]
            [frontend.data.projects :as test-data]
            [frontend.models.feature :as feature]
            [frontend.models.project :as project-model]
            [frontend.models.user :as user]
            [frontend.routes :as routes]
            [frontend.utils :refer [set-page-title!] :refer-macros [component element html]]
            [frontend.utils.function-query :as fq :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.legacy :refer [build-legacy]]
            [frontend.utils.vcs :as vcs]
            [frontend.utils.vcs-url :as vcs-url]
            [om.next :as om-next :refer-macros [defui]]))

(defn- table
  {::fq/queries {:projects (fq/merge [:project/vcs-url
                                      :project/name]
                                     (fq/get project-model/parallelism :project)
                                     (fq/get project-model/buildable-parallelism :project))
                 :plan (fq/get project-model/buildable-parallelism :plan)}}
  [c projects plan]
  (component
    (build-legacy table/table
                  {:rows projects
                   :key-fn :project/vcs-url
                   :columns [{:header "Project"
                              :cell-fn (fn [project]
                                         (let [vcs-url (:project/vcs-url project)]
                                           [:a
                                            {:href (routes/v1-project-dashboard-path
                                                     {:vcs_type (vcs-url/vcs-type vcs-url)
                                                      :org (vcs-url/org-name vcs-url)
                                                      :repo (vcs-url/repo-name vcs-url)})
                                             :on-click #(analytics/track! c {:event-type :project-clicked
                                                                             :properties {:vcs-type (vcs-url/vcs-type vcs-url)
                                                                                          :org (vcs-url/org-name vcs-url)
                                                                                          :repo (vcs-url/repo-name vcs-url)}})}
                                            (vcs-url/repo-name vcs-url)]))}

                             {:header "Parallelism"
                              :type #{:right :shrink}
                              :cell-fn (fn [project]
                                         (let [parallelism (project-model/parallelism project)
                                               buildable-parallelism (when plan (project-model/buildable-parallelism plan project))
                                               vcs-url (:project/vcs-url project)]
                                           [:a {:href (routes/v1-project-settings-path
                                                        {:vcs_type (-> vcs-url vcs-url/vcs-type vcs/->short-vcs)
                                                         :org (vcs-url/org-name vcs-url)
                                                         :repo (vcs-url/repo-name vcs-url)
                                                         :_fragment "parallel-builds"})}
                                            parallelism "x"
                                            (when buildable-parallelism (str " out of " buildable-parallelism "x"))]))}

                             {:header "Team"
                              :type #{:right :shrink}
                              :cell-fn :project/follower-count}

                             {:header "Settings"
                              :type :shrink
                              :cell-fn #(table/action-link
                                          "Settings"
                                          (icon/settings)
                                          (let [vcs-url (:project/vcs-url %)]
                                            (routes/v1-project-settings-path
                                              {:vcs_type (vcs-url/vcs-type vcs-url)
                                               :org (vcs-url/org-name vcs-url)
                                               :repo (vcs-url/repo-name vcs-url)})))}]})))

(defn- no-org-selected [available-orgs bitbucket-enabled?]
  (component
    (card/basic
     (empty-state/empty-state {:icon (if-let [orgs (seq (take 3 available-orgs))]
                                       (empty-state/avatar-icons
                                        (for [{:keys [organization/avatar-url]} orgs]
                                          (gh-utils/make-avatar-url {:avatar_url avatar-url} :size 60)))
                                       (icon/team))
                               :heading (html
                                         [:span
                                          "Get started by selecting your "
                                          (empty-state/important "organization")])
                               :subheading (str
                                            "Select your GitHub "
                                            (when bitbucket-enabled? "or Bitbucket ")
                                            "organization (or username) to view your projects.")}))))

(defui ^:once AddProjectButton
  Object
  (render [this]
    (let [{:keys [empty-state? org]} (om-next/props this)]
      (button/link
        {:href (if (feature/enabled? "top-bar-ui-v-1")
                 (routes/v1-add-projects-path {:org (:organization/name org)
                                               :vcs_type (:organization/vcs-type org)})
                 (routes/v1-add-projects))
        :kind :primary
        :size :small
        :on-click #(analytics/track! this {:event-type :add-project-clicked
                                           :properties {:is-empty-state empty-state?}})}
       "Add Project"))))

(def add-project-button (om-next/factory AddProjectButton))

(defn- no-projects-available [org]
  (empty-state/empty-state {:icon (icon/project)
                            :heading (html
                                      [:span
                                       (empty-state/important (:organization/name org))
                                       " has no projects building on CircleCI"])
                            :subheading "Let's fix that by adding a new project."
                            :action (add-project-button {:empty-state? true
                                                         :org org})}))

(defn- non-code-ident-empty-state []
  (build-legacy empty-state/full-page-empty-state
    {:name "Projects"
     :icon (icon/project)
     :description "A list of your software projects with important summary information about each project’s pricing plan, team size, and settings link."
     :demo-heading "Demos"
     :demo-description "The following list is shown for demonstration. Click the Parallelism link to see the current  number of parallel builds (1x, 2x, 3x) and the total number of containers in use in a demo plan. Click the Settings icon to see the Overview page and the full list of organization, build, test, notification, permission, and continuous deployment settings for each demo project."
     :content
     (card/basic
      (build-legacy table/table
                    {:rows (test-data/non-code-identity-table-data)
                     :key-fn :project/vcs-url
                     :columns [{:header "Project"
                                :cell-fn :reponame}

                               {:header "Parallelism"
                                :type #{:right :shrink}
                                :cell-fn :parallel}

                               {:header "Team"
                                :type #{:right :shrink}
                                :cell-fn :follower-count}

                               {:header "Settings"
                                :type :shrink
                                :cell-fn #(table/action-link "Settings" (icon/settings) "#")}]}))}))


(defui ^:once OrgProjects
  static om-next/Ident
  (ident [this {:keys [organization/vcs-type organization/name]}]
    [:organization/by-vcs-type-and-name {:organization/vcs-type vcs-type :organization/name name}])
  static om-next/IQuery
  (query [this]
    [:organization/vcs-type
     :organization/name
     {:organization/projects (fq/merge [:project/follower-count]
                                       (fq/get table :projects))}
     {:organization/plan (fq/get table :plan)}])
  Object
  (render [this]
    (component
      (let [{:keys [organization/vcs-type organization/name organization/projects organization/plan]} (om-next/props this)
            vcs-icon (case vcs-type
                       "github" (icon/github)
                       "bitbucket" (icon/bitbucket)
                       nil)]
        (card/titled {:title (element :title
                               (html
                                [:div
                                 [:.vcs-icon vcs-icon]
                                 name]))}
                     (if projects
                       (if-let [projects-with-followers
                                (seq (remove #(zero? (:project/follower-count %)) projects))]
                         (table this projects-with-followers plan)
                         (no-projects-available (om-next/props this)))
                       (spinner)))))))

(def org-projects (om-next/factory OrgProjects))

(defui ^:once Page
  static om-next/IQuery
  (query [this]
    ;; NB: Every Page *must* query for {:legacy/state [*]}, to make it available
    ;; to frontend.components.header/header. This is necessary until the
    ;; wrapper, not the template, renders the header.
    ;; See https://circleci.atlassian.net/browse/CIRCLE-2412
    ['{:legacy/state [*]}
     {:app/current-user [{:user/organizations (om-next/get-query org-picker/Organization)}
                         :user/login
                         :user/bitbucket-authorized?
                         :user/github-oauth-scopes]}

     `{(:org-for-projects {:< :routed-entity/organization}) ~(om-next/get-query OrgProjects)}
     `{:routed-entity/organization [:organization/name]}])
  analytics/Properties
  (properties [this]
    (let [props (om-next/props this)]
      {:user (get-in props [:app/current-user :user/login])
       :view :projects
       :org (get-in props [:routed-entity/organization :organization/name :organization/vcs-type])}))
  Object
  (componentDidMount [this]
    (set-page-title! "Projects"))
  (render [this]
    (component
      (let [current-user (:app/current-user (om-next/props this))
            not-have-code-identity? (not (user/has-code-identity? current-user))]
        (main-template/template
          {:app (:legacy/state (om-next/props this))
           :selected-org (:routed-entity/organization (om-next/props this))
           :crumbs [{:type :projects}]
           :header-actions (when-not not-have-code-identity?
                             (add-project-button {:empty-state? false
                                                  :org (:routed-entity/organization (om-next/props this))}))
           :main-content
           (if not-have-code-identity?
             (non-code-ident-empty-state)
             (element
               :main-content
               (let [orgs (get-in (om-next/props this) [:app/current-user :user/organizations])]
                 (html
                   [:div
                    (when-not (feature/enabled? "top-bar-ui-v-1")
                      [:.sidebar
                       (card/basic
                         (if orgs
                           (org-picker/picker
                             {:orgs orgs
                              :selected-org (first (filter #(= (select-keys (:routed-entity/organization (om-next/props this)) [:organization/vcs-type :organization/name])
                                                               (select-keys % [:organization/vcs-type :organization/name]))
                                                           orgs))
                              :on-org-click (fn [{:keys [organization/vcs-type organization/name]}]
                                              (analytics/track! this {:event-type :org-clicked
                                                                      :properties {:login name
                                                                                   :vcs_type vcs-type}})
                                              (navigate! this (routes/v1-organization-projects-path {:org name :vcs_type vcs-type})))})
                           (spinner)))])
                    [:.main
                     (if-let [selected-org (:org-for-projects (om-next/props this))]
                       (org-projects selected-org)
                       (no-org-selected orgs (:user/bitbucket-authorized? current-user)))]]))))})))))
