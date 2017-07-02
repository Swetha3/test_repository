(ns frontend.controllers.navigation
  (:require [cljs.core.async :as async :refer [<!]]
            [clojure.string :as str]
            [frontend.api :as api]
            [frontend.api.path :as api-path]
            [frontend.async :refer [put!]]
            frontend.favicon
            [frontend.models.feature :as feature]
            [frontend.models.organization :as org]
            [frontend.models.user :as user]
            [frontend.pusher :as pusher]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :refer [mlog scroll! set-page-description! set-page-title!]]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.build :as build-utils]
            [frontend.utils.state :as state-utils]
            [frontend.utils.vcs :as vcs]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :as gstring])
  (:require-macros [cljs.core.async.macros :as am :refer [go]]))

;; TODO we could really use some middleware here, so that we don't forget to
;;      assoc things in state on every handler
;;      We could also use a declarative way to specify each page.
;; --- Navigation Multimethod Declarations ---

(defmulti navigated-to
  (fn [history-imp navigation-point args state]
    navigation-point))

(defmulti post-navigated-to!
  (fn [history-imp navigation-point args previous-state current-state comms]
    (frontend.favicon/reset!)
    (when (and (org/name args)
               (-> (get-in current-state state/selected-org-path)
                   (org/same? args)
                   (not)))
      (api/get-org-plan (org/name args)
                        (org/vcs-type args)
                        (:api comms)
                        :org-check))
    (put! (:ws comms) [:unsubscribe-stale-channels])
    navigation-point))

;; --- Navigation Multimethod Implementations ---

(defn navigated-default [navigation-point args state]
  (-> state
      state-utils/clear-page-state
      (assoc state/current-view navigation-point
             state/navigation-data args)))

(defmethod navigated-to :default
  [history-imp navigation-point args state]
  (navigated-default navigation-point args state))

(defn post-default [navigation-point args]
  (set-page-title! (or (:_title args)
                       (str/capitalize (name navigation-point))))
  (when :_description args
        (set-page-description! (:_description args)))
  (scroll! args))

(defmethod post-navigated-to! :default
  [history-imp navigation-point args previous-state current-state comms]
  (post-default navigation-point args))

(defmethod navigated-to :navigate!
  [history-imp navigation-point args state]
  state)

(defmethod post-navigated-to! :navigate!
  [history-imp navigation-point {:keys [path replace-token?]} previous-state current-state comms]
  (let [path (if (= \/ (first path))
               (subs path 1)
               path)]
    (if replace-token? ;; Don't break the back button if we want to redirect someone
      (.replaceToken history-imp path)
      (.setToken history-imp path))))

(defmethod navigated-to :dashboard
  [history-imp navigation-point args state]
  (-> state
      state-utils/clear-page-state
      (assoc state/current-view navigation-point
             state/navigation-data args
             state/recent-builds nil)
      (state-utils/set-dashboard-crumbs args)
      state-utils/reset-current-build
      state-utils/reset-current-project))

(defmethod post-navigated-to! :dashboard
  [history-imp navigation-point args previous-state current-state comms]
  (let [api-ch (:api comms)
        nav-ch (:nav comms)
        projects-loaded? (seq (get-in current-state state/projects-path))
        current-user (get-in current-state state/user-path)]
    (mlog (str "post-navigated-to! :dashboard with current-user? " (not (empty? current-user))
               " projects-loaded? " (not (empty? projects-loaded?))))
    (when (and (not projects-loaded?)
               (not (empty? current-user)))
      (api/get-projects api-ch))
    (go (let [builds-url (api/dashboard-builds-url (assoc (state/navigation-data current-state)
                                                          :builds-per-page (:builds-per-page current-state)
                                                          :all? (get-in current-state state/show-all-builds-path)))
              api-resp (<! (ajax/managed-ajax :get builds-url))
              scopes (:scopes api-resp)]
          (mlog (str "post-navigated-to! :dashboard, " builds-url " scopes " scopes))
          (condp = (:status api-resp)
            :success (put! api-ch [:recent-builds :success (assoc api-resp :context args)])
            :failed (put! nav-ch [:error {:status (:status-code api-resp) :inner? false}])
            (put! (:errors comms) [:api-error api-resp]))
          (when (:repo args)
            (when (:read-settings scopes)
              (api/get-project-settings (:vcs_type args) (:org args) (:repo args) api-ch)
              (ajax/ajax :get
                         (api-path/project-plan (:vcs_type args) (:org args) (:repo args))
                         :project-plan
                         api-ch
                         :context {:project-name (str (:org args) "/" (:repo args))}))))))
  (set-page-title!))

(defmethod post-navigated-to! :build-state
  [history-imp navigation-point args previous-state current-state comms]
  (let [api-ch (:api comms)]
    (when-not (seq (get-in current-state state/projects-path))
      (api/get-projects api-ch))
    (api/get-build-state api-ch))
  (set-page-title! "Build State"))

(defn- add-crumbs [state {:keys [vcs_type project-name build-num org repo tab container-id action-id]}]
  (let [{:keys [workflow-id]} (get-in state state/navigation-data-path)
        crumbs (if workflow-id
                 [{:type :workflows-dashboard}
                  {:type :org :username org :vcs_type vcs_type}
                  {:type :project :username org :project repo :vcs_type vcs_type}
                  {:type :project-branch :username org :project repo :vcs_type vcs_type}
                  {:type :workflow :username org :project repo
                   :workflow-id workflow-id
                   :vcs_type vcs_type}
                  {:type :workflow-job :username org :project repo
                   :build-num build-num
                   :vcs_type vcs_type}]
                 [{:type :dashboard}
                  {:type :org :username org :vcs_type vcs_type}
                  {:type :project :username org :project repo :vcs_type vcs_type}
                  {:type :project-branch :username org :project repo :vcs_type vcs_type}
                  {:type :build :username org :project repo
                   :build-num build-num
                   :vcs_type vcs_type}])]
    (assoc-in state state/crumbs-path crumbs)))

(defmethod navigated-to :build
  [history-imp navigation-point {:keys [project-name build-num tab container-id action-id] :as args}
   state]
  (mlog "navigated-to :build with args " args)
  (if (and (= :build (state/current-view state))
           (not (state-utils/stale-current-build? state project-name build-num)))
    ;; page didn't change, just switched tabs
    (-> state
        (assoc-in state/navigation-tab-path tab)
        (assoc-in state/current-container-path container-id)
        (assoc-in state/current-action-id-path action-id))
    ;; navigated to page, load everything
    (-> state
        state-utils/clear-page-state
        (assoc state/current-view navigation-point
               state/navigation-data (assoc args :show-settings-link? false)
               :project-settings-project-name project-name)
        (add-crumbs args)
        state-utils/reset-current-build
        (#(if (state-utils/stale-current-project? % project-name)
            (state-utils/reset-current-project %)
            %))
        state-utils/reset-dismissed-osx-usage-level)))

(defn initialize-pusher-subscriptions
  "Subscribe to pusher channels for initial messaging. This subscribes
  us to build messages (`update`, `add-messages` and `test-results`),
  and container messages for container 0 (`new-action`,`update-action`
  and `append-action`). The first two subscriptions[1] remain for the
  whole build. The second channel will be unsubscribed when other
  containers come into view.

  [1] We currently subscribe to both the old single-channel-per-build
  pusher channel, and the new \"@all\" style channel. This should be
  removed as soon it has been rolled in all environments including
  enterprise sites."
  [state comms parts]
  (let [ws-ch (:ws comms)
        parts (assoc parts :container-index 0)
        subscribe (fn [channel messages]
                    (put! ws-ch [:subscribe {:channel-name channel :messages messages}]))]
    (subscribe (pusher/build-all-channel parts) pusher/build-messages)
    (subscribe (pusher/build-container-channel parts) pusher/container-messages)
    (subscribe (pusher/obsolete-build-channel parts) (concat pusher/build-messages
                                                             pusher/container-messages))))

(defmethod post-navigated-to! :build
  [history-imp navigation-point {:keys [project-name build-num vcs_type] :as args} previous-state current-state comms]
  (let [api-ch (:api comms)
        projects-loaded? (seq (get-in current-state state/projects-path))
        current-user (get-in current-state state/user-path)
        build-url (gstring/format "/api/v1.1/project/%s/%s/%s" vcs_type project-name build-num)
        container-id (state/current-container-id current-state)
        container (get-in current-state (state/container-path container-id))
        last-action (-> container :actions last)
        build (get-in current-state state/build-path)
        vcs-url (:vcs_url build)
        previous-container-id (state/current-container-id previous-state)
        current-tab (or (get-in current-state state/navigation-tab-path)
                        (build-utils/default-tab build (get-in current-state state/project-scopes-path)))]
    (mlog (str "post-navigated-to! :build current-user? " (not (empty? current-user))
               " projects-loaded? " (not (empty? projects-loaded?))))
    (when (and (not projects-loaded?)
               (not (empty? current-user)))
      (api/get-projects api-ch))
    (ajax/ajax :get build-url :build-fetch
               api-ch
               :context {:project-name project-name :build-num build-num})
    (let [[username project] (str/split project-name #"/")]
      (initialize-pusher-subscriptions current-state comms
                                       {:username username
                                        :project project
                                        :build-num build-num
                                        :vcs-type vcs_type}))
    (api/get-action-steps {:vcs-url vcs-url
                           :build-num build-num
                           :project-name (vcs-url/project-name vcs-url)
                           :old-container-id previous-container-id
                           :new-container-id container-id}
                          (:api comms)))
  (set-page-title! (str project-name " #" build-num)))

(defmethod navigated-to :add-projects
  [history-imp navigation-point args state]
  (let [current-user (get-in state state/user-path)
        nav-org (not-empty (select-keys args [:login :vcs_type]))]
    (-> state
        state-utils/clear-page-state
        (assoc-in state/add-projects-selected-org-path (org/get-from-map args))
        (assoc state/current-view navigation-point
               state/navigation-data args)
        ;; force a reload of repos.
        (assoc-in state/repos-path [])
        (assoc-in state/github-repos-loading-path (user/github-authorized? current-user))
        (assoc-in state/bitbucket-repos-loading-path (user/bitbucket-authorized? current-user))
        (assoc-in state/crumbs-path [{:type :add-projects}])
        (assoc-in [:settings :add-projects :repo-filter-string] "")
        (state-utils/reset-current-org))))

(defn load-repos
  [current-state api-ch]
  (let [load-gh-repos? (get-in current-state state/github-repos-loading-path)
        load-bb-repos? (get-in current-state state/bitbucket-repos-loading-path)]
    (when load-gh-repos?
      (api/get-github-repos api-ch))
    (when load-bb-repos?
      (api/get-bitbucket-repos api-ch))))

(defmethod post-navigated-to! :add-projects
  [history-imp navigation-point _ previous-state current-state comms]
  (let [api-ch (:api comms)]
    ;; TODO: after top-bar-ui-v-1 launched, only load repos we need and do not
    ;; load orgs
    ;; load orgs, collaborators, and repos
    (api/get-orgs api-ch :include-user? true)
    (load-repos current-state api-ch)
    (set-page-title! "Add Projects")))

(defmethod navigated-to :build-insights
  [history-imp navigation-point args state]
  (-> state
      (assoc state/current-view navigation-point
             state/navigation-data args)
      state-utils/clear-page-state
      (assoc-in state/crumbs-path [{:type :build-insights}])))

(defmethod post-navigated-to! :build-insights
  [history-imp navigation-point args previous-state current-state comms]
  (let [api-ch (:api comms)]
    (when (feature/enabled? "top-bar-ui-v-1")
      ;; since build-insights uses user centric and not org centric api calls,
      ;; we need to use the plan endpoint to check if the current user has access.
      ;; TODO: make org centric insights endpoints and use the status from those instead
      (api/get-org-plan (org/name args)
                        (org/vcs-type args)
                        api-ch
                        :org-check))
    (api/get-projects api-ch)
    (api/get-user-plans api-ch))
  (set-page-title! "Insights"))

(defmethod navigated-to :project-insights
  [history-imp navigation-point {:keys [org repo branch vcs_type] :as args} state]
  (-> state
      (assoc state/current-view navigation-point
             state/navigation-data args)
      state-utils/clear-page-state
      (assoc-in state/crumbs-path [{:type :build-insights}
                                   {:type :org
                                    :username org
                                    :vcs_type vcs_type}
                                   {:type :project
                                    :username org
                                    :project repo
                                    :vcs_type vcs_type}
                                   {:type :project-branch
                                    :username org
                                    :branch branch
                                    :project repo
                                    :vcs_type vcs_type}])))

(defmethod post-navigated-to! :project-insights
  [history-imp navigation-point {:keys [org repo vcs_type] :as args} previous-state current-state comms]
  (api/get-project-settings vcs_type org repo (:api comms))
  (set-page-title! "Insights"))

(defmethod navigated-to :team
  [history-imp navigation-point args state]
  (-> state
      state-utils/clear-page-state
      (assoc state/current-view navigation-point
             state/navigation-data args)
      (assoc-in state/crumbs-path [{:type :team}])))

(defmethod post-navigated-to! :team
  [history-imp navigation-point args previous-state current-state comms]
  (if (feature/enabled? "top-bar-ui-v-1")
    (let [selected-org (get-in current-state state/selected-org-path)]
      (api/get-org-settings-normalized (:login selected-org) (:vcs_type selected-org) (:api comms)))))

(defmethod post-navigated-to! :invite-teammates
  [history-imp navigation-point args previous-state current-state comms]
  (let [api-ch (:api comms)
        org (:org args)
        vcs_type (:vcs_type args)]
    ;; get the list of orgs
    (api/get-orgs api-ch :include-user? true)
    (when org
      (api/get-org-members org vcs_type api-ch))
    (set-page-title! "Invite teammates")))

(defmethod navigated-to :project-settings
  [history-imp navigation-point {:keys [project-name subpage org repo vcs_type] :as args} state]
  (-> state
      state-utils/clear-page-state
      (assoc state/current-view navigation-point
             state/navigation-data args
             :project-settings-project-name project-name)
      (assoc-in state/crumbs-path [{:type :settings-base}
                                   {:type :org
                                    :username org
                                    :vcs_type vcs_type}
                                   {:type :project
                                    :username org
                                    :project repo
                                    :vcs_type vcs_type}])
      (#(if (state-utils/stale-current-project? % project-name)
          (state-utils/reset-current-project %)
          %))))

(defmethod post-navigated-to! :project-settings
  [history-imp navigation-point {:keys [project-name vcs_type subpage]} previous-state current-state comms]
  (let [api-ch (:api comms)
        navigation-data (state/navigation-data current-state)
        vcs-type (:vcs_type navigation-data)
        org (:org navigation-data)
        repo (:repo navigation-data)]
    (when-not (seq (get-in current-state state/projects-path))
      (api/get-projects api-ch))
    (api/get-project-settings vcs-type org repo api-ch)

    (cond (and (or (= subpage :parallel-builds) (= subpage :build-environment))
               (not (get-in current-state state/project-plan-path)))
          (ajax/ajax :get
                     (api-path/project-plan vcs-type org repo)
                     :project-plan
                     api-ch
                     :context {:project-name project-name})

          (and (= subpage :checkout)
               (not (get-in current-state state/project-checkout-keys-path)))
          (ajax/ajax :get
                     (api-path/project-checkout-keys vcs-type project-name)
                     :project-checkout-key
                     api-ch
                     :context {:project-name project-name})

          (and (#{:api :badges} subpage)
               (not (get-in current-state state/project-tokens-path)))
          (ajax/ajax :get
                     (api-path/project-tokens vcs-type project-name)
                     :project-token
                     api-ch
                     :context {:project-name project-name})

          (and (= subpage :env-vars)
               (not (get-in current-state state/project-envvars-path)))
          (ajax/ajax :get
                     (gstring/format "/api/v1.1/project/%s/%s/envvar" vcs_type project-name)
                     :project-envvar
                     api-ch
                     :context {:project-name project-name})

          (= subpage :code-signing)
          (do
            (api/get-project-code-signing-keys project-name vcs_type api-ch)
            (api/get-project-provisioning-profiles project-name vcs_type api-ch))

          :else nil))

  (set-page-title! (str "Project settings - " project-name)))

(defmethod post-navigated-to! :landing
  [history-imp navigation-point _ previous-state current-state comms]
  (set-page-title! "Continuous Integration and Deployment")
  (set-page-description! "Free Hosted Continuous Integration and Deployment for web and mobile applications. Build better apps and ship code faster with CircleCI."))


(defmethod navigated-to :org-settings
  [history-imp navigation-point {:keys [subpage org vcs_type] :as args} state]
  (mlog "Navigated to subpage:" subpage)

  (-> state
      state-utils/clear-page-state
      (assoc-in state/current-view-path navigation-point)
      (assoc-in state/navigation-data-path args)
      (assoc-in state/org-settings-subpage-path subpage)
      (assoc-in state/org-settings-org-name-path org)
      (assoc-in state/org-settings-vcs-type-path vcs_type)
      (assoc-in state/crumbs-path [{:type :settings-base}
                                   {:type :org
                                    :username org}])
      (#(if (state-utils/stale-current-org? % org)
          (state-utils/reset-current-org %)
          %))))

(defmethod post-navigated-to! :org-settings
  [history-imp navigation-point {:keys [vcs_type org subpage]} previous-state current-state comms]
  (let [api-ch (:api comms)]
    (when-not (seq (get-in current-state state/projects-path))
      (api/get-projects api-ch))
    (if (get-in current-state state/org-plan-path)
      (mlog "plan details already loaded for" org)
      (api/get-org-plan org vcs_type api-ch :org-plan))
    (if (and (= org (get-in current-state state/org-name-path))
             (= vcs_type (get-in current-state state/org-vcs_type-path))
             (get-in current-state state/org-plan-path))
      (mlog "organization details already loaded for" org)
      (api/get-org-settings org vcs_type api-ch))
    (condp = subpage
      :organizations (api/get-orgs api-ch :include-user? true)
      :billing (do
                 (ajax/ajax :get
                            (gstring/format "/api/v1.1/organization/%s/%s/card"
                                            vcs_type
                                            org)
                            :plan-card
                            api-ch
                            :context {:org-name org
                                      :vcs-type vcs_type})
                 (ajax/ajax :get
                            (gstring/format "/api/v1.1/organization/%s/%s/invoices"
                                            vcs_type
                                            org)
                            :plan-invoices
                            api-ch
                            :context {:org-name org
                                      :vcs-type vcs_type}))
      nil))
  (set-page-title! (str "Org settings - " org)))

(defmethod navigated-to :logout
  [history-imp navigation-point _ state]
  (state-utils/clear-page-state state))

(defmethod post-navigated-to! :logout
  [history-imp navigation-point _ previous-state current-state comms]
  (go (let [api-result (<! (ajax/managed-ajax :post "/logout"))]
        (set! js/window.location "/"))))

(defmethod navigated-to :error
  [history-imp navigation-point {:keys [status] :as args} state]
  (let [orig-nav-point (get-in state [:navigation-point])]
    (mlog "navigated-to :error with (state/current-view state) of " orig-nav-point)
    (-> state
        state-utils/clear-page-state
        (assoc state/current-view navigation-point
               state/navigation-data args
               :original-navigation-point orig-nav-point))))

(defmethod post-navigated-to! :error
  [history-imp navigation-point {:keys [status] :as args} previous-state current-state comms]
  (set-page-title! (condp = status
                     401 "Login required"
                     404 "Page not found"
                     500 "Internal server error"
                     "Something unexpected happened")))

(defmethod navigated-to :admin-settings
  [history-imp navigation-point {:keys [subpage] :as args} state]
  (-> state
      state-utils/clear-page-state
      (assoc-in state/crumbs-path [{:type :admin}])
      (assoc state/current-view navigation-point
             state/navigation-data args
             :admin-settings-subpage subpage
             state/recent-builds nil)))

(defmethod post-navigated-to! :admin-settings
  [history-imp navigation-point {:keys [subpage tab]} previous-state current-state comms]
  (let [api-ch (:api comms)]
    (case subpage
      :fleet-state (do
                     (api/get-fleet-state api-ch)
                     (api/get-admin-dashboard-builds tab api-ch)
                     (set-page-title! "Fleet State"))
      :license (set-page-title! "License")
      :users (do
               (api/get-all-users api-ch)
               (set-page-title! "Users"))
      :projects (do
                  (api/get-all-projects api-ch)
                  (set-page-title! "Projects"))
      :system-settings (do
                         (api/get-all-system-settings api-ch)
                         (set-page-title! "System Settings")))))

(defmethod post-navigated-to! :setup-project
  [history-imp navigation-point _ previous-state current-state comms]
  (let [api-ch (:api comms)
        buildable-projects (get-in current-state state/setup-project-projects-path)]
    (when (nil? buildable-projects)
      (api/get-all-repos api-ch))))
