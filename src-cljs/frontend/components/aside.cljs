(ns frontend.components.aside
  (:require [clojure.string :refer [lower-case]]
            [frontend.analytics :as analytics]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.pieces.popover :as popover]
            [frontend.components.pieces.status :as status]
            [frontend.config :as config]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.models.feature :as feature]
            [frontend.models.organization :as org]
            [frontend.models.plan :as pm]
            [frontend.models.project :as project-model]
            [frontend.models.user :as user]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [html]]
            [frontend.utils.github :as gh-utils]
            [goog.string :as gstring]
            [om.core :as om :include-macros true]))

(defn project-settings-link [{:keys [project]} owner]
  (when (and (project-model/can-write-settings? project))
    (let [org-name (:username project)
          repo-name (:reponame project)
          vcs-type (:vcs_type project)]
      [:a.project-settings-icon {:href (routes/v1-project-settings-path {:vcs_type vcs-type
                                                                         :org org-name
                                                                         :repo repo-name})
                                 :title (str (project-model/project-name project)
                                             " settings")
                                 :on-click #((om/get-shared owner :track-event) {:event-type :project-settings-clicked
                                                                                 :properties {:org org-name
                                                                                              :repo repo-name}})}
       [:i.material-icons "settings"]])))

(defn branch-list [{:keys [branches
                           show-all-branches?
                           navigation-data]}
                   owner
                   {:keys [identities
                           show-project?
                           workflows?
                           ;; only used when workflows? is true
                           om-next-parent]}]
  (reify
    om/IDisplayName (display-name [_] "Aside Branch List")
    om/IRender
      (render [_]
        (let [branches-filter (if show-all-branches?
                                (constantly true)
                                (partial project-model/personal-branch? identities))]
          (html
           [:ul.branches
            (for [branch (filter branches-filter branches)]
              (let [project (:project branch)
                    latest-build (last (sort-by :build_num (concat (:running_builds branch)
                                                                   (:recent_builds branch))))
                    vcs-type (project-model/vcs-type project)
                    org-name (project-model/org-name project)
                    repo-name (project-model/repo-name project)
                    branch-identifier (:identifier branch)]
                [:li {:key (hash [vcs-type org-name repo-name branch-identifier])
                      :class (when (and (= vcs-type (:vcs_type navigation-data))
                                        (= org-name (:org navigation-data))
                                        (= repo-name (:repo navigation-data))
                                        (= (name branch-identifier)
                                           (:branch navigation-data)))
                               "selected")}
                 [:a {:href (if workflows?
                              (routes/v1-project-branch-workflows-path (:vcs_type project)
                                                                       (:username project)
                                                                       (:reponame project)
                                                                       (-> branch-identifier name gstring/urlDecode))
                              (routes/v1-dashboard-path {:vcs_type (:vcs_type project)
                                                         :org (:username project)
                                                         :repo (:reponame project)
                                                         :branch (name branch-identifier)}))
                      :on-click (let [event {:event-type :branch-clicked
                                             :properties {:repo repo-name
                                                          :org org-name
                                                          :branch (name branch-identifier)}}]
                                  ;; if this is being rendered for
                                  ;; workflows, use the om-next
                                  ;; analytics mechanism. For links,
                                  ;; this is necessary because using
                                  ;; the om-previous analytics
                                  ;; mechanism will record the `:view`
                                  ;; property as the route being
                                  ;; navigated *to*, rather than the
                                  ;; view that's active at the time of
                                  ;; the click.
                                  (if workflows?
                                    #(analytics/track! om-next-parent event)
                                    #((om/get-shared owner :track-event) event)))}
                  [:.branch
                   [:.last-build-status (status/build-icon (build-model/build-status latest-build))]
                   [:.branch-info
                    (when show-project?
                      [:.project-name
                       {:title (project-model/project-name project)}
                       (project-model/project-name project)])
                    [:.branch-name
                     {:title (utils/display-branch branch-identifier)}
                     (utils/display-branch branch-identifier)]
                    (let [last-activity-time (project-model/most-recent-activity-time branch)]
                      [:.last-build-info
                       {:title (when last-activity-time
                                 (datetime/full-datetime (js/Date.parse last-activity-time)))}
                       (if last-activity-time
                         (list
                          (om/build common/updating-duration
                                    {:start last-activity-time}
                                    {:opts {:formatter datetime/time-ago}})
                          " ago")
                         "never")])]]]
                 (when show-project?
                   (project-settings-link {:project project} owner))]))])))))

(defn project-aside [{:keys [project
                             show-all-branches?
                             navigation-data
                             expanded-repos]}
                     owner
                     {:keys [identities
                             workflows?
                             ;; only used when workflows? is true
                             om-next-parent]}]
  (reify
    om/IDisplayName (display-name [_] "Aside Project")
    om/IRender
    (render [_]
      (let [vcs-url (:vcs_url project)
            vcs-type (project-model/vcs-type project)
            org-name (project-model/org-name project)
            repo-name (project-model/repo-name project)]
        (html [:li
               [:.project-heading
                {:class (when (and (= vcs-type (:vcs_type navigation-data))
                                   (= org-name (:org navigation-data))
                                   (= repo-name (:repo navigation-data))
                                   (not (contains? navigation-data :branch)))
                          "selected")
                 :title (project-model/project-name project)}
                [:i.fa.rotating-chevron {:class (when (expanded-repos vcs-url) "expanded")
                                         :on-click #(raise! owner [:expand-repo-toggled {:repo vcs-url}])}]
                [:a.project-name {:href (if workflows?
                                          (routes/v1-project-workflows-path (:vcs_type project)
                                                                            (:username project)
                                                                            (:reponame project))
                                          (routes/v1-project-dashboard-path {:vcs_type (:vcs_type project)
                                                                             :org (:username project)
                                                                             :repo (:reponame project)}))
                                  :on-click (let [event {:event-type :project-clicked
                                                         :properties {:vcs-type (:vcs_type project)
                                                                      :org org-name
                                                                      :repo repo-name
                                                                      :component "branch-picker"}}]
                                              ;; if this is being rendered for
                                              ;; workflows, use the om-next
                                              ;; analytics mechanism. For links,
                                              ;; this is necessary because using
                                              ;; the om-previous analytics
                                              ;; mechanism will record the `:view`
                                              ;; property as the route being
                                              ;; navigated *to*, rather than the
                                              ;; view that's active at the time of
                                              ;; the click.
                                              (if workflows?
                                                #(analytics/track! om-next-parent
                                                                   event)
                                                #((om/get-shared owner :track-event)
                                                  event)))}
                 (if (feature/enabled? "top-bar-ui-v-1")
                   (project-model/repo-name project)
                   (project-model/project-name project))]
                (project-settings-link {:project project} owner)]

               (when (expanded-repos vcs-url)
                 (om/build branch-list
                           {:branches (->> project
                                           project-model/branches
                                           (sort-by (comp lower-case name :identifier)))
                            :show-all-branches? show-all-branches?
                            :navigation-data navigation-data}
                           {:opts {:identities identities
                                   :workflows? workflows?
                                   :om-next-parent om-next-parent}}))])))))

(defn expand-menu-items [items subpage]
  (for [item items]
    (case (:type item)

      :heading
      [:header.aside-item.aside-heading {:key (hash item)}
       (:title item)]

      :subpage
      [:a.aside-item {:key (hash item)
                      :href (:href item)
                      :class (when (= subpage (:subpage item)) "active")}
       (:title item)])))

(defn project-settings-nav-items [data owner]
  (let [navigation-data (:navigation-data data)
        project (get-in data state/project-path)
        feature-flags (project-model/feature-flags project)]
    (remove nil?
      [{:type :heading :title "Project Settings"}
       {:type :subpage :href "edit" :title "Overview" :subpage :overview}
       {:type :subpage :href (routes/v1-org-settings-path navigation-data) :title "Org Settings"
        :class "project-settings-to-org-settings"}
       {:type :heading :title "Build Settings"}
       ;; Both conditions are needed to handle special cases like non-enterprise stanging
       ;; instances that don't have OS X beta enabled.
       (when (or (not (config/enterprise?)) (contains? feature-flags :osx))
         {:type :subpage :href "#build-environment" :title "Build Environment" :subpage :build-environment})
       (when (project-model/parallel-available? project)
         {:type :subpage :href "#parallel-builds" :title "Adjust Parallelism" :subpage :parallel-builds})
       {:type :subpage :href "#env-vars" :title "Environment Variables" :subpage :env-vars}
       {:type :subpage :href "#advanced-settings" :title "Advanced Settings" :subpage :advanced-settings}
       (when (or (feature/enabled? :project-cache-clear-buttons)
                 (config/enterprise?))
         {:type :subpage :href "#clear-caches" :title "Clear Caches" :subpage :clear-caches})
       {:type :heading :title "Test Commands"}
       {:type :subpage :href "#setup" :title "Dependency Commands" :subpage :setup}
       {:type :subpage :href "#tests" :title "Test Commands" :subpage :tests}
       {:type :heading :title "Notifications"}
       {:type :subpage :href "#hooks" :title "Chat Notifications" :subpage :hooks}
       {:type :subpage :href "#webhooks" :title "Webhook Notifications" :subpage :webhooks}
       {:type :subpage :href "#badges" :title "Status Badges" :subpage :badges}
       {:type :heading :title "Permissions"}
       {:type :subpage :href "#checkout" :title "Checkout SSH keys" :subpage :checkout}
       {:type :subpage :href "#ssh" :title "SSH Permissions" :subpage :ssh}
       {:type :subpage :href "#api" :title "API Permissions" :subpage :api}
       {:type :subpage :href "#aws" :title "AWS Permissions" :subpage :aws}
       {:type :subpage :href "#jira-integration" :title "JIRA Integration" :subpage :jira-integration}
       (when (project-model/osx? project)
         {:type :subpage :href "#code-signing" :title "Code Signing" :subpage :code-signing})
       {:type :heading :title "Continuous Deployment"}
       {:type :subpage :href "#heroku" :title "Heroku Deployment" :subpage :heroku}
       {:type :subpage :href "#aws-codedeploy" :title "AWS CodeDeploy" :subpage :aws-codedeploy}
       {:type :subpage :href "#deployment" :title "Other Deployments" :subpage :deployment}])))

(defn project-settings-menu [app owner]
  (reify
    om/IRender
    (render [_]
      (let [subpage (-> app :navigation-data :subpage)]
        (html
         [:div.aside-user
          [:div.aside-user-options
           (expand-menu-items (project-settings-nav-items app owner) subpage)]])))))

(defn org-settings-nav-items [plan {org-name :name
                                    org-vcs-type :vcs_type
                                    :as org-data}]
  (concat
   [{:type :heading :title "Plan"}
    {:type :subpage :title "Overview" :href "#" :subpage :overview}]
   (when-not (config/enterprise?)
     (if (pm/piggieback? plan org-name org-vcs-type)
       [{:type :subpage :href "#containers" :title "Add Containers" :subpage :containers}]
       (concat
         [{:type :subpage :title "Plan Settings" :href "#containers" :subpage :containers}]
         (when (pm/stripe-customer? plan)
           [{:type :subpage :title "Billing & Statements" :href "#billing" :subpage :billing}])
         (when (pm/transferrable-or-piggiebackable-plan? plan)
           [{:type :subpage :title "Share & Transfer" :href "#organizations" :subpage :organizations}]))))
   [{:type :heading :title "Organization"}
    {:type :subpage :href "#projects" :title "Projects" :subpage :projects}
    {:type :subpage :href "#users" :title "Users" :subpage :users}]))

(defn admin-settings-nav-items []
  (concat
    [{:type :subpage :href "/admin" :title "Overview" :subpage :overview}
     {:type :subpage :href "/admin/fleet-state" :title "Fleet State" :subpage :fleet-state}]
    (when-not (config/enterprise?)
      [{:type :subpage :href "/admin/switch" :title "Switch User" :subpage :switch}])
    (when (config/enterprise?)
      [{:type :subpage :href "/admin/management-console" :title "Management Console"}
       {:type :subpage :href "/admin/license" :title "License" :subpage :license}
       {:type :subpage :href "/admin/users" :title "Users" :subpage :users}
       {:type :subpage :href "/admin/projects" :title "Projects" :subpage :projects}
       {:type :subpage :href "/admin/system-settings" :title "System Settings" :subpage :system-settings}])))

(defn admin-settings-menu [app owner]
  (reify
    om/IRender
    (render [_]
      (let [subpage (-> app :navigation-data :subpage)]
        (html
         [:div.aside-user
          [:header [:h4 "Admin Settings"]]
          [:div.aside-user-options
           (expand-menu-items (admin-settings-nav-items) subpage)]])))))

(defn redirect-org-settings-subpage
  "Piggiebacked plans can't go to :containers, :organizations, :billing, or :cancel.
  Un-piggiebacked plans shouldn't be able to go to the old 'add plan' page. This function
  selects a different page for these cases."
  [subpage plan org-name vcs-type]
  (cond ;; Redirect :plan to :containers for paid plans that aren't piggiebacked.
        (and plan
             (not (pm/piggieback? plan org-name vcs-type))
             (= subpage :plan))
        :containers

        ;; Redirect :organizations, :billing, and :cancel to the overview page
        ;; for piggiebacked plans.
        (and plan
             (pm/piggieback? plan org-name vcs-type)
             (#{:organizations :billing :cancel} subpage))
        :overview

        :else subpage))

(defn org-settings-menu [app owner]
  (reify
    om/IRender
    (render [_]
      (let [plan (get-in app state/org-plan-path)
            org-data (get-in app state/org-data-path)
            subpage (redirect-org-settings-subpage (-> app :navigation-data :subpage) plan (:name org-data) (:vcs_type org-data))
            items (org-settings-nav-items plan org-data)]
        (html
         [:div.aside-user [:header.aside-item.aside-heading "Organization Settings"]
          [:div.aside-user-options
           (expand-menu-items items subpage)]])))))

(defn toggle-all-builds [owner show-all-builds? disable-toggle?]
  [:header.toggle-all-builds
   [:label {:class (if disable-toggle?
                     "disabled tooltip-anchor"
                     (if show-all-builds? "" "checked"))}
    "My builds"
    [:input {:name "toggle-all-builds"
             :type "radio"
             :value "false"
             :checked (not show-all-builds?)
             :react-key "toggle-all-builds-my-builds"
             :disabled disable-toggle?
             :on-change #(raise! owner [:show-all-builds-toggled false])}]]
   [:label {:class (if disable-toggle?
                     "disabled"
                     (if show-all-builds? "checked" ""))}
    "All builds"
    [:input {:name "toggle-all-builds"
             :type "radio"
             :value "true"
             :checked show-all-builds?
             :react-key "toggle-all-builds-all-builds"
             :disabled disable-toggle?
             :on-change #(raise! owner [:show-all-builds-toggled true])}]]])

(defn builds-table-filter [{:keys [data on-branch?]} owner]
  ;; Create an additional filter for the builds-table to feed into
  ; Currently just duplicating the my branches / all fiels
  (reify
    om/IRender
    (render [_]
      (let [show-all-builds? (get-in data state/show-all-builds-path)
            disable-toggle? on-branch?
            toggle-all-builds (toggle-all-builds owner show-all-builds? disable-toggle?)]
        (html
          [:div.dashboard-top-menu
           (if disable-toggle?
             (popover/popover {:title nil
                               :body [:span "My / All builds sort is not available on branches."]
                               :placement :left
                               :trigger-mode :hover}
               toggle-all-builds)
             toggle-all-builds)])))))

(defn collapse-group-id [project]
  "Computes a hash of the project id.  Includes the :current-branch if
  available.  The hashing is performed because this data is stored on
  the client side and we don't want to leak data"
  (let [project-id (project-model/id project)
        branch (:current-branch project)]
    (utils/md5 (str project-id branch))))

(defn branch-activity-list [app
                            owner
                            {:keys [workflows?
                                    ;; only used when workflows? is true
                                    om-next-parent]}]
  (reify
    om/IRender
    (render [_]
      (let [show-all-branches? (get-in app state/show-all-branches-path)
            expanded-repos (get-in app state/expanded-repos-path)
            sort-branches-by-recency? (get-in app state/sort-branches-by-recency-path false)
            selected-org (get-in app state/selected-org-path)
            projects (cond->> (get-in app state/projects-path)
                       (feature/enabled? "top-bar-ui-v-1") (filter #(project-model/belong-to-org? % selected-org)))
            user (get-in app state/user-path)
            identities (:identities user)]
        (html
         [:div.aside-activity
          [:header
           [:select {:class "toggle-sorting"
                     :name "toggle-sorting"
                     :on-change #(raise! owner [:sort-branches-toggled
                                                (utils/parse-uri-bool (.. % -target -value))])
                     :value (pr-str sort-branches-by-recency?)}
            [:option {:value "false"} "By project"]
            [:option {:value "true"} "Recent"]]

           [:div.toggle-all-branches
            [:input {:id "my-branches"
                     :name "toggle-all-branches"
                     :type "radio"
                     :value "false"
                     :checked (not show-all-branches?)
                     :on-change #(raise! owner [:show-all-branches-toggled false])}]
            [:label {:for "my-branches"}
             "My branches"]
            [:input {:id "all-branches"
                     :name "toggle-all-branches"
                     :type "radio"
                     :value "true"
                     :checked show-all-branches?
                     :on-change #(raise! owner [:show-all-branches-toggled true])}]
            [:label {:for "all-branches"}
             "All branches"]]]

           (if sort-branches-by-recency?
             (om/build branch-list
                       {:branches (->> projects
                                       project-model/sort-branches-by-recency
                                       ;; Arbitrary limit on visible branches.
                                       (take 100))
                        :show-all-branches? show-all-branches?
                        :navigation-data (:navigation-data app)}
                       {:opts {:identities identities
                               :show-project? true
                               :workflows? workflows?
                               :om-next-parent om-next-parent}})
             [:ul.projects
              (for [project (sort project-model/sidebar-sort projects)]
                (om/build project-aside
                          {:project project
                           :show-all-branches? show-all-branches?
                           :expanded-repos expanded-repos
                           :navigation-data (:navigation-data app)}
                          {:react-key (project-model/id project)
                           :opts {:identities identities
                                  :workflows? workflows?
                                  :om-next-parent om-next-parent}}))])

          (when (empty? projects)
            [:.no-projects-followed-card
             (card/basic
               (html
                 [:div
                  [:p "No projects listed?"]
                  (button/link {:href (routes/v1-add-projects-path (org/for-route selected-org))
                                :kind :secondary}
                    [:span
                     [:i.material-icons "library_add"]
                     "Add projects"])]))])
          (when-not (user/has-private-scopes? user)
            [:.add-private-project-card
             (card/basic
               (html
                 [:div
                  [:p "Something missing?"]
                  (button/link {:href (gh-utils/auth-url)
                                :on-click #((om/get-shared owner :track-event) {:event-type :add-private-repos-clicked
                                                                                :properties {:component "branch-picker"}})
                                :kind :secondary}
                    [:span
                     [:i.octicon.octicon-mark-github]
                     "Add private projects"])]))])])))))

(defn- aside-nav-clicked
  [owner event-name]
  ((om/get-shared owner :track-event) {:event-type event-name
                                       :properties {:component "left-nav"}}))


(defn aside-nav-original [{:keys [user current-route owner]}]
  "Original side-nav, preserved for most users as the top-bar-ui-v-1
   is implemented. Case-by-case feature flags too cumbersome as routes change"
  (html
    [:nav.aside-left-nav
     [:a.aside-item.logo {:title "Dashboard"
                          :data-placement "right"
                          :data-trigger "hover"
                          :href (routes/v1-dashboard-path {})
                          :on-click #(aside-nav-clicked owner :logo-clicked)}
      [:div.logomark
       (common/ico :logo)]]

     [:a.aside-item {:class (when (contains? state/dashboard-routes current-route) "current")
                     :data-placement "right"
                     :data-trigger "hover"
                     :title "Builds"
                     :href (routes/v1-dashboard-path {})
                     :on-click #(aside-nav-clicked owner :builds-icon-clicked)}
      [:i.material-icons "storage"]
      [:div.nav-label "Builds"]]

     [:a.aside-item {:class (when (contains? state/workflows-routes current-route) "current")
                     :data-placement "right"
                     :data-trigger "hover"
                     :title "Workflows"
                     :href (routes/v1-workflows)}
      (icon/workflows)
      [:div.nav-label "Workflows"]]

     [:a.aside-item {:class (when (contains? state/insights-routes current-route)
                              "current")
                     :data-placement "right"
                     :data-trigger "hover"
                     :title "Insights"
                     :href "/build-insights"
                     :on-click #(aside-nav-clicked owner :insights-icon-clicked)}
      [:i.material-icons "assessment"]
      [:div.nav-label "Insights"]]

     (if (config/enterprise?)
       [:a.aside-item {:class (when (= :add-projects current-route)
                                "current")
                       :href "/add-projects",
                       :data-placement "right"
                       :data-trigger "hover"
                       :title "Projects"
                       :on-click #(aside-nav-clicked owner :add-project-icon-clicked)}
        [:i.material-icons "library_add"]
        [:div.nav-label "Projects"]]

       [:a.aside-item {:class (when (= :route/projects current-route)
                                "current")
                       :title "Projects"
                       :data-placement "right"
                       :data-trigger "hover"
                       :href "/projects"
                       :on-click #(aside-nav-clicked owner :projects-icon-clicked)}
        [:i.material-icons "book"]
        [:div.nav-label "Projects"]])

     [:a.aside-item {:class (when (= :team current-route) "current")
                     :href "/team",
                     :data-placement "right"
                     :data-trigger "hover"
                     :title "Team"
                     :on-click #(aside-nav-clicked owner :team-icon-clicked)}
      [:i.material-icons "group"]
      [:div.nav-label "Team"]]

     [:a.aside-item {:class (when (= :route/account current-route) "current")
                     :data-placement "right"
                     :data-trigger "hover"
                     :title "User Settings"
                     :href "/account"
                     :on-click #(aside-nav-clicked owner :account-settings-icon-clicked)}
      [:i.material-icons "settings"]
      [:div.nav-label "User Settings"]]

     [:a.aside-item {:title "Documentation"
                     :data-placement "right"
                     :data-trigger "hover"
                     :target "_blank"
                     :href "https://circleci.com/docs/"
                     :on-click #(aside-nav-clicked owner :docs-icon-clicked)}
      [:i.material-icons "description"]
      [:div.nav-label "Docs"]]

     (when-not user/support-eligible?
       [:a.aside-item (merge (common/contact-support-a-info owner)
                        {:title "Support"
                         :data-placement "right"
                         :data-trigger "hover"
                         :data-bind "tooltip: {title: 'Support', placement: 'right', trigger: 'hover'}"
                         :on-click #(aside-nav-clicked owner :support-icon-clicked)})
        [:i.material-icons "chat"]
        [:div.nav-label "Support"]])

     (when-not (config/enterprise?)
       [:a.aside-item {:data-placement "right"
                       :data-trigger "hover"
                       :title "Changelog"
                       :target "_blank"
                       :href "https://circleci.com/changelog/"
                       :on-click #(aside-nav-clicked owner :changelog-icon-clicked)}
        [:i.material-icons "receipt"]
        [:div.nav-label "Changelog"]]


       [:a.aside-item {:data-placement "right"
                       :data-trigger "hover"
                       :title "What's New"
                       :target "_blank"
                       :href "https://circleci.com/beta-access/"
                       :on-click #(aside-nav-clicked owner :beta-icon-clicked)}
        (icon/engine-new)
        [:div.nav-label "What's New"]])

     (when (:admin user)
       [:a.aside-item {:class (when (= :admin-settings current-route) "current")
                       :data-placement "right"
                       :data-trigger "hover"
                       :title "Admin"
                       :href "/admin"
                       :on-click #(aside-nav-clicked owner :admin-icon-clicked)}
        [:i.material-icons "build"]
        [:div.nav-label "Admin"]])

     [:a.aside-item.push-to-bottom {:data-placement "right"
                                    :data-trigger "hover"
                                    :title "Log Out"
                                    :href "/logout"
                                    :on-click #(aside-nav-clicked owner :logout-icon-clicked)}
      [:i.material-icons "power_settings_new"]
      [:div.nav-label "Log Out"]]]))

(defn aside-nav-new [{:keys [current-route org owner]}]
  "New side nav, to be used in conjunction with the top bar"
  (html
    [:nav.aside-left-nav
     [:a.aside-item {:class (when (contains? state/dashboard-routes current-route)
                              "current")
                     :data-placement "right"
                     :data-trigger "hover"
                     :title "Builds"
                     :href (routes/v1-organization-dashboard-path org)
                     :on-click #(aside-nav-clicked owner :builds-icon-clicked)}
      [:i.material-icons "storage"]
      [:div.nav-label "Builds"]]

     [:a.aside-item {:class (when (contains? state/workflows-routes current-route)
                              "current")
                     :data-placement "right"
                     :data-trigger "hover"
                     :title "Workflows"
                     :href (routes/v1-org-workflows-path (:vcs_type org)
                                                         (:org org))}
      (icon/workflows)
      [:div.nav-label "Workflows"]]

     [:a.aside-item {:class (when (contains? state/insights-routes current-route)
                              "current")
                     :data-placement "right"
                     :data-trigger "hover"
                     :title "Insights"
                     :href (routes/v1-organization-insights-path org)
                     :on-click #(aside-nav-clicked owner :insights-icon-clicked)}
      [:i.material-icons "assessment"]
      [:div.nav-label "Insights"]]

     [:a.aside-item {:class (when (or (= :route/projects current-route)
                                      (= :add-projects current-route))
                              "current")
                     :title "Projects"
                     :data-placement "right"
                     :data-trigger "hover"
                     :href (routes/v1-organization-projects-path org)
                     :on-click #(aside-nav-clicked owner :projects-icon-clicked)}
      [:i.material-icons "book"]
      [:div.nav-label "Projects"]]

     [:a.aside-item {:class (when (= :team current-route) "current")
                     :href (routes/v1-team-path org)
                     :data-placement "right"
                     :data-trigger "hover"
                     :title "Team"
                     :on-click #(aside-nav-clicked owner :team-icon-clicked)}
      [:i.material-icons "group"]
      [:div.nav-label "Team"]]

     (when (:admin? org)
       [:a.aside-item {:class (when (= :org-settings current-route) "current")
                       :data-placement "right"
                       :data-trigger "hover"
                       :title "Organization Settings"
                       :href (routes/v1-org-settings-path org)
                       :on-click #(aside-nav-clicked owner :settings-icon-clicked)}
        (icon/org-settings)
        [:div.nav-label "Settings"]])]))

(defn aside-nav [{:keys [user current-route org]} owner]
  (reify
    om/IDisplayName (display-name [_] "Aside Nav")
    om/IDidMount
    (did-mount [_]
      (utils/tooltip ".aside-item"))
    om/IRender
    (render [_]
      (let [args {:current-route current-route
                  :owner owner}]
        (if (feature/enabled? "top-bar-ui-v-1")
          (aside-nav-new (merge args {:org {:org (org/name org)
                                            :vcs_type (:vcs_type org)
                                            :admin? (user/org-admin-authorized? user org)}}))
          (aside-nav-original (merge args {:user user})))))))
