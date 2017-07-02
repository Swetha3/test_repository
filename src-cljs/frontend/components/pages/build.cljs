(ns frontend.components.pages.build
  (:require [devcards.core :as dc :refer-macros [defcard-om]]
            [frontend.async :refer [raise!]]
            [frontend.components.build :as build-com]
            [frontend.components.jira-modal :as jira-modal]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.templates.main :as main-template]
            [frontend.experimental.no-test-intervention :as no-test-intervention]
            [frontend.experimental.open-pull-request :refer [open-pull-request-action]]
            [frontend.models.build :as build-model]
            [frontend.models.feature :as feature]
            [frontend.models.project :as project-model]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [component html]]
            [om.core :as om :include-macros true]))

(defn- ssh-available?
  "Show the SSH button unless it's disabled"
  [project]
  (not (project-model/feature-enabled? project :disable-ssh)))

(defn- rebuild-actions [{:keys [build project]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:rebuild-status "Rebuild"})

    om/IDidMount
    (did-mount [_]
      (utils/tooltip ".rebuild-container"))

    om/IWillReceiveProps
    (will-receive-props [_ {:keys [build]}]
      (when (build-model/running? build)
        (om/set-state! owner [:rebuild-status] "Rebuild")))

    om/IRenderState
    (render-state [_ {:keys [rebuild-status]}]
      (let [rebuild-args (merge (build-model/build-args build) {:component "rebuild-dropdown"})
            update-status!  #(om/set-state! owner [:rebuild-status] %)
            actions         {:rebuild
                             {:text  "Rebuild"
                              :title "Retry the same tests"
                              :action #(do (raise! owner [:retry-build-clicked (merge rebuild-args {:no-cache? false})])
                                           (update-status! "Rebuilding..."))}

                             :without_cache
                             {:text  "Rebuild without cache"
                              :title "Retry without cache"
                              :action #(do (raise! owner [:retry-build-clicked (merge rebuild-args {:no-cache? true})])
                                           (update-status! "Rebuilding..."))}

                             :with_ssh
                             {:text  "Rebuild with SSH"
                              :title "Retry with SSH in VM",
                              :action #(do (raise! owner [:ssh-build-clicked rebuild-args])
                                           (update-status! "Rebuilding..."))}}
            text-for    #(-> actions % :text)
            action-for  #(-> actions % :action)
            can-trigger-builds? (project-model/can-trigger-builds? project)]
        (html
         [:div.rebuild-container
          (when-not can-trigger-builds?
            {:data-original-title "You need write permissions to trigger builds."
             :data-placement "left"})
          [:button.rebuild
           {:on-click (action-for :rebuild)
            ;; using :disabled also disables tooltips when hovering over button
            :class (when-not can-trigger-builds? "disabled")}
           [:div.rebuild-icon (icon/rebuild)]
           rebuild-status]
          [:span.dropdown.rebuild
           [:i.fa.fa-chevron-down.dropdown-toggle {:data-toggle "dropdown"}]
           [:ul.dropdown-menu.pull-right
            [:li {:class (when-not can-trigger-builds? "disabled")}
             [:a {:on-click (action-for :without_cache)} (text-for :without_cache)]]
            [:li {:class (when-not (and can-trigger-builds? (ssh-available? project)) "disabled")}
             [:a {:on-click (action-for :with_ssh)} (text-for :with_ssh)]]]]])))))

(defn- header-actions
  [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:show-jira-modal? false
       :show-setup-docs-modal? false})

    om/IWillReceiveProps
    (will-receive-props [_ data]
      (let [build (get-in data state/build-path)
            show-setup-docs-modal? (no-test-intervention/show-setup-docs-modal? build)]
        (om/set-state! owner :show-setup-docs-modal? show-setup-docs-modal?)))

    om/IRenderState
    (render-state [_ {:keys [show-jira-modal? show-setup-docs-modal?]}]
      (let [build-data (dissoc (get-in data state/build-data-path) :container-data)
            build (get-in data state/build-path)
            build-id (build-model/id build)
            build-num (:build_num build)
            vcs-url (:vcs_url build)
            project (get-in data state/project-path)
            user (get-in data state/user-path)
            logged-in? (not (empty? user))
            jira-data (get-in data state/jira-data-path)
            can-trigger-builds? (project-model/can-trigger-builds? project)
            can-write-settings? (project-model/can-write-settings? project)
            track-event (fn [event-type]
                          ((om/get-shared owner :track-event)
                           {:event-type event-type
                            :properties {:project-vcs-url (:vcs_url project)
                                         :user (:login user)
                                         :component "header"}}))]
        (component
          (html
           [:div
            ;; Ensure we never have more than 1 modal showing
            (cond
              show-jira-modal?
              (om/build jira-modal/jira-modal {:project project
                                               :jira-data jira-data
                                               :close-fn #(om/set-state! owner :show-jira-modal? false)})
              (and show-setup-docs-modal?
                   (= :setup-docs-modal (no-test-intervention/ab-test-treatment)))
              (om/build no-test-intervention/setup-docs-modal
                        {:close-fn
                         #(om/set-state! owner :show-setup-docs-modal? false)}))
            ;; Cancel button
            (when (and (build-model/can-cancel? build) can-trigger-builds?)
              (button/managed-button
               {:loading-text "Canceling"
                :failed-text  "Couldn't Cancel"
                :success-text "Canceled"
                :kind :flat
                :size :small
                :on-click #(raise! owner [:cancel-build-clicked (build-model/build-args build)])}
               "Cancel Build"))
            ;; Rebuild button
            (om/build rebuild-actions {:build build :project project})
            ;; PR button
            (when (and (feature/enabled? :open-pull-request)
                       (not-empty build))
              (om/build open-pull-request-action {:build build}))
            ;; JIRA button
            (when (and jira-data can-write-settings?)
              (button/icon {:label "Add ticket to JIRA"
                            :bordered? true
                            :on-click #(om/set-state! owner :show-jira-modal? true)}
                           (icon/add-jira-issue)))
            ;; Settings button
            (when can-write-settings?
              (button/icon-link {:href (routes/v1-project-settings-path (:navigation-data data))
                                 :bordered? true
                                 :label "Project Settings"
                                 :on-click #((om/get-shared owner :track-event)
                                             {:event-type :project-settings-clicked
                                              :properties {:project-vcs-url (:vcs_url project)
                                                           :user (:login user)}})}
                                (icon/settings)))]))))))

(defn page [app owner]
  (reify
    om/IRender
    (render [_]
      (main-template/template
       {:app app
        :main-content (om/build build-com/build
                                {:app app
                                 :ssh-available? (ssh-available? (get-in app (get-in app state/project-path)))})
        :header-actions (om/build header-actions app)}))))

(dc/do
  ;; Stub out jQuery tooltip functionality. Not the best solution, but we need
  ;; to get rid of jQuery tooltips anyhow.
  (aset js/window "$" (constantly #js {:tooltip (constantly nil)}))

  ;; Stub out feature/enabled? to turn features on. Note that this is
  ;; global (to the devcards app), and therefore not sustainable. The next
  ;; namespace that needs to add feature flags like this will overwrite this
  ;; one. We'll need a better way to handle these.
  (set! feature/enabled? #{:jira-integration :open-pull-request})

  ;; Note: The following devcards use defcard-om so they can supply a
  ;; stub :track-event in :shared.

  (defcard-om header-actions
    header-actions
    {}
    {:shared {:track-event (constantly nil)}})

  (defcard-om header-actions-cancelable
    header-actions
    (-> {}
        ;; To be canceled, a build must have the lifecycle and status of "running"...
        (assoc-in state/build-path {:lifecycle "running"
                                    :status "running"})
        ;; ...*and* the user must have the "trigger-builds" scope on the project.
        (assoc-in (conj state/project-path :scopes) #{"trigger-builds"}))
    {:shared {:track-event (constantly nil)}})

  (defcard-om header-actions-with-jira
    header-actions
    (-> {}
        ;; To have the JIRA action, a build must have JIRA data...
        (assoc-in state/jira-data-path {:foo "bar"})
        ;; ...*and* the user must have the "write-settings" scope on the project.
        (assoc-in (conj state/project-path :scopes) #{"write-settings"}))
    {:shared {:track-event (constantly nil)}}))
