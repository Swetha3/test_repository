(ns frontend.components.add-projects
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [frontend.async :refer [navigate! raise!]]
            [frontend.components.forms :refer [managed-button]]
            [frontend.components.pages.user-settings.integrations :as integrations]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.modal :as modal]
            [frontend.components.pieces.org-picker :as org-picker]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.components.pieces.tabs :as tabs]
            [frontend.models.feature :as feature]
            [frontend.models.organization :as org]
            [frontend.models.plan :as pm]
            [frontend.models.repo :as repo-model]
            [frontend.models.user :as user-model]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [defrender html]]
            [frontend.utils.bitbucket :as bitbucket]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.legacy :refer [build-next]]
            [frontend.utils.vcs :as vcs-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :as gstring]
            [inflections.core :as inflections]
            [om.core :as om :include-macros true]))

(def view "add-projects")

(defn vcs-github? [item] (contains? #{"github" nil} (:vcs_type item)))
(defn vcs-bitbucket? [item] (= "bitbucket" (:vcs_type item)))

(defn missing-scopes-notice [current-scopes missing-scopes]
  [:div
   [:div.alert.alert-error
    "We don't have all of the GitHub OAuth scopes we need to run your tests. "
    [:a {:href (gh-utils/auth-url (concat missing-scopes current-scopes))}
     (gstring/format "Click to grant Circle %s: %s."
                     (inflections/pluralize (count missing-scopes) "missing scope")
                     (string/join " and " missing-scopes))]]])

(defn select-vcs-type [vcs-type item]
  (case vcs-type
    "bitbucket" (vcs-bitbucket? item)
    "github"    (vcs-github?    item)))

(defn loading-repos-for-vcs-type? [user vcs-type]
  (get-in user [:repos-loading (keyword vcs-type)]))

;; This is only the keys that we're interested in in this namespace. We'd give
;; this a broader scope if we could, but that's the trouble with legacy keys:
;; they vary from context to context. These are known to be correct here.
(def ^:private legacy-org-keys->modern-org-keys
  {:login :organization/name
   :vcs_type :organization/vcs-type
   :avatar_url :organization/avatar-url})

(defn- legacy-org->modern-org
  "Converts an org with legacy keys to the modern equivalent, suitable for
  our Om Next components."
  [org]
  (set/rename-keys org legacy-org-keys->modern-org-keys))

(defn- modern-org->legacy-org
  "Inverse of legacy-org->modern-org."
  [org]
  (set/rename-keys org (set/map-invert legacy-org-keys->modern-org-keys)))

(defn repos-explanation [user]
  [:div.add-repos
   [:ul
    [:li
     "Get started by selecting your GitHub "
     (when (user-model/bitbucket-authorized? user)
       "or Bitbucket ")
     "username or organization."]
    [:li "Choose a repo you want to test and we'll do the rest!"]]])

(defn- repo-title [repo link?]
  (let [repo-name (vcs-url/repo-name (:vcs_url repo))
        title (str repo-name (when (:fork repo) " (forked)"))]
    (html
      [:div.proj-name
       (if link?
         [:a {:title (str "View " title " project")
              :href (vcs-url/project-path (:vcs_url repo))}
          repo-name]
         [:span {:title title} repo-name])
       (when (repo-model/likely-osx-repo? repo)
         [:i.fa.fa-apple])
       (when (:fork repo)
         [:i.octicon.octicon-repo-forked])])))

(defn repo-item [data owner]
  (reify
    om/IDisplayName (display-name [_] "repo-item")
    om/IDidMount
    (did-mount [_]
      (utils/tooltip (str "#view-project-tooltip-" (-> data :repo repo-model/id (string/replace #"[^\w]" "")))))
    om/IRenderState
    (render-state [_ {:keys [building?]}]
      (let [repo (:repo data)
            settings (:settings data)
            login (get-in settings [:add-projects :selected-org :login])
            type (get-in settings [:add-projects :selected-org :type])
            repo-id (repo-model/id repo)
            repo-url (vcs-url/project-path (:vcs_url repo))
            tooltip-id (str "view-project-tooltip-" (string/replace repo-id #"[^\w]" ""))
            settings (:settings data)
            building-on-circle? (repo-model/building-on-circle? repo)
            repo-name (vcs-url/repo-name (:vcs_url repo))
            title-el (repo-title repo building-on-circle?)]
        (html
         (cond (repo-model/can-follow? repo)
               [:li.repo-follow
                title-el
                ; TODO: remove this after :onboarding-v1 is out of treament.
                (when building?
                  [:div.building "Starting first build..."])

                (if (and (feature/enabled? :onboarding-v1)
                         (not building-on-circle?))
                  (button/link
                    {:kind :primary
                     :href (if (feature/enabled? "top-bar-ui-v-1")
                             (routes/v1-setup-project-path {:org login :vcs_type type})
                             (routes/v1-setup-project))
                     :on-click #(raise! owner [:setup-project-select-project repo-id])}
                    "Build project")

                  (managed-button
                    [:button {:on-click #(do
                                           (raise! owner [:followed-repo (assoc repo
                                                                           :login login
                                                                           :type type)])
                                           ; TODO: rm not building-on-circle? content when :onboarding-v1
                                           ; it out of treatment.
                                           (when (not building-on-circle?)
                                             (om/set-state! owner :building? true)
                                             ((om/get-shared owner :track-event)
                                               {:event-type :build-project-clicked
                                                :properties {:project-vcs-url repo-url
                                                             :repo repo-name
                                                             :org login}})))
                              ; TODO: rm not building-on-circle? content when :onboarding-v1
                              :title (if building-on-circle?
                                       "This project is currently building on CircleCI. Clicking will cause builds for this project to show up for you in the UI."
                                       "This project is not building on CircleCI. Clicking will cause CircleCI to start building the project.")
                              :data-spinner true}
                     ; TODO: rm not building-on-circle? content when :onboarding-v1
                     (if building-on-circle? "Follow project" "Build project")]))]

               (:following repo)
               [:li.repo-unfollow
                title-el
                (managed-button
                 [:button {:on-click #(raise! owner [:unfollowed-repo (assoc repo
                                                                             :login login
                                                                             :type type)])
                           :data-spinner true}
                  [:span "Unfollow project"]])]

               (repo-model/requires-invite? repo)
               [:li.repo-nofollow
                title-el
                [:div.notice {:title "You must be an admin to add a project on CircleCI"}
                 [:i.material-icons.lock "lock"]
                 "Contact repo admin"]]))))))

(defrender repo-filter [settings owner]
  (let [repo-filter-string (get-in settings [:add-projects :repo-filter-string])]
    (html
     [:div.repo-filter
      [:input.unobtrusive-search
       {:placeholder "Filter projects..."
        :type "search"
        :value repo-filter-string
        :on-change #(utils/edit-input owner [:settings :add-projects :repo-filter-string] %)}]
      [:div.checkbox.pull-right.fork-filter
       [:label
        [:input {:type "checkbox"
                 :checked (-> settings :add-projects :show-forks)
                 :name "Show forks"
                 :on-change #(utils/toggle-input owner [:settings :add-projects :show-forks] %)}]
        "Show Forks"]]])))

(defn empty-repo-list [loading-repos? repo-filter-string selected-org-login has-private-scopes? owner]
  (if loading-repos?
    [:div.empty-placeholder (spinner)]
    [:div.add-repos
     (if repo-filter-string
       (str "No matching repos for organization " selected-org-login)
       (str "No repos found for organization " selected-org-login))
     [:br]
     (when-not has-private-scopes?
       (button/link {:href (gh-utils/auth-url)
                     :on-click #((om/get-shared owner :track-event) {:event-type :add-private-repos-clicked
                                                                     :properties {:component "empty-repo-list"}})
                     :kind :secondary}
                    "Add Private GitHub Projects"))]))

(defn select-plan-button [{{:keys [login vcs_type]} :selected-org} owner]
  (reify
    om/IRender
    (render [_]
      (button/link {:href (routes/v1-org-settings-path {:org login
                                                        :vcs_type vcs_type
                                                        :_fragment "osx-pricing"})
                    :on-click #((om/get-shared owner :track-event)
                                {:event-type :select-plan-clicked
                                 :properties {:org login
                                              :vcs-type vcs_type
                                              :plan-type pm/osx-plan-type}})
                    :kind :primary}
                   "Select Plan"))))

(defn free-trial-button [{{:keys [login vcs_type]} :selected-org} owner]
  (reify
    om/IRender
    (render [_]
      (button/managed-button
       {:on-click #(do
                    (raise! owner
                            [:activate-plan-trial {:plan-type :osx
                                                   :template pm/osx-trial-template
                                                   :org {:name login
                                                         :vcs_type vcs_type}}]))}
       "Start 2 Week Trial"))))

(defn no-plan-empty-state [{{:keys [login vcs_type] :as selected-org} :selected-org} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      ((om/get-shared owner :track-event) {:event-type :no-plan-banner-impression
                                           :properties {:org login
                                                        :vcs-type vcs_type
                                                        :plan-type pm/osx-plan-type}}))
    om/IRender
    (render [_]
      (html
       [:div.no-plan-empty-state
        [:i.fa.fa-apple.apple-logo]
        [:div.title
         [:span.bold login] " has no " [:span.bold "OS X plan"] " on CircleCI."]
        [:div.info
         "Select a plan to build your OS X projects now."]
        [:div.buttons
         (om/build select-plan-button {:selected-org selected-org})
         " "
         (om/build free-trial-button {:selected-org selected-org})]]))))

(defn unfollow-projects-button
  [{:keys [unfollowed-repos org-name]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:show-modal? false})
    om/IRenderState
    (render-state [_ {:keys [show-modal?]}]
      (html
        [:div
         [:button {:on-click #(om/set-state! owner :show-modal? true)}
          "Unfollow all projects"]
         (when show-modal?
           (let [close-fn #(om/set-state! owner :show-modal? false)]
             (modal/modal-dialog {:title "Are you sure?"
                                  :body (gstring/format "Are you sure you want to unfollow (%s) projects in the %s organization?"
                                                        (count unfollowed-repos)
                                                        org-name)
                                  :actions [(button/button {:on-click close-fn
                                                            :kind :flat}
                                                           "Cancel")
                                            (button/managed-button {:kind :primary
                                                                    :success-text "Removed"
                                                                    :loading-text "Removing..."
                                                                    :failed-text "Failed"
                                                                    :on-click #(do
                                                                                 (raise! owner [:unfollowed-repos unfollowed-repos])
                                                                                 (close-fn)
                                                                                 ((om/get-shared owner :track-event) {:event-type :unfollow-projects-clicked
                                                                                                                      :properties {:org-name org-name}}))}
                                                                   "Unfollow All")]
                                  :close-fn close-fn})))]))))

(defmulti repo-list (fn [{:keys [type]}] type))

(defmethod repo-list :linux [{:keys [user repos loading-repos? repo-filter-string selected-org selected-plan settings]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        (if (empty? repos)
          (empty-repo-list loading-repos? repo-filter-string (:login selected-org) (user-model/has-private-scopes? user) owner)
          [:ul.proj-list.list-unstyled
           (for [repo repos]
             (om/build repo-item {:repo repo :settings settings}))])))))

(defmethod repo-list :osx [{:keys [user repos loading-repos? repo-filter-string selected-org selected-plan settings]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        (if (empty? repos)
         (empty-repo-list loading-repos? repo-filter-string (:login selected-org) (user-model/has-private-scopes? user) owner)
         [:ul.proj-list.list-unstyled
          (if (and (:admin selected-org) (not (pm/osx? selected-plan)))
            (om/build no-plan-empty-state {:selected-org selected-org})
            (for [repo repos]
              (om/build repo-item {:repo repo :settings settings})))])))))

(defn repo-lists [{:keys [user repos selected-org selected-plan settings] :as data} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected-tab-name :linux})

    om/IRenderState
    (render-state [_ {:keys [selected-tab-name]}]
      (let [selected-org-login (org/name selected-org)
            loading-repos? (loading-repos-for-vcs-type? user (:vcs_type selected-org))
            repo-filter-string (get-in settings [:add-projects :repo-filter-string])
            show-forks (true? (get-in settings [:add-projects :show-forks]))]
        (html
         [:div.proj-wrapper
          (if-not selected-org-login
            (repos-explanation user)
            (list
             (om/build tabs/tab-row {:tabs [{:name :linux
                                             :icon (html [:i.fa.fa-linux.fa-lg])
                                             :label "Linux"}
                                            {:name :osx
                                             :icon (html [:i.fa.fa-apple.fa-lg])
                                             :label "OS X"}]
                                     :selected-tab-name selected-tab-name
                                     :on-tab-click #(om/set-state! owner [:selected-tab-name] %)})

             (let [;; we display a repo if it belongs to this org, matches the filter string,
                   ;; and matches the fork settings.
                   display? (fn [repo]
                              (and
                               (or show-forks (not (:fork repo)))
                               (select-vcs-type (or (:vcs_type selected-org)
                                                    "github") repo)
                               (= (:username repo) selected-org-login)
                               (gstring/caseInsensitiveContains (:name repo) repo-filter-string)))
                   filtered-repos (->> repos
                                       (filter display?)
                                       (sort-by :pushed_at)
                                       (reverse))
                   osx-repos (->> filtered-repos (filter repo-model/likely-osx-repo?))
                   linux-repos (->> filtered-repos (remove repo-model/likely-osx-repo?))]
               [:div
                [:div.filter-row
                 (om/build repo-filter settings)
                 [:div.unfollow-repos
                  (om/build unfollow-projects-button {:unfollowed-repos (->> (if (= :osx selected-tab-name)
                                                                               osx-repos
                                                                               linux-repos)
                                                                             (filter :following))
                                                      :org-name selected-org-login})]]
                [:div
                 (condp = selected-tab-name
                   :linux
                   (om/build repo-list {:repos (if (pm/osx? selected-plan) ; Allows mistaken OS X repos to still be built.
                                                 linux-repos
                                                 filtered-repos)
                                        :loading-repos? loading-repos?
                                        :repo-filter-string repo-filter-string
                                        :selected-org selected-org
                                        :selected-plan selected-plan
                                        :type selected-tab-name
                                        :settings settings})

                   :osx
                   (om/build repo-list {:repos osx-repos
                                        :loading-repos? loading-repos?
                                        :repo-filter-string repo-filter-string
                                        :selected-org selected-org
                                        :selected-plan selected-plan
                                        :type selected-tab-name
                                        :settings settings}))]])))])))))

(defn inaccessible-follows
  "Any repo (project) we follow where the org isn't in our set of orgs is either:
  an org we have been removed from, or an org that turned on 3rd party app
  restrictions and didn't enable CircleCI"
  [user-data followed]
  (let [org-set (->> user-data
                     :organizations
                     (filter :org)
                     (map :login)
                     set)
        org-set (conj org-set (:login user-data))]
    (filter #(not (contains? org-set (:username %))) followed)))

(defn inaccessible-repo-item [data owner]
  (reify
    om/IDisplayName (display-name [_] "repo-item")
    om/IRenderState
    (render-state [_ {:keys [building?]}]
      (let [repo (:repo data)
            settings (:settings data)
            login (get-in repo [:username])]
        (html
         [:li.repo-unfollow
          [:div.proj-name
           [:span {:title (str (:reponame repo) (when (:fork repo) " (forked)"))}
            (:reponame repo)]
           (when (:fork repo)
             [:span.forked (str " (" (vcs-url/org-name (:vcs_url repo)) ")")])]
          (managed-button
           [:button {:on-click #(raise! owner [:unfollowed-repo (assoc repo
                                                                  :login login
                                                                  :type type)])
                     :data-spinner true}
            [:span "Unfollow project"]])])))))

(defn inaccessible-org-item [data owner]
  (reify
    om/IDisplayName (display-name [_] "org-item")
    om/IRenderState
    (render-state [_ {:keys [building?]}]
      (let [repos (:repos data)
            settings (:settings data)
            org-name (:org-name data)
            visible? (get-in settings [:add-projects :inaccessible-orgs org-name :visible?])]
        (html
         [:div
          [:div.repo-filter
           [:div.orgname {:on-click #(raise! owner [:inaccessible-org-toggled {:org-name org-name :value (not visible?)}])}
            (if visible?
              [:i.fa.fa-chevron-up]
              [:i.fa.fa-chevron-down])
            [:span {:title org-name} org-name]]]
          (when visible?
            [:ul.proj-list.list-unstyled
             (map (fn [repo] (om/build inaccessible-repo-item {:repo repo :settings settings}))
                  repos)])])))))

(defn- inaccessible-orgs-notice-body
  [inaccessible-orgs follows-by-orgs settings]
  [:div.inaccessible-notice.card
   [:h2 "Warning: Access Problems"]
   [:p.missing-org-info
    "You are following projects owned by GitHub organizations to which you don't currently have access. If an admin for the org recently enabled the new GitHub Third Party Application Access Restrictions for these organizations, you may need to enable CircleCI access for the orgs at "
    [:a {:href (gh-utils/third-party-app-restrictions-url) :target "_blank"}
     "GitHub's application permissions"]
    "."]
   [:div.inaccessible-org-wrapper
    (for [follows-by-org (vals follows-by-orgs)]
      (let [org-name (->> follows-by-org first :username)]
        (when (contains? inaccessible-orgs org-name)
          (om/build inaccessible-org-item
            {:org-name org-name :repos follows-by-org :settings settings}))))]])

(defn inaccessible-orgs-notice [inaccessible-followed-projects settings selected-org-name]
  "Note: These functions historically do not take vcs-type into account
         This may become an issue in future as users have the same
         name organization under different vcs-providers"
  (let [inaccessible-orgs (set (map :username inaccessible-followed-projects))
        followed-projects-by-org (group-by :username inaccessible-followed-projects)]
    (if (feature/enabled? "top-bar-ui-v-1")
      (when (contains? inaccessible-orgs selected-org-name)
        (inaccessible-orgs-notice-body #{selected-org-name} followed-projects-by-org settings))
      (inaccessible-orgs-notice-body inaccessible-orgs followed-projects-by-org settings))))

(defn- org-picker [{:keys [orgs user selected-org tab]} owner]
  (reify
    om/IDisplayName (display-name [_] "Organization Listing")
    om/IRender
    (render [_]
      (let [github-authorized? (user-model/github-authorized? user)
            bitbucket-possible? (vcs-utils/bitbucket-possible?)
            bitbucket-authorized? (user-model/bitbucket-authorized? user)
            selected-vcs-type (cond
                                (not bitbucket-possible?) "github"
                                tab tab
                                github-authorized? "github"
                                :else "bitbucket")
            tab-content (html
                         [:div
                          (when (= "github" selected-vcs-type)
                            (when-not github-authorized?
                              [:div
                               [:p "GitHub is not connected to your account yet. To connect it, click the button below:"]
                               (button/link {:href (gh-utils/auth-url)
                                             :on-click #((om/get-shared owner :track-event) {:event-type :vcs-authorize-clicked
                                                                                             :properties {:vcs-type selected-vcs-type
                                                                                                          :component "org-picker"
                                                                                                          :cta-text "Authorize GitHub"}})
                                             :kind :primary}
                                            "Authorize GitHub")]))
                          (when (and (= "bitbucket" selected-vcs-type)
                                     (not bitbucket-authorized?))
                            [:div
                             [:p "Bitbucket is not connected to your account yet. To connect it, click the button below:"]
                             (button/link {:href (bitbucket/auth-url)
                                           :on-click #((om/get-shared owner :track-event) {:event-type :vcs-authorize-clicked
                                                                                           :properties {:vcs-type selected-vcs-type
                                                                                                        :component "org-picker"
                                                                                                        :cta-text "Authorize Bitbucket"}})
                                           :kind :primary}
                                          "Authorize Bitbucket")])
                          (build-next
                           org-picker/picker
                           {:orgs (->> orgs
                                       (filter (partial select-vcs-type selected-vcs-type))
                                       (map legacy-org->modern-org))
                            :selected-org (legacy-org->modern-org selected-org)
                            :on-org-click #(raise! owner [:selected-add-projects-org (modern-org->legacy-org %)])})
                          (when (get-in user [:repos-loading (keyword selected-vcs-type)])
                            [:div.empty-placeholder (spinner)])])]
        (if bitbucket-possible?
          (let [tabs [{:name "github"
                       :icon (html [:i.octicon.octicon-mark-github])
                       :label "GitHub"}
                      {:name "bitbucket"
                       :icon (html [:i.fa.fa-bitbucket])
                       :label "Bitbucket"}]]
            (card/tabbed
             {:tab-row (om/build tabs/tab-row {:tabs tabs
                                               :selected-tab-name selected-vcs-type
                                               :on-tab-click #(navigate! owner (routes/v1-add-projects-path {:_fragment %}))})}
             tab-content))
          (card/basic
           tab-content))))))

;; We display you, then all of your organizations, then all of the owners of
;; repos that aren't organizations and aren't you. We do it this way because the
;; organizations route is much faster than the repos route. We show them
;; in this order (rather than e.g. putting the whole thing into a set)
;; so that new ones don't jump up in the middle as they're loaded.
(defn orgs-from-repos [user repos]
  (let [user-org-keys (->> user
                           :organizations
                           (map (juxt :vcs_type :login))
                           set)
        user-org? (comp user-org-keys (juxt :vcs_type :login))]
    (concat (sort-by :org (:organizations user))
      (->> repos
           (map (fn [{:keys [owner vcs_type]}] (assoc owner :vcs_type vcs_type)))
           (remove user-org?)
           distinct))))

(defrender add-projects [data owner]
  (let [user (:current-user data)
        repos (:repos user)
        settings (:settings data)
        {{tab :tab} :navigation-data} data
        selected-org (get-in settings [:add-projects :selected-org])
        inaccessible-followed-projects (inaccessible-follows user
                                                             (get-in data state/projects-path))]
    (html
     [:div#add-projects
      (when (seq (user-model/missing-scopes user))
        (missing-scopes-notice (user-model/current-scopes user)
                               (user-model/missing-scopes user)))
      (when (and (seq inaccessible-followed-projects)
                 (not (loading-repos-for-vcs-type? user :github))
                 (not (loading-repos-for-vcs-type? user :bitbucket)))
        (inaccessible-orgs-notice inaccessible-followed-projects settings (-> data
                                                                              (get-in state/selected-org-path)
                                                                              org/name)))
      [:.text-card
       (card/titled
         {:title "CircleCI helps you ship better code, faster. Let's add some projects on CircleCI."}
         (html
           [:div
            [:p "To kick things off, you'll need to choose a project to build. We'll start a new build for you each time someone pushes a new commit."]
            [:p (when-not (user-model/has-private-scopes? user)
                  (button/link {:href (gh-utils/auth-url)
                                :on-click #((om/get-shared owner :track-event) {:event-type :add-private-repos-clicked
                                                                                :properties {:component "add-projects-header-card"}})
                                :kind :primary}
                    "Add private repos"))]]))]

      [:div.org-repo-container
       (when-not (feature/enabled? "top-bar-ui-v-1")
         [:div.app-aside.org-listing
          (let [orgs (orgs-from-repos user repos)]
            [:div
             [:div.overview
              [:span.big-number "1"]
              [:div.instruction "Choose an organization that you are a member of."]]
             (om/build org-picker {:orgs orgs
                                   :selected-org selected-org
                                   :user user
                                   :tab tab})])
          (when (user-model/github-authorized? user)
            (integrations/gh-permissions))])
       [:div#project-listing.project-listing
        (when-not (feature/enabled? "top-bar-ui-v-1")
          [:div.overview
           [:span.big-number "2"]
           [:div.instruction
            [:p "Choose a repo to add to CircleCI. We'll start a new build for you each time someone pushes a new commit."]
            [:p "You can also follow a project that's already been added to CircleCI. You'll see your followed projects in "
             [:a {:href (routes/v1-dashboard-path {})} "Builds"]
             " and "
             [:a {:href (routes/v1-insights)} "Insights"]
             "."]]])
        (om/build repo-lists {:user user
                              :repos repos
                              :selected-org selected-org
                              :selected-plan (get-in data state/org-plan-path)
                              :settings settings})]]])))
