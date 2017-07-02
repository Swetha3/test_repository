(ns frontend.components.header
  (:require [frontend.async :refer [raise!]]
            [frontend.config :as config]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.components.license :as license]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.page-header :as page-header]
            [frontend.components.pieces.top-banner :as top-banner]
            [frontend.components.svg :as svg]
            [frontend.config :as config]
            [frontend.models.feature :as feature]
            [frontend.models.plan :as plan]
            [frontend.models.project :as project-model]
            [frontend.notifications :as n]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [html]]
            [frontend.utils.github :refer [auth-url]]
            [frontend.utils.state :as state-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :refer [format]]
            [om.core :as om :include-macros true]))

(defn show-follow-project-button? [app]
  (when-let [project (get-in app state/project-path)]
    (and (not (:following project))
         (= (vcs-url/org-name (:vcs_url project))
            (get-in app [:navigation-data :org]))
         (= (vcs-url/repo-name (:vcs_url project))
            (get-in app [:navigation-data :repo])))))

(defn follow-project-button [project owner]
  (reify
    om/IRender
    (render [_]
      (let [project-id (project-model/id project)
            vcs-url (:vcs_url project)]
        (button/managed-button
         {:on-click #(do
                      (raise! owner [:followed-project {:vcs-url vcs-url :project-id project-id}])
                      ((om/get-shared owner :track-event) {:event-type :follow-project-clicked
                                                           :properties {:vcs-url vcs-url
                                                                        :component "header"}}))
          :loading-text "Following..."
          :failed-text "Failed to follow"
          :success-text "Followed"
          :kind :primary
          :size :small}
         "Follow Project")))))

(defn show-settings-link? [app]
  (and
    (:read-settings (get-in app state/page-scopes-path))
    (not= false (-> app :navigation-data :show-settings-link?))))

(defn settings-link [app owner]
  (let [{:keys [repo org] :as navigation-data} (:navigation-data app)]
    (cond repo (when (:write-settings (get-in app state/project-scopes-path))
                 [:div.btn-icon
                  [:a.header-settings-link.project-settings
                   {:href (routes/v1-project-settings-path navigation-data)
                    :title "Project settings"}
                   [:i.material-icons "settings"]]])
          org [:div.btn-icon
               [:a.header-settings-link.org-settings
                {:href (routes/v1-org-settings-path navigation-data)
                 :on-click #((om/get-shared owner :track-event) {:event-type :org-settings-link-clicked
                                                                 :properties {:org org
                                                                              :component "header"}})
                 :title "Organization settings"}
                [:i.material-icons "settings"]]]
          :else nil)))

(defn maybe-active [current goal]
  {:class (when (= current goal)
            "active")})

(defn outer-subheader [nav-maps nav-point]
  (map
    #(when (-> %
               keys
               set
               (contains? nav-point))
       [:div.navbar.navbar-default.navbar-fixed-top.subnav
        [:div.container-fluid
         [:ul.nav.navbar-nav
          (for [[point {:keys [path title]}] %]
            [:li.list-item (maybe-active nav-point point)
             [:a.menu-item {:href path} title]])]]])
    nav-maps))

(defn outer-header [app owner]
  (reify
    om/IDisplayName (display-name [_] "Outer Header")
    om/IRender
    (render [_]
      (let [flash (get-in app state/flash-path)
            logged-in? (get-in app state/user-path)
            nav-point (:navigation-point app)
            hamburger-state (get-in app state/hamburger-menu-path)
            first-build (first (:recent-builds app))]
        (html
          [:div.outer-header
           [:div
            (when flash
              [:div#flash {:dangerouslySetInnerHTML {:__html flash}}])]
           [:div.navbar.navbar-default {:class (case nav-point
                                                 :language-landing
                                                 (get-in app [:navigation-data :language])
                                                 :integrations
                                                 (name (get-in app [:navigation-data :integration]))
                                                 nil)
                                        :on-touch-end #(raise! owner [:change-hamburger-state])}
            [:div.container
             [:div.hamburger-menu
              (condp = hamburger-state
                "closed" [:i.fa.fa-bars.fa-2x]
                "open" [:i.fa.fa-close.fa-2x])]
             [:div.navbar-header
              [:a#logo.navbar-brand
               {:href "/"
                :on-click #(raise! owner [:clear-build-data])}
               (common/circle-logo {:width nil
                                    :height 25})]
              (if logged-in?
                [:a.mobile-nav {:href "/dashboard"} "Back to app"]
                [:a.mobile-nav.signup
                 {:href (if (config/enterprise?)
                          (auth-url)
                          "/signup/")}
                 "Sign up"])]
             [:div.navbar-container {:class hamburger-state}
              (if (and (:current-build-data app) (feature/enabled? "show-demo-label"))
                [:ul.nav.navbar-nav
                    [:li [:span.demo-label " "
                          (when first-build (format "You are viewing the CircleCI open source dashboard with builds from %s's %s repo"
                                                    (:username first-build)
                                                    (:reponame first-build)))]]]
                [:ul.nav.navbar-nav
                 (when (config/show-marketing-pages?)
                   (list
                     [:li.dropdown {:class (when (contains? #{:features
                                                              :mobile
                                                              :ios
                                                              :android
                                                              :integrations
                                                              :enterprise}
                                                            nav-point)
                                             "active")}
                      [:a.menu-item {:href "https://circleci.com/features/"}
                       "Product "
                       [:i.fa.fa-caret-down]]
                      [:ul.dropdown-menu
                       [:li {:role "presentation"}
                        [:a.sub.menu-item (merge
                                           (maybe-active nav-point :features)
                                           {:role "menuitem"
                                            :tabIndex "-1"
                                            :href "https://circleci.com/features/"})
                         "Features"]]
                       [:li {:role "presentation"}
                        [:a.sub.menu-item (merge
                                            (maybe-active nav-point :mobile)
                                            {:role "menuitem"
                                             :tabIndex "-1"
                                             :href "https://circleci.com/mobile/"})
                         "Mobile"]]
                       [:li {:role "presentation"}
                        [:a.sub.menu-item (merge
                                            (maybe-active nav-point :integrations)
                                            {:role "menuitem"
                                             :tabIndex "-1"
                                             :href "https://circleci.com/integrations/docker/"})
                         "Docker"]]
                       [:li {:role "presentation"}
                        [:a.sub.menu-item (merge
                                            (maybe-active nav-point :enterprise)
                                            {:role "menuitem"
                                             :tabIndex "-1"
                                             :href "https://circleci.com/enterprise/"})
                         "Enterprise"]]]]
                     [:li (maybe-active nav-point :pricing)
                      [:a.menu-item {:href "https://circleci.com/pricing/"} "Pricing"]]))
                 [:li (maybe-active nav-point :documentation)
                  [:a.menu-item {:href "https://circleci.com/docs/"}
                   (if (config/enterprise?)
                    "CircleCI Documentation"
                    "Documentation")]]
                 (if (config/enterprise?)
                   [:li [:a.menu-item {:href "https://circleci.com/docs/enterprise/"} "Enterprise Documentation"]])
                 [:li [:a.menu-item {:href "https://discuss.circleci.com" :target "_blank"} "Discuss"]]
                 (when (config/show-marketing-pages?)
                   (list
                     [:li {:class (when (contains? #{:about
                                                     :contact
                                                     :team
                                                     :jobs
                                                     :press}
                                                   nav-point)
                                    "active")}
                      [:a.menu-item {:href "https://circleci.com/about/"} "About Us"]]
                     [:li [:a.menu-item {:href "http://blog.circleci.com"} "Blog"]]))])

              (if logged-in?
                [:ul.nav.navbar-nav.navbar-right.back-to-app
                 [:li [:a.menu-item {:href "/dashboard"} "Back to app"]]]
                [:ul.nav.navbar-nav.navbar-right
                 [:li
                  [:a.login.login-link.menu-item {:href (if (or config/client-dev?
                                                                (config/enterprise?))
                                                          (auth-url)
                                                          "/vcs-authorize/")
                                                  :on-click #((om/get-shared owner :track-event) {:event-type :login-clicked})
                                                  :title "Log In"}
                   "Log In"]]
                 (when (not (config/enterprise?))
                   [:li
                    [:a.signup-link.btn.btn-success.navbar-btn.menu-item
                     {:href "/signup/"
                      :on-click #((om/get-shared owner :track-event) {:event-type :signup-clicked})}
                     "Sign Up"]])])]]]
           (outer-subheader
             [{:mobile {:path "/mobile"
                        :title "Mobile"}
               :ios {:path "/mobile/ios"
                     :title "iOS"}
               :android {:path "/mobile/android"
                         :title "Android"}}
              {:about {:path "/about"
                       :title "Overview"}
               :team {:path "/about/team"
                      :title "Team"}
               :contact {:path "/contact"
                         :title "Contact Us"}
               :jobs {:path "/jobs"
                      :title "Jobs"}
               :press {:path "/press"
                       :title "Press"}}
              {:enterprise {:path "/enterprise"
                            :title "Overview"}
               :azure {:path "/enterprise/azure"
                       :title "Azure"}
               :aws {:path "/enterprise/aws"
                     :title "AWS"}}]
             nav-point)])))))

(defn osx-usage-warning-banner [plan owner]
  (reify
    om/IRender
    (render [_]
      (let [{{plan-org-name :name
              plan-vcs-type :vcs_type} :org} plan]
        (html
         [:div.alert.alert-warning {:data-component `osx-usage-warning-banner}
          [:div.usage-message
           [:div.icon (om/build svg/svg {:src (common/icon-path "Info-Warning")})]
           [:div.text
            [:span "Your current usage represents "]
            [:span.usage (plan/current-months-osx-usage-% plan)]
            [:span "% of your "]
            [:a.plan-link
             {:href (routes/v1-org-settings-path {:org plan-org-name
                                                  :vcs_type plan-vcs-type})} "current OS X plan"]
            [:span ". Please "]
            [:a.plan-link {:href (routes/v1-org-settings-path {:org plan-org-name
                                                               :vcs_type plan-vcs-type
                                                               :_fragment "osx-pricing"})}
             "upgrade"]
            [:span " or reach out to your account manager if you have questions about billing."]
            [:span " See overage rates "]
            [:a.plan-link {:href (routes/v1-org-settings-path {:org plan-org-name
                                                               :vcs_type plan-vcs-type
                                                               :_fragment "osx-pricing"})}
             "here."]]]
          [:a.dismiss {:on-click #(raise! owner [:dismiss-osx-usage-banner {:current-usage (plan/current-months-osx-usage-% plan)}])}
           [:i.material-icons "clear"]]])))))

(defn trial-offer-banner [app owner]
  (let [event-data {:plan-type :paid
                    :template :t3
                    :org (get-in app state/project-plan-org-path)}]
    (reify
      om/IDidMount
      (did-mount [_]
        ((om/get-shared owner :track-event) {:event-type :trial-offer-banner-impression}))
      om/IRender
      (render [_]
        (html
          [:div.alert.offer
           [:div.text
            [:div "Projects utilizing more containers generally have faster builds and less queueing. "
             [:a {:on-click #(raise! owner [:activate-plan-trial event-data])}
              "Click here "]
             "to activate a free two-week trial of 3 additional linux containers."]]
           [:a.dismiss {:on-click #(raise! owner [:dismiss-trial-offer-banner event-data])}
            [:i.material-icons "clear"]]])))))

(defn inner-header [{:keys [app crumbs actions]} owner]
  (reify
    om/IDisplayName (display-name [_] "Inner Header")
    om/IRender
    (render [_]
      (let [user (get-in app state/user-path)
            logged-out? (not user)
            license (get-in app state/license-path)
            project (get-in app state/project-path)
            plan (get-in app state/project-plan-path)
            show-web-notif-banner? (not (get-in app state/remove-web-notification-banner-path))
            show-web-notif-banner-follow-up? (not (get-in app state/remove-web-notification-confirmation-banner-path))]
        (html
         [:header.main-head (when logged-out? {:class "guest"})
          (when (license/show-banner? license)
            (om/build license/license-banner license))
          (when logged-out?
            (om/build outer-header app))
          (when (and (= :build (:navigation-point app))
                     (project-model/feature-enabled? project :osx))
            (list
             (when (and (plan/osx? plan)
                        (plan/over-usage-threshold? plan plan/first-warning-threshold)
                        (plan/over-dismissed-level? plan (get-in app state/dismissed-osx-usage-level)))
               (om/build osx-usage-warning-banner plan))))
          (when (and (not (plan/trial? plan))
                     (= :build (:navigation-point app))
                     (not (project-model/oss? project))
                     (plan/admin? plan)
                     (feature/enabled? :offer-linux-trial)
                     (not (get-in app state/dismissed-trial-offer-banner)))
            (om/build trial-offer-banner app))
          ; only show web notifications when the user is logged in.
          (when (and user (:build (get-in app state/build-data-path)))
            (cond
              (and (= (n/notifications-permission) "default")
                   show-web-notif-banner?)
              (om/build top-banner/banner
                        {:banner-type "warning"
                         :content [:div
                                   [:span.banner-alert-icon
                                    [:img {:src (common/icon-path "Info-Info")}]]
                                   [:b "  New: "] "You can now get web notifications when your build is done! "
                                   [:a
                                    {:href "#"
                                     :on-click #(n/request-permission
                                                 (fn [response]
                                                   (raise! owner [:set-web-notifications-permissions {:enabled? (= response "granted")
                                                                                                      :response response}])))}
                                    "Click here to activate web notifications."]]
                         :impression-event-type :web-notifications-permissions-banner-impression
                         :dismiss-fn #(raise! owner [:dismiss-web-notifications-permissions-banner {:response (n/notifications-permission)}])})
              (and (not show-web-notif-banner?)
                   show-web-notif-banner-follow-up?)
              (om/build top-banner/banner
                        (let [response (n/notifications-permission)]
                          {:banner-type (case (n/notifications-permission)
                                          "default" "danger"
                                          "denied" "danger"
                                          "granted" "success")
                           :content [:div (let [not-granted-message "If you change your mind you can go to this link to turn web notifications on: "]
                                            (case (n/notifications-permission)
                                              "default" not-granted-message
                                              "denied"  not-granted-message
                                              "granted" "Thanks for turning on web notifications! If you want to change settings go to: "))
                                     [:a {:on-click #(raise! owner [:web-notifications-confirmation-account-settings-clicked {:response response}])}
                                      "User Notifications"]]
                           :dismiss-fn #(raise! owner [:dismiss-web-notifications-confirmation-banner])}))))
          (when (and (seq crumbs)
                     (or (= :build (get-in app state/current-view-path))
                         (-> user :projects empty? not)))
            (om/build page-header/header {:crumbs crumbs
                                          :logged-out? (not (:name user))
                                          :topbar-beta {:org (state-utils/last-visited-or-default-org app)
                                                        :nav-point (get-in app state/current-view-path)}
                                          :actions (cond-> []
                                                     (show-settings-link? app)
                                                     (conj (settings-link app owner))

                                                     true
                                                     (conj actions)

                                                     (show-follow-project-button? app)
                                                     (conj (html [:.follow-project (om/build follow-project-button project)])))
                                          :platform (when-let [platform (and (= :build (:navigation-point app))
                                                                             (->> state/build-data-path
                                                                                  (get-in app)
                                                                                  :build
                                                                                  :platform))]
                                                      platform)
                                          :show-nux-experience? (state-utils/show-nux-experience? app)}))])))))

(defn header [{:keys [app crumbs actions] :as props} owner]
  (reify
    om/IDisplayName (display-name [_] "Header")
    om/IRender
    (render [_]
      (if (#{:landing :error} (:navigation-point app))
        (om/build outer-header app)
        (om/build inner-header props)))))
