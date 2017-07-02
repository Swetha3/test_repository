(ns frontend.components.pages.user-settings
  (:require [frontend.analytics :as analytics]
            [frontend.api :as api]
            [frontend.components.account :as old-components]
            [frontend.components.aside :as aside]
            [frontend.components.common :as common]
            [frontend.components.pages.user-settings.heroku :as heroku]
            [frontend.components.pages.user-settings.integrations :as integrations]
            [frontend.components.templates.main :as main-template]
            [frontend.config :as config]
            [frontend.models.user :as user]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :refer [set-page-title!] :refer-macros [component element html]]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.legacy :refer [build-legacy]]
            [frontend.utils.seq :refer [select-in]]
            [om.next :as om-next :refer-macros [defui ui]]))

(defn nav-items []
  (remove
    nil?
    [{:type :subpage :href (routes/v1-account) :title "Account Integrations" :subpage :integrations}
     {:type :subpage :href (routes/v1-account-subpage {:subpage "notifications"}) :title "Notification Settings" :subpage :notifications}
     {:type :subpage :href (routes/v1-account-subpage {:subpage "api"}) :title "Personal API Tokens" :subpage :api}
     {:type :subpage :href (routes/v1-account-subpage {:subpage "heroku"}) :title "Heroku API Key" :subpage :heroku}
     (when-not (config/enterprise?)
       {:type :subpage :href (routes/v1-account-subpage {:subpage "plans"}) :title "Organization Plans" :subpage :plans})
     (when-not (config/enterprise?)
       {:type :subpage :href (routes/v1-account-subpage {:subpage "beta"}) :title "Beta Program" :subpage :beta})]))

(defn menu [subpage]
  (html
   ;; TODO: Now that this is rendered from here, the settings menus should use a
   ;; styled component. See CIRCLE-2545.
   [:div.aside-user
    [:header
     [:h4 "User Settings"]]
    [:div.aside-user-options
     (aside/expand-menu-items (nav-items) subpage)]]))


(defn component-class-for-old-subpage
  "Takes a legacy (old-Om) component function and returns an Om Next class which
  renders it, querying for the `:legacy/state`. This lets us migrate the
  subpages to Om Next one at a time."
  [old-subpage]
  (ui
    static om-next/IQuery
    (query [this]
      '[{:legacy/state [*]}])
    Object
    (render [this]
      (build-legacy old-subpage
                    (select-in (:legacy/state (om-next/props this))
                               [state/general-message-path
                                state/user-path
                                state/projects-path
                                state/web-notifications-enabled-path])))))
(def subpage-routes
  {:integrations integrations/Subpage
   :notifications (component-class-for-old-subpage old-components/notifications)
   :heroku (component-class-for-old-subpage heroku/subpage)
   :api (component-class-for-old-subpage old-components/api-tokens)
   :plans (component-class-for-old-subpage old-components/plans)
   :beta (component-class-for-old-subpage old-components/beta-program)})

;; In the model of Compassus.
(def subpage-route->query
  (into {}
        (map (fn [[subpage-route class]]
               (when (om-next/iquery? class)
                 [subpage-route (om-next/get-query class)])))
        subpage-routes))

;; In the model of Compassus.
(def subpage-route->factory
  (zipmap (keys subpage-routes)
          (map om-next/factory (vals subpage-routes))))

(def
  ^{:doc "When the subpage-route is nil, pretend it's this."}
  default-subpage-route
  :integrations)

(defui ^:once Page
  static om-next/IQuery
  (query [this]
    ;; NB: Every Page *must* query for {:legacy/state [*]}, to make it available
    ;; to frontend.components.header/header. This is necessary until the
    ;; wrapper, not the template, renders the header.
    ;; See https://circleci.atlassian.net/browse/CIRCLE-2412
    ['{:legacy/state [*]}
     {:app/current-user [:user/login]}
     :app/subpage-route
     {:app/subpage-route-data (assoc subpage-route->query
                                     ;; Add the default route at nil.
                                     nil (subpage-route->query default-subpage-route))}])
  analytics/Properties
  (properties [this]
    (let [props (om-next/props this)]
      {:user (get-in props [:app/current-user :user/login])
       :view :account}))
  Object
  (componentDidMount [this]
    (set-page-title! "User")

    ;; Replicates the old behavior of post-navigated-to! :account.
    ;; This will go away as the subpages which use this data move to Om Next
    ;; themselves.
    (let [api-ch (om-next/shared this [:comms :api])]
      (when-not (seq (get-in (:legacy/state (om-next/props this)) state/projects-path))
        (api/get-projects api-ch))
      (ajax/ajax :get "/api/v1/sync-github" :me api-ch)
      (api/get-orgs api-ch :include-user? true)
      (ajax/ajax :get "/api/v1/user/token" :tokens api-ch)))
  (render [this]
    (component
      (let [legacy-state (:legacy/state (om-next/props this))
            subpage-route (:app/subpage-route (om-next/props this) default-subpage-route)]
        (main-template/template
         {:app legacy-state
          :crumbs [{:type :account}]
          :sidebar (menu subpage-route)
          :main-content
          (element :main-content
            (html
             [:div
              (build-legacy common/flashes (get-in legacy-state state/error-message-path))
              ((get subpage-route->factory subpage-route) (:app/subpage-route-data (om-next/props this)))]))})))))
