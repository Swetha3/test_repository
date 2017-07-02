(ns frontend.components.nux-bootstrap
  (:require [clojure.string :as string]
            [frontend.async :refer [raise!]]
            [frontend.components.add-projects :refer [orgs-from-repos]]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.modal :as modal]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.models.feature :as feature]
            [frontend.models.project :as project-model]
            [frontend.models.repo :as repo-model]
            [frontend.models.user :as user-model]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [html]]
            [frontend.utils.github :as gh-utils]
            [goog.string :as gstring]
            [om.core :as om :include-macros true]
            [frontend.api :as api]))

(defn count-projects [{:keys [building? projects]}]
  (let [action (if building? filter remove)]
    (->> projects
         (action repo-model/building-on-circle?)
         count)))

(defn event-properties [cta-button-text projects]
  (let [selected-projects (filter :checked projects)]
    {:button-text cta-button-text
     :selected-building-projects-count (count-projects {:building? true :projects selected-projects})
     :selected-not-building-projects-count (count-projects {:building? false :projects selected-projects})
     :displayed-building-projects-count (count-projects {:building? true :projects projects})
     :displayed-not-building-projects-count (count-projects {:building? false :projects projects})
     :total-displayed-projects-count (count projects)
     :total-selected-projects-count (count selected-projects)}))

(defn follow-and-build-confirm-button
  [{:keys [cta-button-text selected-building-projects-count selected-not-building-projects-count projects-loaded? do-it-fn]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:show-modal? false})
    om/IRenderState
    (render-state [_ {:keys [show-modal?]}]
      (html
        [:span
         (button/button {:kind :primary
                         :disabled? (or (= (+ selected-not-building-projects-count selected-building-projects-count) 0)
                                        (not projects-loaded?))
                         :on-click #(om/set-state! owner :show-modal? true)}
                        cta-button-text)
         (when show-modal?
           (let [close-fn #(om/set-state! owner :show-modal? false)]
             (modal/modal-dialog {:title "Almost there!"
                                  :body [:div
                                         [:p (gstring/format "You're about to add (%s) new projects and to follow (%s) additional projects already building on CircleCI."
                                                             selected-not-building-projects-count
                                                             selected-building-projects-count)]
                                         [:p "We will add your SSH key to all of the new projects, and then build them for the first time."]]
                                  :actions [(button/button {:on-click close-fn
                                                            :kind :flat}
                                                           "Cancel")
                                            (button/managed-button {:kind :primary
                                                                    :loading-text "Following..."
                                                                    :failed-text "Failed"
                                                                    :success-text "Success!"
                                                                    :on-click #(do ((do-it-fn)
                                                                                    (close-fn)))}
                                                                   "Got it!")]
                                  :close-fn close-fn})))]))))

(defn project-list [{:keys [org projects projects-loaded?] :as data} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:expanded? true})

    om/IRenderState
    (render-state [_ {:keys [expanded?]}]
      (let [org-name (:login org)
            toggle-expand-fn #(om/set-state! owner :expanded? (not expanded?))
            projects-count (count projects)
            selected-projects-count (->> projects
                                        (filter :checked)
                                        count)
            all-selected? (= projects-count selected-projects-count)
            project-items
              (fn [projects]
                [:div.projects
                 {:class (str (when (< 7 (count projects)) "eight-projects")
                              " "
                              (when (< 11 (count projects)) "twelve-projects"))}
                 (->> projects
                      (sort-by #(-> % :name (string/lower-case)))
                      (map
                        (fn [project]
                          [:div.checkbox
                           [:label
                            [:input {:type "checkbox"
                                     :checked (:checked project)
                                     :name "follow checkbox"
                                     :on-click #(utils/toggle-input owner (conj state/repos-building-path
                                                                                (:vcs_url project)
                                                                                :checked)
                                                                    %)}]
                            (when (not (repo-model/building-on-circle? project))
                              [:span.new-badge])
                            (when (:fork project)
                              [:i.octicon.octicon-repo-forked])
                            (:name project)]])))])
            check-all-activity-repos
              (fn []
                (let [action-text (if all-selected? "Deselect" "Select")]
                  [:div [:a {:on-click #(do
                                          (raise! owner [:check-all-activity-repos {:org-name org-name :checked (not all-selected?)}])
                                          ((om/get-shared owner :track-event) {:event-type :checked-all-projects-clicked
                                                                               :properties (-> (event-properties action-text projects)
                                                                                               (assoc :org-name org-name))}))}
                         (str action-text " all projects")]]))]
        (html
          [:div.org-projects
           [:h2.maybe-border-bottom {:on-click toggle-expand-fn}
            [:i.fa.rotating-chevron {:class (when expanded? "expanded")}]
            [:img.avatar {:src (gh-utils/make-avatar-url {:avatar_url (:avatar_url org)} :size 25)}]
            org-name
            [:span.selected-explaination
             (if projects-loaded?
               (gstring/format "%s out of %s projects selected" selected-projects-count projects-count)
               "Counting projects...")]]
           (when expanded?
             (if projects-loaded?
               [:div.projects-container.maybe-border-bottom
                (check-all-activity-repos)
                (project-items projects)]
               [:div.projects-container.maybe-border-bottom
                (spinner)]))])))))

(defn nux-bootstrap-content [{:keys [projects current-user projects-loaded? on-success] :as data} owner]
  (let [processed-projects (->> projects
                                vals
                                (remove nil?))
        organizations (orgs-from-repos current-user processed-projects)
        project-orgs (group-by project-model/org-name processed-projects)
        cta-button-text (if (feature/enabled? :onboarding-v1)
                          "Follow"
                          "Follow and Build")
        {:keys [total-selected-projects-count selected-building-projects-count selected-not-building-projects-count]
         :as event-properties} (event-properties cta-button-text processed-projects)
        follow-and-build-action #(do
                                   (raise! owner [:followed-projects {:on-success on-success}])
                                   ((om/get-shared owner :track-event) {:event-type :follow-and-build-projects-clicked
                                                                        :properties event-properties}))]
    (reify
      om/IDidMount
      (did-mount [_]
        ((om/get-shared owner :track-event) {:event-type :nux-bootstrap-impression
                                             :properties event-properties}))
      om/IRender
      (render [_]
        (html
          (card/titled
           {:title "Getting Started"}
           (html
             (when-not (empty? organizations)
               [:div.getting-started
                [:div
                 "Choose projects to follow and populate your dashboard to see what builds pass/fail and show how fast they run."]
                (when-not (feature/enabled? :onboarding-v1)
                  [:div
                   "Projects that have never been built on CircleCI before have a "
                   [:span.new-badge]
                   "before the project name."])
                [:div.org-projects-container
                 (map (fn [org]
                        (om/build project-list {:org org
                                                :projects-loaded? projects-loaded?
                                                :projects (get project-orgs (:login org))}))
                      organizations)]
                (if (< total-selected-projects-count 25)
                  (button/managed-button {:kind :primary
                                          :loading-text "Following..."
                                          :failed-text "Failed"
                                          :success-text "Success!"
                                          :disabled? (or (= total-selected-projects-count 0)
                                                         (not projects-loaded?))
                                          :on-click follow-and-build-action}
                                         cta-button-text)
                  (om/build follow-and-build-confirm-button {:cta-button-text cta-button-text
                                                             :projects-loaded? projects-loaded?
                                                             :selected-building-projects-count selected-building-projects-count
                                                             :selected-not-building-projects-count selected-not-building-projects-count
                                                             :do-it-fn follow-and-build-action}))
                (when (and projects-loaded? (> selected-building-projects-count 0))
                  [:div.explanation (gstring/format "Follow (%s) projects currently building on CircleCI" selected-building-projects-count)])
                (when (and projects-loaded? (> selected-not-building-projects-count 0))
                  [:div.explanation (gstring/format "Add (%s) projects not yet building on CircleCI" selected-not-building-projects-count)])]))))))))
(defn- add-project-href
  [top-bar-nav-org]
  (if (feature/enabled? "top-bar-ui-v-1")
    (routes/v1-add-projects-path top-bar-nav-org)
    (routes/v1-add-projects)))

(defn- success-confirmation [{:keys [selected-org new-projects projects] :as data} owner]
  (reify
    om/IRender
    (render [_]
      (let [org-name (:login selected-org)
            vcs-type (:vcs_type selected-org)
            top-bar-nav-org {:org org-name :vcs_type vcs-type}
            count-projects (fn [projs filter-fn]
                             (->> projs
                                  vals
                                  filter-fn
                                  (remove nil?)
                                  count))]
        (card/titled
          {:title (gstring/format "Successfully Following %s Projects"
                                  (count-projects projects #(filter :checked %)))}
          (html
            [:div
             [:p "We noticed you have "
              [:b (count-projects new-projects identity)]
              " new projects on CircleCI. You can build these projects at any time from the "
              [:a {:href (add-project-href top-bar-nav-org)}
               "Add Projects"]
              " page."]
             [:p "New projects can be made using our old 1.0 architecture or a new and improved 2.0 architecture."]
             [:div
              (button/link
                {:href (if (feature/enabled? "top-bar-ui-v-1")
                         (routes/v1-organization-dashboard-path top-bar-nav-org)
                         (routes/v1-dashboard-path {}))
                 :on-click #(let [api-ch (om/get-shared owner [:comms :api])]
                              (api/get-dashboard-builds {} api-ch)
                              (api/get-projects api-ch)
                              (api/get-me api-ch))
                 :kind :primary}
                "View Builds")

              [:a.add-projects-button-link
               {:href (add-project-href top-bar-nav-org)
                :kind :secondary}
               "Setup New Projects"]]]))))))

(defn build-empty-state [{:keys [selected-org current-user projects-loaded? organizations new-projects projects] :as data} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:show-success? false})

    om/IDidMount
    (did-mount [_]
      (raise! owner [:nux-bootstrap]))
    om/IRenderState
    (render-state [_ {:keys [show-success?]}]
      (let [avatar-url (get-in data [:current-user :identities :github :avatar_url])]
        (html
          [:div.no-projects-block
           (if (and (feature/enabled? :onboarding-v1)
                    show-success?)
             (om/build success-confirmation {:selected-org selected-org
                                             :new-projects new-projects
                                             :projects projects})

             (card/collection
               [(card/basic
                  (empty-state/empty-state
                    {:icon (empty-state/avatar-icons
                             [(gh-utils/make-avatar-url {:avatar_url avatar-url} :size 60)])
                     :heading (html [:span (empty-state/important "Welcome to CircleCI!")])
                     :subheading (html
                                   [:div
                                    "You've joined the ranks of 100,000+ teams who ship better code, faster."
                                    [:br]
                                    (when-not (user-model/has-private-scopes? current-user)
                                      (button/link {:href (gh-utils/auth-url)
                                                    :on-click #((om/get-shared owner :track-event) {:event-type :add-private-repos-clicked
                                                                                                    :properties {:component "nux"}})
                                                    :kind :primary}
                                                   "Add private repos"))])}))

                (if organizations
                  (om/build nux-bootstrap-content (assoc data :on-success #(om/set-state! owner :show-success? true)))
                  (card/basic (spinner)))
                (if organizations
                  (card/titled
                    {:title "Looking for something else?"}
                    (html
                      [:div
                       [:div
                        "Project not listed? Visit the "
                        [:a {:href "/add-projects"} "Add Projects"]
                        " page to find it."]
                       [:div
                        "Interested in a tour? "
                        [:a {:href "https://circleci.com/gh/spotify/helios/5715?appcue=-KaIkbbdxnEVnAzMAkKx"
                             :on-click #((om/get-shared owner :track-event) {:event-type :view-demo-clicked})}
                         "See how Spotify uses CircleCI"]]])))]))])))))
