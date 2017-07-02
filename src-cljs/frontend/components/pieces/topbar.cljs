(ns frontend.components.pieces.topbar
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.api :as api]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.pieces.icon :as icon]
            [frontend.config :as config]
            [frontend.models.organization :as org]
            [frontend.routes :as routes]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.bitbucket :as bb-utils]
            [frontend.utils.html :as html]
            [om.core :as om])
  (:require-macros [frontend.utils :refer [component html]]))

(defn orgs-dropdown-selector [orgs selected-org current-route owner]
  [:li.dropdown.org-dropdown
   [:button.dropdown-toggle.org-button
    {:data-toggle "dropdown"
     :aria-haspopup "true"
     :aria-expanded "false"}
    [:.avatar
     [:img.gravatar.selected-org-icon {:src (gh-utils/make-avatar-url selected-org :size 60)}]]
    [:.org-name
     [:span.selected-org-name (:login selected-org)]
     [:i.material-icons.org-picker-arrow "keyboard_arrow_down"]]]

   [:ul.dropdown-menu.pull-right.animated.slideInDown.nav-items.org-dropdown-menu
    [:li.org-dropdown-menu__item.switch-org-text
     "Switch Organization"]
    (for [{:as org :keys [login vcs_type]} orgs]
      (let [org-same? (org/same? org selected-org)]
        [:li
         [:a.org-dropdown-menu__item {:class (when org-same? "selected-org")
                                      :href (routes/org-centric-path {:current-org org
                                                                      :nav-point current-route})}
          [:span.org-icon-and-name
           [:span.leading-dot
            "●"]
           [:img.avatar {:src (gh-utils/make-avatar-url org :size 40)}]
           [:span.org-name login]]
          [:span.vcs-icon
           (case vcs_type
             "github" (icon/github)
             "bitbucket" (icon/bitbucket))]]]))]])

(defn updates-dropdown-list
  [class-name]
  [:ul {:class class-name}
   (when-not (config/enterprise?)
     [:li [:a.nav-item {:href "https://circleci.com/changelog/"
                        :target "_blank"}
           [:div.heading "Changelog"]
           [:div.description "The latest CircleCI product updates."]]])
   [:li [:a.nav-item {:href "https://discuss.circleci.com/c/announcements"
                      :target "_blank"}
         [:div.heading "Announcements"]
         [:div.description "Important announcements from CircleCI about upcoming features and updates."]]]])

(defn updates-dropdown []
  [:li.dropdown.updates-menu
   [:button.dropdown-toggle
    {:data-toggle "dropdown"
     :aria-haspopup "true"
     :aria-expanded "false"}
    "Updates" [:i.material-icons "keyboard_arrow_down"]]
   (updates-dropdown-list "dropdown-menu pull-right animated slideInDown")])

(defn support-dropdown-list
  [owner class-name]
  [:ul {:class class-name}
   [:li
    [:a.nav-item {:href "https://circleci.com/docs/"
                  :target "_blank"}
     [:div.heading "Docs"]
     [:div.description "Read about building, testing, and deploying your software on CircleCI."]]]
   [:li
    [:a.nav-item {:href "https://discuss.circleci.com/"
                  :target "_blank"}
     [:div.heading "Discuss"]
     [:div.description "Get help and meet developers talking about testing, continuous integration, and continuous delivery."]]]
   [:li
    [:a.nav-item (common/contact-support-a-info owner)
     [:div.heading "Support"]
     [:div.description "Send us a message. We are here to help!"]]]
   [:li
    [:a.nav-item {:href "https://discuss.circleci.com/c/feature-request"
                  :target "_blank"}
     [:div.heading "Suggest a feature"]]]
   [:li
    [:a.nav-item {:href "https://discuss.circleci.com/c/bug-reports"
                  :target "_blank"}
     [:div.heading "Report an issue"]]]])

(defn support-dropdown [owner]
  [:li.dropdown.support-menu
   [:button.dropdown-toggle
    {:data-toggle "dropdown"
     :aria-haspopup "true"
     :aria-expanded "false"}
    "Support" [:i.material-icons "keyboard_arrow_down"]]
   (support-dropdown-list owner "dropdown-menu pull-right animated slideInDown")])

(defn user-dropdown-list
  [user class-name]
  [:ul {:class class-name}
   [:li [:a.nav-item {:href "/account"} [:div.heading "User settings"]]]
   (when (:admin user)
     [:li [:a.nav-item {:href "/admin"} [:div.heading "Admin"]]])
   [:li [:a.nav-item {:href "/logout"} [:div.heading "Log out"]]]])

(defn user-dropdown [user]
  [:li.dropdown.user-menu
   [:button.dropdown-toggle
    {:data-toggle "dropdown"
     :aria-haspopup "true"
     :aria-expanded "false"}
    [:img.gravatar {:src (gh-utils/make-avatar-url user :size 60)}]]
   (user-dropdown-list user "dropdown-menu pull-right animated slideInDown")])

(defn hamburger-dropdown [user owner]
  [:li.dropdown.hamburger-menu
   [:button.dropdown-toggle
    {:data-toggle "dropdown"
     :aria-haspopup "true"
     :aria-expanded "false"}
    [:i.material-icons "menu"]]
   [:ul.dropdown-menu.pull-right.animated.slideInDown
     [:li.hamburger-options
      [:div.heading.hamburger "User"]
      (user-dropdown-list user "hamburger-list")]
     [:li.hamburger-options
      [:div.heading.hamburger "Support"]
      (support-dropdown-list owner "hamburger-list")]
     [:li.hamburger-options
      [:div.heading.hamburger "Updates"]
      (updates-dropdown-list "hamburger-list")]]])

(defn- disable-org-picker?
  [current-route]
  (contains? #{:route/account :admin-settings} current-route))

(defn topbar
  "A bar which sits at the top of the screen and provides navigation and context.

  :user - The current user. We display their avatar in the bar.
  :orgs - all the orgs that the user is a part of
  :selected-org - The currently selected organization in the top bar."
  [{:keys [user orgs selected-org current-route]} owner]
  (reify
    om/IRender
    (render [_]
      (component
        (html
          [:div
           [:div.navs-container
            [:ul.nav-options.collapsing-nav {:class (when (disable-org-picker? current-route) "disable")}
              (orgs-dropdown-selector orgs selected-org current-route owner)]

            [:a.logomark {:href "/dashboard"
                          :aria-label "Dashboard"}
             (common/ico :logo)]
            [:ul.nav-options
             (updates-dropdown)
             (support-dropdown owner)
             (user-dropdown user)
             (hamburger-dropdown user owner)]]])))))

(dc/do
  (defcard topbar
    (om/build topbar {:user {:avatar_url "https://avatars.githubusercontent.com/u/5826638"}
                      :selected-org {:name "lorem"
                                     :vcs_type "github"}})))
