(ns frontend.components.pieces.page-header
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.async :refer [raise!]]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.popover :as popover]
            [frontend.models.feature :as feature]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [component html]]
            [frontend.utils.devcards :refer [iframe]]
            [om.core :as om :include-macros true]))

(defn crumb-node [{:keys [active name path track-event-type demo? logged-out?]} owner]
  "Individual breadcrumbs in page header.

   :demo? - Boolean value to indicate whether or not a header should be visible
            to external (e.g. OSS) viewers of the builds page, such as those
            viewing a demo of CircleCI."
  (reify
    om/IRender
    (render [_]
      (component
        (html
          (if (or (not logged-out?)
                  (and logged-out? demo?))
            (if active
              [:li.active
               [:a {:disabled true :title name} name " "]]
              [:li
               [:a {:href path
                    :title name
                    :on-click (when track-event-type
                                #((om/get-shared owner :track-event) {:event-type track-event-type}))}
                   name " "]])
            [:li
             [:span name]]))))))

(defmulti crumb
  (fn [{:keys [type]}] type))

(defmethod crumb :default
  [attrs]
  (om/build crumb-node attrs))

(defmethod crumb :dashboard
  [{:keys [owner logged-out?]}]
  (om/build crumb-node {:name "Builds"
                        :path (routes/v1-dashboard-path {})
                        :track-event-type :breadcrumb-dashboard-clicked
                        :demo? false
                        :logged-out? logged-out?}))

(defmethod crumb :workflows-dashboard
  [{:keys [owner]}]
  (om/build crumb-node {:name "Jobs"
                        :path (routes/v1-dashboard-path {})
                        :track-event-type :breadcrumb-workflows-dashboard-clicked}))

(defmethod crumb :project-workflows
  [{:keys [username project vcs_type]}]
  (om/build crumb-node
            {:name project
             :path (routes/v1-project-workflows-path vcs_type
                                                     username
                                                     project)}))

(defmethod crumb :branch-workflows
  [{:keys [username project vcs_type branch]}]
  (om/build crumb-node
            {:name branch
             :path (routes/v1-project-branch-workflows-path vcs_type
                                                            username
                                                            project
                                                            branch)}))

(defmethod crumb :org-workflows
  [{:keys [username vcs_type]}]
  (om/build crumb-node
            {:name username}))

(defmethod crumb :workflows
  [_]
  (om/build crumb-node {:name "Workflows"}))

(defmethod crumb :workflow-run
  [{:keys [run/id]}]
  (om/build crumb-node
            {:name (str id)
             :path (routes/v1-run {:run-id id})}))

(defmethod crumb :project
  [{:keys [vcs_type username project active owner logged-out?]}]
  (om/build crumb-node {:name project
                        :path (routes/v1-dashboard-path {:vcs_type vcs_type :org username :repo project})
                        :active active
                        :track-event-type :breadcrumb-project-clicked
                        :demo? true
                        :logged-out? logged-out?}))

(defmethod crumb :project-settings
  [{:keys [vcs_type username project active]}]
  (om/build crumb-node {:name "project settings"
                        :path (routes/v1-project-settings-path {:vcs_type vcs_type :org username :repo project})
                        :active active}))

(defmethod crumb :project-branch
  [{:keys [vcs_type username project branch active tag owner logged-out?]}]
  (om/build crumb-node {:name (cond
                                tag (utils/trim-middle (utils/display-tag tag) 45)
                                branch (utils/trim-middle (utils/display-branch branch) 45)
                                :else "...")
                        :path (when branch
                               (routes/v1-dashboard-path {:vcs_type vcs_type
                                                          :org username
                                                          :repo project
                                                          :branch branch}))
                        :track-event-type :breadcrumb-branch-clicked
                        :active active
                        :demo? true
                        :logged-out? logged-out?}))

(defmethod crumb :build
  [{:keys [vcs_type username project build-num active]}]
  (om/build crumb-node {:name (str "build " build-num)
                        :track-event-type :breadcrumb-build-clicked
                        :demo? true
                        :path (routes/v1-build-path vcs_type username project nil build-num)
                        :active active}))

(defmethod crumb :workflow-job
  [{:keys [vcs_type username project build-num active]}]
  (om/build crumb-node {:name (str "job " build-num)
                        :track-event-type :breadcrumb-build-clicked
                        :path (routes/v1-build-path vcs_type username project nil build-num)
                        :active active}))

(defmethod crumb :workflow
  [{:keys [vcs_type username project workflow-id active]}]
  (om/build crumb-node {:name (str "workflow " workflow-id)
                        :track-event-type :breadcrumb-workflow-clicked
                        :path (routes/v1-project-workflows-path vcs_type
                                                                username
                                                                project)
                        :active active}))

(defmethod crumb :org
  [{:keys [vcs_type username active owner logged-out?]}]
  (om/build crumb-node {:name username
                        :track-event-type :breadcrumb-org-clicked
                        :demo? false
                        :path (routes/v1-dashboard-path {:vcs_type vcs_type
                                                         :org username})
                        :active active
                        :logged-out? logged-out?}))

(defmethod crumb :org-settings
  [{:keys [vcs_type username active]}]
  (om/build crumb-node {:name "organization settings"
                        :path (routes/v1-org-settings-path {:org username
                                                            :vcs_type vcs_type})
                        :active active}))

(defmethod crumb :add-projects
  [attrs]
  (om/build crumb-node {:name "Add Projects"
                        :path (routes/v1-add-projects)}))

(defmethod crumb :projects
  [attrs]
  (om/build crumb-node {:name "Projects"
                        :path (routes/v1-projects)}))

(defmethod crumb :admin
  [attrs]
  (om/build crumb-node {:name "Admin"
                        :path (routes/v1-admin)}))

(defmethod crumb :team
  [attrs]
  (om/build crumb-node {:name "Team"
                        :path (routes/v1-team)}))

(defmethod crumb :account
  [attrs]
  (om/build crumb-node {:name "User"
                        :path (routes/v1-account)}))

(defmethod crumb :settings-base
  [attrs]
  (om/build crumb-node {:name "Settings"
                        :active false}))

(defmethod crumb :build-insights
  [attrs]
  (om/build crumb-node {:name "Insights"
                        :path (routes/v1-insights)
                        :active false}))

(defn engine-2 []
  (component
    (html
      [:svg {:xmlns "http://www.w3.org/2000/svg"
             :viewBox "0 0 40 23"}
       [:g
        [:rect.rect {:rx "3"}]
        [:text.text
         [:tspan {:x "8" :y "17"}
          "2.0"]]]])))

(defn platform-tooltip [href tracking-fn]
  (popover/tooltip
    {:body
     (html
       [:div "This build ran on 2.0. "
        [:div [:a {:href href
                   :target "_blank"
                   :on-click #(tracking-fn {:event-type :beta-link-clicked
                                            :properties {:href href}})}
               "Learn more →"]]])
     :placement :bottom}
    (engine-2)))

(defn header
  "The page header.

  :crumbs      - The breadcrumbs to display.
  :logged-out? - Whether or not a viewer is logged out
                 If they are logged out, then page is being viewed from the outer .com,
                 typically to view of an OSS-project
  :actions     - (optional) A component (or collection of components) which will be
                 placed on the right of the header. This is where page-wide actions are
                 placed.
  :topbar-beta - Temporarily in place while user top-bar-ui-v-1 feature flag. Provides
                 nav-point and org to allow for toggling into and out of topbar beta view"
  [{:keys [crumbs actions logged-out? platform topbar-beta show-nux-experience?]} owner]
  (let [crumbs-login (map #(assoc % :logged-out? logged-out?) crumbs)
        has-topbar? (feature/enabled? "top-bar-ui-v-1")
        toggle-topbar (when-not has-topbar? "top-bar-ui-v-1")
        toggle-topbar-text (if has-topbar?
                             "Leave Beta UI"
                             "Join Beta UI")
        om-next-page? (-> topbar-beta :nav-point routes/om-next-nav-point?)]
    (reify
      om/IDisplayName (display-name [_] "User Header")
      om/IRender
      (render [_]
        (component
          (html
            [:div
             [:ol.breadcrumbs
              (map crumb crumbs-login)]
             [:.actions
              (when (= platform "2.0")
                (platform-tooltip "https://circleci.com/docs/2.0/"
                                  (om/get-shared owner :track-event)))
              (when (feature/enabled? "top-bar-beta-button")
                [:div.topbar-toggle
                 (when (and has-topbar? (not om-next-page?))
                   [:span.feedback
                    (button/link {:fixed? true
                                  :kind :primary
                                  :size :small
                                  :target "_blank"
                                  :href "mailto:beta+ui@circleci.com?Subject=Topbar%20UI%20Feedback"
                                  :on-click #((om/get-shared owner :track-event) {:event-type :feedback-clicked
                                                                                  :properties {:component "topbar"
                                                                                               :treatment "top-bar-beta"}})}
                      "Provide Beta UI Feedback")])])

              (when (and (false? om-next-page?)
                         (false? show-nux-experience?))
                (button/link {:fixed? true
                              :kind (if has-topbar?
                                      :secondary
                                      :primary)
                              :size :small
                              :href (if has-topbar?
                                      "/dashboard"
                                      (routes/org-centric-path {:nav-point (:nav-point topbar-beta)
                                                                :current-org (:org topbar-beta)}))
                              :on-click #(do
                                           (raise! owner [:preferences-updated {state/user-betas-key [toggle-topbar]}])
                                           ((om/get-shared owner :track-event) {:event-type :topbar-toggled
                                                                                :properties {:toggle-topbar-text toggle-topbar-text
                                                                                             :has-topbar has-topbar?
                                                                                             :toggled-topbar-on (boolean toggle-topbar)}}))}
                  toggle-topbar-text))
              actions]]))))))

(dc/do
  (def ^:private crumbs
    [{:type :dashboard}
     {:type :org
      :username "some-org"
      :vcs_type "github"}
     {:type :project
      :username "some-org"
      :project "a-project"
      :vcs_type "github"}
     {:type :project-branch
      :username "some-org"
      :project "a-project"
      :vcs_type "github"
      :branch "a-particular-branch"}
     {:type :build
      :username "some-org"
      :project "a-project"
      :build-num 66908
      :vcs_type "github"}])

  (defcard header-with-no-actions
           (iframe
             {:width "992px"}
             (om/build header {:crumbs crumbs})))

  (defcard header-with-no-actions-narrow
           (iframe
             {:width "991px"}
             (om/build header {:crumbs crumbs})))

  (defcard header-with-actions
           (iframe
             {:width "992px"}
             (om/build header {:crumbs crumbs
                               :actions [(button/button {} "Do Something")
                                         (button/button {:kind :primary} "Do Something")]})))

  (defcard header-with-actions-narrow
           (iframe
             {:width "991px"}
             (om/build header {:crumbs crumbs
                               :actions [(button/button {} "Do Something")
                                         (button/button {:kind :primary} "Do Something")]})))

  (defcard engine-2.0-icon
           (engine-2)))
