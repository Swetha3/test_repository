(ns frontend.components.pages.team
  (:require [frontend.api :as api]
            [frontend.async :refer [raise!]]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.form :as form]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.pieces.modal :as modal]
            [frontend.components.pieces.org-picker :as org-picker]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.components.pieces.table :as table]
            [frontend.components.templates.main :as main-template]
            [frontend.models.feature :as feature]
            [frontend.models.organization :as org]
            [frontend.models.user :as user]
            [frontend.state :as state]
            [frontend.utils :as utils :refer [prettify-vcs_type valid-email?] :refer-macros [component element html]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.legacy :refer [build-next]]
            [goog.string :as gstr]
            [inflections.core :as inflections]
            [om.core :as om :include-macros true]))

(defn- non-code-empty-state []
  (om/build empty-state/full-page-empty-state
    {:name "Team"
     :icon (icon/team)
     :description "A list of all of the users that follow CircleCI projects and the number of projects they follow, so you can easily invite teammates to your project."
     :content
     [:.teams-empty-state-content
      [:img.placeholder-img {:src (utils/cdn-path "/img/empty-state-teams.png")}]]}))

(defn- add-follow-counts [users projects]
  (for [user users
        :let [followings
              (group-by :follower
                        (for [project projects
                              follower (:followers project)]
                          {:follower follower
                           :project project}))]]
    (assoc user ::follow-count (count (get followings user)))))

(defn- table [{:keys [users projects vcs-users] :as selected-org}]
  (let [authed-users-login (->> vcs-users
                                (filter :authorized?)
                                (map :login)
                                (into #{}))
        authed-and-following-users (filter #(contains? authed-users-login (:login %)) users)
        rows (cond-> authed-and-following-users
               projects (add-follow-counts projects))
        columns (cond-> [{:header "Login"
                          :cell-fn :login}]
                  projects (conj {:header "Projects Followed"
                                  :type #{:right :shrink}
                                  :cell-fn ::follow-count}))]
    (om/build table/table
              {:rows rows
               :key-fn :login
               :columns columns})))

(defn- no-org-selected [available-orgs bitbucket-enabled?]
  (component
    (card/basic
     (empty-state/empty-state {:icon (if-let [orgs (seq (take 3 available-orgs))]
                                       (empty-state/avatar-icons
                                        (for [org orgs]
                                          (gh-utils/make-avatar-url org :size 60)))
                                       (icon/team))
                               :heading (html
                                         [:span
                                          "Get started by selecting your "
                                          (empty-state/important "organization")])
                               :subheading (str
                                            "Select your GitHub "
                                            (when bitbucket-enabled? "or Bitbucket ")
                                            "organization (or username) to view your team.")}))))

(defn- organization-ident
  "Builds an Om Next-like ident for an organization."
  [org]
  ;; Om Next will not support composite keys like this. We'll need to make a
  ;; simple unique id available on the frontend for Om Next.
  [:organization/by-vcs-type-and-name
   [(:vcs_type org) (:login org)]])

;; functions for invite-teammates-modal component state manipulation

(defn- component-user-ident [{:keys [type login] :as user} & keys]
  [:org-members-by-type-and-login [type login]])

(defn- conj-to-user-ident [user & keys]
  (apply conj (component-user-ident user) keys))

(defn- select! [owner user]
  (om/set-state! owner (conj-to-user-ident user :selected?) true))

(defn- deselect! [owner user]
  (om/set-state! owner (conj-to-user-ident user :selected?) false))

(defn- selected? [owner user]
  (om/get-state owner (conj-to-user-ident user :selected?)))

(defn- set-entered-email! [owner user value]
  (om/set-state! owner (conj-to-user-ident user :entered-email) value))

(defn- get-entered-email [owner user]
  (om/get-state owner (conj-to-user-ident user :entered-email)))

(defn- get-user-from-state [owner vcs-user]
  (om/get-state owner (component-user-ident vcs-user)))

(defn- invitees [component-state vcs-users]
  (keep (fn [{:keys [login] :as vcs-user}]
          (let [user-ident (component-user-ident vcs-user)
                {:keys [selected?] :as component-user} (get-in component-state user-ident)]
            (when selected?
              (-> vcs-user
                  (assoc :email (:entered-email component-user))
                  (select-keys [:external_id :login :email :name])))))
        vcs-users))

(defn- invite-button-text [number-of-invites]
  (if (zero? number-of-invites)
    "Send Invites"
    (str "Send " (inflections/pluralize number-of-invites "Invite"))))

(defn invite-teammates-modal [{:keys [selected-org close-fn show-modal?]} owner]
  (reify
    om/IWillReceiveProps
    (will-receive-props [_ new-props]
      (let [new-selected-org (:selected-org new-props)
            new-vcs-users (:vcs-users new-selected-org)
            vcs-users (:vcs-users selected-org)
            new-show-modal? (:show-modal? new-props)]
        (when (and new-show-modal?
                   (not new-vcs-users))
          (api/get-org-members (:name new-selected-org) (:vcs_type new-selected-org) (om/get-shared owner [:comms :api])))
        (when (not= new-vcs-users vcs-users)
          (om/set-state! owner
                         (reduce (fn [new-state {:keys [user? email] :as user}]
                                   (if user?
                                     new-state
                                     (let [user-state (if-let [component-user (get-user-from-state owner user)]
                                                        component-user
                                                        (let [trimmed-email (some-> email gstr/trim)]
                                                          {:entered-email trimmed-email
                                                           :selected? (valid-email? trimmed-email)}))
                                           user-ident (component-user-ident user)]
                                       (assoc-in new-state user-ident user-state))))
                                 {}
                                 new-vcs-users)))))

    om/IRenderState
    (render-state [_ {:keys [org-members-by-type-and-login] :as state}]
      (component
       (html
        [:div
         (when show-modal?
           (let [{:keys [vcs-users]} selected-org
                 users (remove :user? vcs-users)
                 count-users (count users)
                 count-selected (count (filter (fn [[_ user]]
                                                 (:selected? user))
                                               org-members-by-type-and-login))
                 count-with-email (count (filter (fn [[_ user]]
                                                   (-> user :entered-email valid-email?))
                                                 org-members-by-type-and-login))]
             (modal/modal-dialog {:title "Invite Teammates"
                                  :body
                                  (element :body
                                           (html
                                            [:div
                                             (if-not (contains? selected-org :vcs-users)
                                               [:div.empty-placeholder (spinner)]
                                               (list
                                                [:.header
                                                 "These are the people who are not using CircleCI yet. "
                                                 [:span
                                                  [:b count-with-email]
                                                  " of "
                                                  [:b count-users]
                                                  " have valid email addresses."]]
                                                [:.members-table
                                                 (om/build table/table
                                                           {:rows users
                                                            :key-fn :login
                                                            :columns [{:header "Username"
                                                                       :cell-fn (fn [{:keys [login] :as user}]
                                                                                  (element :username
                                                                                           (html
                                                                                            [:div
                                                                                             [:img.invite-gravatar {:src (gh-utils/make-avatar-url user
                                                                                                                                                   :size 50)}]
                                                                                             (str "  " login)])))}
                                                                      {:header "Email"
                                                                       :cell-fn (fn [user]
                                                                                  (let [selected? (selected? owner user)
                                                                                        entered-email (get-entered-email owner user)]
                                                                                    (element :email-field
                                                                                      (html
                                                                                        [:div
                                                                                         (om/build form/text-field
                                                                                              {:on-change (fn [event]
                                                                                                            (let [trimmed-input (gstr/trim (.. event -currentTarget -value))
                                                                                                                  valid? (valid-email? trimmed-input)]
                                                                                                              (set-entered-email! owner user trimmed-input)
                                                                                                              (cond
                                                                                                                (and (not selected?)
                                                                                                                     valid?)
                                                                                                                (select! owner user)
                                                                                                                (and selected?
                                                                                                                     (not valid?))
                                                                                                                (deselect! owner user))))
                                                                                               :value entered-email
                                                                                               :size :medium
                                                                                               :validation-error (when (and (or selected?
                                                                                                                                (not (empty? entered-email)))
                                                                                                                            (not (valid-email? entered-email)))
                                                                                                                   (str entered-email " is not a valid email"))})]))))}
                                                                      {:type :shrink
                                                                       :cell-fn (fn [user]
                                                                                  (let [entered-email (get-entered-email owner user)
                                                                                        valid? (valid-email? entered-email)]
                                                                                    [:input {:type "checkbox"
                                                                                             :disabled (and (not valid?)
                                                                                                            (not (empty? entered-email)))
                                                                                             :checked (selected? owner user)
                                                                                             :on-click #(if-let [checked? (.. % -currentTarget -checked)]
                                                                                                          (when valid?
                                                                                                            (select! owner user))
                                                                                                          (deselect! owner user))}]))}]
                                                            :striped? true})]))]))
                                  :actions [(button/button {:on-click close-fn
                                                            :kind :flat}
                                                           "Cancel")
                                            (button/managed-button {:success-text "Sent"
                                                                    :on-click #(do
                                                                                 (raise! owner [:invited-team-members {:invitees (invitees state vcs-users)
                                                                                                                       :vcs_type (:vcs_type selected-org)
                                                                                                                       :org-name (:name selected-org)}])
                                                                                 (close-fn))
                                                                    :kind :primary
                                                                    :disabled? (zero? count-selected)}
                                                                   (invite-button-text count-selected))]
                                  :close-fn close-fn})))])))))
(defn- correct-org-selected?
  [org-in-component-state-ident org-in-app-state]
  (= org-in-component-state-ident
     (organization-ident org-in-app-state)))

(defn- main-content [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected-org-ident (if (feature/enabled? "top-bar-ui-v-1")
                             (organization-ident (get-in app state/selected-org-path))
                             nil)
       :show-invite-modal? nil})
    om/IWillMount
    (will-mount [_]
      (if (feature/enabled? "top-bar-ui-v-1")
        (let [selected-org (get-in app state/selected-org-path)]
          (api/get-org-members (org/name selected-org) (:vcs_type selected-org) (om/get-shared owner [:comms :api])))
        (api/get-orgs (om/get-shared owner [:comms :api]) :include-user? true)))
    ;; Emulate Om Next queries: Treat :selected-org-ident like a query param,
    ;; and when it changes, re-read the query. That is, in this case, fetch from
    ;; the API.
    om/IWillUpdate
    (will-update [_ _ {:keys [selected-org-ident]}]
      (let [[_ [vcs-type name]] selected-org-ident
            api-chan (om/get-shared owner [:comms :api])]
        (if (feature/enabled? "top-bar-ui-v-1")
          (when-not (correct-org-selected? selected-org-ident (get-in app state/selected-org-path))
            (let [org-in-app-state (get-in app state/selected-org-path)]
              (om/set-state! owner :selected-org-ident (organization-ident org-in-app-state))
              (api/get-org-members (org/name org-in-app-state) (:vcs_type org-in-app-state) api-chan)))
          (when (not= (:selected-org-ident (om/get-render-state owner))
                  selected-org-ident)
            (api/get-org-settings-normalized name vcs-type api-chan)
            (api/get-org-members name vcs-type api-chan)))))

    om/IRenderState
    (render-state [_ {:keys [selected-org-ident show-invite-modal?]}]
      (component
       (let [user (:current-user app)
             selected-org (when selected-org-ident (get-in app selected-org-ident))
             available-orgs (filter :org (:organizations user))]
         (html
          [:div
           (when-not (feature/enabled? "top-bar-ui-v-1")
             [:.sidebar
              (card/basic
                (if available-orgs
                  (build-next
                    org-picker/picker
                    {:orgs (map org/legacy-org->modern-org available-orgs)
                     :selected-org (org/legacy-org->modern-org selected-org)
                     :on-org-click (fn [modern-org]
                                     (let [{:keys [login vcs_type] :as org} (org/modern-org->legacy-org modern-org)]
                                       (om/set-state! owner :selected-org-ident (organization-ident org))
                                       ((om/get-shared owner :track-event) {:event-type :org-clicked
                                                                            :properties {:view :team
                                                                                         :login login
                                                                                         :vcs_type vcs_type}})))})
                  (spinner)))])
           [:.main
            (om/build invite-teammates-modal {:selected-org (select-keys selected-org [:name :vcs_type :vcs-users])
                                              :close-fn #(om/set-state! owner :show-invite-modal? false)
                                              :show-modal? show-invite-modal?})
            (if-let [[_ [vcs-type org-name]] selected-org-ident]
              (let [[vcs-icon vcs-label]
                    (case vcs-type
                      "github" [[:i.octicon.octicon-mark-github] "GitHub"]
                      "bitbucket" [[:i.fa.fa-bitbucket] "Bitbucket"]
                      nil)]
                (card/titled
                  {:title (html [:div org-name vcs-icon])
                   :action (element :action
                                    (html
                                      [:div
                                       [:span
                                        (button/button
                                          {:kind :secondary
                                           :size :small
                                           :on-click #(raise! owner [:org-settings-normalized {:org-name org-name :vcs-type vcs-type}])}
                                          (str "Resync with " vcs-label))]
                                       [:span
                                        (button/button
                                          {:kind :primary
                                           :size :small
                                           :on-click #(do
                                                       (om/set-state! owner :show-invite-modal? true)
                                                       ((om/get-shared owner :track-event)
                                                         {:event-type :invite-teammates-clicked
                                                          :properties {:view :team}}))}
                                          "Invite Teammates")]]))}
                  [:div
                   [:p
                    (gstr/format
                      "These are all the users that follow %s projects.
                      Some users might be following only open-source projects.
                      If you want to invite someone not listed here, please
                      make sure they have the right permissions on %s first."
                      org-name
                      (prettify-vcs_type vcs-type))]
                   (if (:vcs-users selected-org)
                     (table (select-keys selected-org [:users :projects :vcs-users]))
                     (spinner))]))
              (no-org-selected available-orgs (user/bitbucket-authorized? user)))]]))))))

(defn page [app owner]
  (reify
    om/IRender
    (render [_]
      (main-template/template
        {:app app
         :main-content (if (not (user/has-code-identity? (:current-user app)))
                         (non-code-empty-state)
                         (om/build main-content app))}))))
