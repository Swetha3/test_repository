(ns frontend.components.build
  (:require [frontend.async :refer [raise!]]
            [frontend.components.build-steps :as build-steps]
            [frontend.components.common :as common]
            [frontend.components.invites :as invites]
            [frontend.components.pages.build.head :as build-head]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.components.project.common :as project-common]
            [frontend.config :as config]
            [frontend.datetime :as datetime]
            [frontend.experimental.no-test-intervention :as no-test-intervention]
            [frontend.models.build :as build-model]
            [frontend.models.container :as container-model]
            [frontend.models.plan :as plan-model]
            [frontend.models.project :as project-model]
            [frontend.routes :as routes]
            [frontend.scroll :as scroll]
            [frontend.state :as state]
            [frontend.timer :as timer]
            [frontend.utils :as utils :refer-macros [defrender html]]
            [frontend.utils.build :as build-utils]
            [frontend.utils.seq :refer [find-index]]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.dom :as dom]
            [goog.string :as gstring]
            [om.core :as om :include-macros true]))

(defn report-infrastructure-error [{:keys [messages failed infrastructure_fail fail_reason]} owner]
  (when (and (empty? messages)
             failed
             infrastructure_fail)
    [:div
     [:div.alert.alert-danger.iconified
      [:div [:img.alert-icon {:src (common/icon-path "Info-Error")}]]
      (cond
        (config/enterprise?)
        [:div
         "Looks like you may have encountered a bug in the build infrastructure. "
         "Your build should have been automatically retried.  If the problem persists, please "
         (common/contact-us-inner owner)
         ", so CircleCI can investigate."]

        (= fail_reason "exit-code-65-bug")
        [:div "This build failed as an \"Exit Code 65\" bug. "
         "You will not be charged for this build and it will be retried up to 3 times."
         "To follow progress regarding the root cause of this error, please follow "
         [:a {:href "https://discuss.circleci.com/t/xcode-exit-code-65/4284/9"
              :target "_blank"}
          "this Discuss post."]]

        :else
        [:div
         "Looks like we had a bug in our infrastructure, or that of our providers (generally "
         [:a {:href "https://status.github.com/"} "GitHub"]
         " or "
         [:a {:href "http://status.aws.amazon.com/"} "AWS"]
         "). We should have automatically retried this build. We've been alerted of"
         " the issue and are almost certainly looking into it, please "
         (common/contact-us-inner owner)
         " if you're interested in the cause or if the problem persists."])]]))

(defn sticky [{:keys [wrapper-class content-class content]} owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [stick]}]
      (let [wrapper-style (when stick
                            {:height (:height stick)})
            content-style (when stick
                            {:position :fixed
                             :top (:top stick)
                             :left (:left stick)
                             :width (:width stick)
                             :backgroundColor "#f5f5f5"})]
        (html [:div {:ref "wrapper" :class wrapper-class :style wrapper-style}
               [:div {:ref "content" :class content-class :style content-style}
                content]])))

    om/IDidMount
    (did-mount [_]
      (let [scroll-window (utils/sel1 ".main-body")]
        (scroll/register owner
                         #(let [scroll-rect (.getBoundingClientRect scroll-window)
                                wrapper (om/get-node owner "wrapper")
                                content (om/get-node owner "content")
                                wrapper-rect (.getBoundingClientRect wrapper)
                                content-height (.-height (.getBoundingClientRect content))
                                stick? (<= (.-top wrapper-rect) (.-top scroll-rect))]
                            (om/set-state! owner :stick
                                           (when stick?
                                             {:top (.-top scroll-rect)
                                              :left (.-left scroll-rect)
                                              :width (.-width scroll-rect)
                                              :height content-height})))
                         scroll-window)))

    om/IWillUnmount
    (will-unmount [_]
      (scroll/dispose owner))))

(defn notices [data owner]
  (reify
    om/IRender
    (render [_]
      (html
       (let [build-data (:build-data data)
             project-data (:project-data data)
             plan (:plan project-data)
             project (:project project-data)
             build (:build build-data)
             browser-settings (:browser-settings data)]
         [:div.notices
          (common/messages (set (:messages build)))
          [:div.row
           [:div.col-xs-12
            (when (and (no-test-intervention/show-intervention? build)
                       (= :setup-docs-banner (no-test-intervention/ab-test-treatment)))
              (om/build no-test-intervention/setup-docs-banner nil))

            (when-let [error-div (report-infrastructure-error build owner)]
              error-div)

            (when (project-common/show-trial-notice? project plan (:dismissed-trial-update-banner browser-settings))
              (om/build project-common/trial-notice project-data))

            (when (plan-model/suspended? plan)
              (om/build project-common/suspended-notice {:plan plan
                                                         :vcs_type (:vcs_type project)}))

            (when (and project (project-common/show-enable-notice project))
              (om/build project-common/enable-notice project))

            (when (build-model/display-build-invite build)
              (om/build invites/build-invites
                        (:invite-data data)
                        {:opts {:vcs_type (vcs-url/vcs-type (:vcs_url build))
                                :project-name (vcs-url/project-name (:vcs_url build))}}))]]])))))

(defn last-action-end-time
  [container]
  (-> (filter #(not (:filler-action %)) (:actions container)) last :end_time))

(defn action-duration-ms
  [{:keys [start_time end_time] :as action}]
  (if (and start_time end_time)
    (let [start (.getTime (js/Date. start_time))
          end (if end_time
                (.getTime (js/Date. end_time))
                (datetime/now))]
      (- end start))
    0))

;; TODO this seems a little bit slow when calculating durations for really complex builds with
;; lots of containers. Is there a good way to avoid recalculating this when selecting container pills? (Perhaps caching the calculated value using IWillUpdate / IShouldUpdate?)
(defn container-utilization-duration
  [actions]
  (->> actions
       (remove :background)
       (map action-duration-ms)
       (apply +)))

(defn container-duration-label [{:keys [actions]}]
  (reify
    om/IRender
    (render [_]
      (html
        [:span.lower-pill-section
         "("
         (datetime/as-duration (container-utilization-duration actions))
         ")"]))))

(defn compute-override-status
  "This is called when properties are updated in will-receive-props.

  This exists because the jump between :running and :waiting status is
  jarring and it can happen very quickly in a sequence of short build
  steps. To mitigate this, we are introducing a two second delay when
  transitioning from the :running state to the :waiting state.

  compute-override-status checks if we are transitioning to
  the :waiting status.  If we are, it generates 'override' data to put
  into component local state.  This data is used during rendering to
  decide which status to display."
  [current-status next-status]
  (if (and (= next-status :waiting)
           (not= current-status next-status))
    {:status current-status
     :until (+ (datetime/now)
               2000)}))

(defn maybe-override-status
  "If there is an override status and its time has not experied, use
  that status.  If the time has expried, use the real status."
  [real-status {:keys [until] :as override-status}]
  (if (and override-status
           (>= until (datetime/now)))
    (:status override-status)
    real-status))

(defn container-pill [{:keys [container status selected-container-id scopes current-tab build build-finished?]} owner]
  (reify
    om/IDisplayName
    (display-name [_] "Container Pill v2")
    om/IDidMount
    (did-mount [_]
      (timer/set-updating! owner (not (last-action-end-time container))))
    om/IDidUpdate
    (did-update [_ _ _]
      (timer/set-updating! owner (not (last-action-end-time container))))
    om/IWillReceiveProps
    (will-receive-props [this next-props]
      (let [next-status (:status next-props)]
        (om/set-state! owner :override-status (compute-override-status status next-status))))
    om/IRenderState
    (render-state [_ {:keys [override-status]}]
      (html
       (let [container-id (container-model/id container)
             status (maybe-override-status status override-status)
             status-class (case status
                            :failed :status-class/failed
                            :success :status-class/succeeded
                            :canceled :status-class/stopped
                            :running :status-class/running
                            :waiting :status-class/waiting
                            nil)
             {:keys [vcs_type username reponame build_num]} build]
         [:a.container-selector.exception
          {:href (routes/v1-build-path vcs_type
                                       username
                                       reponame
                                       nil
                                       build_num
                                       current-tab
                                       container-id)
           :on-click #(when (not= container-id selected-container-id)
                        ((om/get-shared owner :track-event) {:event-type :container-selected
                                                             :properties {:old-container-id selected-container-id
                                                                          :new-container-id container-id}}))
           :class (concat (container-model/status->classes status)
                          (when (= container-id selected-container-id) ["active"]))}
          [:span.upper-pill-section
           [:span.container-index (str (:index container))]
           [:span.status-icon {:class (name status-class)}
            (case status-class
              :status-class/failed (icon/status-failed)
              :status-class/stopped (icon/status-canceled)
              :status-class/succeeded (icon/status-passed)
              :status-class/running (icon/status-running)
              :status-class/waiting (icon/status-queued))]]
          (when build-finished?
            (om/build container-duration-label {:actions (:actions container)}))])))))

(def paging-width 10)

(defrender container-controls [{:keys [current-filter containers categorized-containers]} owner]
  (let [filters [{:filter :all
                  :containers containers
                  :label "All"}
                 {:filter :success
                  :containers (:success categorized-containers)
                  :label "Successful"}
                 {:filter :failed
                  :containers (:failed categorized-containers)
                  :label "Failed"}]]
    (html
     [:.controls
      [:.filtering
       [:span.filter-help "Show containers:"]
       (for [{:keys [filter containers label]} filters
             :let [c-name (name filter)
                   id (gstring/format "containers-filter-%s" c-name)
                   cnt (count containers)]]
         (list
          [:input {:id id
                   :type "radio"
                   :name "container-filter"
                   :checked (= current-filter filter)
                   :disabled (zero? cnt)
                   :on-change #(raise! owner [:container-filter-changed {:new-filter filter :containers containers}])
                   :on-click #((om/get-shared owner :track-event) {:event-type :container-filter-clicked
                                                                   :properties {:old-filter current-filter :new-filter filter}})}]
          [:label {:for id}
           (gstring/format "%s (%s)" label cnt)]))]])))

(defn- paging-offset-to-display-container
  "Given the id of a container and a vector of containers which will be
  displayed, calculate the paging offset which will cause that container to be
  part of the displayed page. If the container is not found in containers,
  returns nil."
  [container-id containers]
  ;; NB: :index here gets the container's id, or number, also called its "index"
  ;; in our domain. index-into-containers is the index within the given
  ;; containers vector of the container with the given id. Not the same
  ;; meaning of "index".
  (let [index-into-containers (find-index #(= container-id (:index %)) containers)]
    (when index-into-containers
      (* paging-width (js/Math.floor (/ index-into-containers paging-width))))))

(defn container-pills [{:keys [scopes container-data build-running? current-tab container-id build] :as data} owner]
  (reify
    om/IDisplayName
    (display-name [_]
      "Container Pills")
    om/IRender
    (render [_]
      (let [{:keys [containers current-filter paging-offset]} container-data
            categorized-containers (group-by #(container-model/status % build-running?) containers)

            filtered-containers (condp some [current-filter]
                                  #{:all} containers
                                  #{:success :failed} :>> categorized-containers)

            paging-offset (or paging-offset
                              ;; A nil paging-offset means "display whatever page the selected container is on".
                              (paging-offset-to-display-container container-id filtered-containers)
                              ;; If the selected container isn't in filtered-containers, show the first page.
                              0)

            container-count (count filtered-containers)
            previous-container-count (max 0 (- paging-offset 1))
            subsequent-container-count (min paging-width (- container-count (+ paging-offset paging-width)))
            project (get-in data [:project-data :project])
            {{plan-org-name :name, plan-vcs-type :vcs_type} :org, :as plan} (get-in data [:project-data :plan])
            show-upsell? (project-model/show-upsell? project plan)
            add-button-text (if show-upsell? "Add Containers +" "+")
            div (html
                 [:div.container-list {:class (when (and (> previous-container-count 0)
                                                         (> subsequent-container-count 0))
                                                "prev-and-next")}
                  (if (pos? previous-container-count)
                    [:a.container-selector.page-container-pills.exception
                     {:on-click #(raise! owner [:container-paging-offset-changed {:paging-offset (- paging-offset paging-width)}])}
                     [:div.nav-caret
                      [:i.fa.fa-2x.fa-angle-left]]
                     [:div.pill-details ;; just for flexbox container
                      [:div "Previous " paging-width]
                      [:div container-count " total"]]])
                  (for [container (subvec filtered-containers
                                          paging-offset
                                          (min container-count (+ paging-offset paging-width)))]
                    (om/build container-pill
                              {:container container
                               :build build
                               :build-running? build-running?
                               :build-finished? (build-model/finished? build)
                               :selected-container-id container-id
                               :current-tab current-tab
                               :scopes scopes
                               :status (container-model/status container build-running?)}
                              {:react-key (:index container)}))
                  (cond

                    (pos? subsequent-container-count)
                    [:a.container-selector.page-container-pills.exception
                     {:on-click #(raise! owner [:container-paging-offset-changed {:paging-offset (+ paging-offset paging-width)}])}
                     [:div.pill-details ;; just for flexbox container
                      [:div "Next " subsequent-container-count]
                      [:div container-count " total"]]
                     [:div.nav-caret
                      [:i.fa.fa-2x.fa-angle-right]]]

                    (project-model/parallel-available? project)
                    [:a.container-selector.add-containers.exception
                     {:href (routes/v1-org-settings-path {:org plan-org-name
                                                          :vcs_type plan-vcs-type
                                                          :_fragment "linux-pricing"})
                      :title "Adjust containers"
                      :class (when show-upsell? "upsell")
                      :on-click #((om/get-shared owner :track-event) {:event-type :add-more-containers-clicked
                                                                      :properties {:is-upsell-text show-upsell? :button-text add-button-text}})}
                     [:span add-button-text]])])]
        (html
         [:div.container-pills-container
          (when (and (project-model/parallel-available? project)
                     (build-model/finished? build))
            (om/build container-controls {:current-filter current-filter
                                          :containers containers
                                          :categorized-containers categorized-containers
                                          :build-running? build-running?}))
          (om/build sticky {:content div :content-class "containers"})])))))

(def css-trans-group (-> js/React
                         (aget "addons")
                         (aget "CSSTransitionGroup")
                         js/React.createFactory))

(defn transition-group
  [opts & components]
  (let [[group-name enter? leave? appear? class-name]
        (if (map? opts)
          [(:name opts)
           (:enter opts)
           (:leave opts)
           (:appear opts)
           (:class opts)]
          [opts true true false nil])]
    (apply
      css-trans-group
      #js {:transitionName group-name
           :transitionEnter enter?
           :transitionLeave leave?
           :transitionAppear appear?
           :component "div"
           :className class-name}
      components)))

(defn- maybe-scroll-to-action! [app owner]
  (when-let [action-id (om/get-state owner :action-id-to-scroll-to)]
    (when-let [action-node (dom/getElement (str "action-" action-id))]
      (utils/scroll-to-build-action! action-node)
      (raise! owner [:action-log-output-toggled
                     {:index (state/current-container-id app)
                      :step action-id
                      :value true}])
      (om/set-state! owner :action-id-to-scroll-to nil))))

(defn path-comp
  "Helper fn to get [build-num container-id action-id] from target-state"
  [target-state]
  (let [{:keys [build-num container-id action-id]} (get-in target-state state/navigation-data-path)]
    [build-num container-id action-id]))

(defn build-path [build-data tab container-id]
  (let [build (:build build-data)]
    (routes/v1-build-path
     (vcs-url/vcs-type (:vcs_url build))
     (:username build)
     (:reponame build)
     nil
     (:build_num build)
     tab
     container-id)))

(defn build [{:keys [app ssh-available?]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:action-transition-direction "steps-ltr"
       :action-id-to-scroll-to nil})
    om/IWillMount
    (will-mount [_]
      (when-let [action-id (get-in app state/current-action-id-path)]
        (om/set-state! owner :action-id-to-scroll-to action-id)))
    om/IDidMount
    (did-mount [_]
      (maybe-scroll-to-action! app owner))
    om/IWillReceiveProps
    (will-receive-props [this {next-app :app}]
      (let [{prev-app :app} (om/get-props owner)
            old-ix (state/current-container-id prev-app)
            new-ix (state/current-container-id next-app)]
        (om/set-state! owner
                       :action-transition-direction
                       (if (> old-ix new-ix)
                         "steps-ltr"
                         "steps-rtl"))
        (when-not (= (path-comp prev-app)
                     (path-comp next-app))
          (om/set-state! owner :action-id-to-scroll-to (get-in next-app state/current-action-id-path)))))
    om/IDidUpdate
    (did-update [_ _ _]
      (maybe-scroll-to-action! app owner))
    om/IRender
    (render [_]
      (let [build (get-in app state/build-path)
            build-data (get-in app state/build-data-path)
            container-data (get-in app state/container-data-path)
            invite-data (get-in app state/build-invite-data-path)
            project-data (get-in app state/project-data-path)
            user (get-in app state/user-path)
            current-tab (or (get-in app state/navigation-tab-path)
                            (build-utils/default-tab build (get-in app state/project-scopes-path)))]
        (html
         [:div.build-info-v2
          (if-not build
           [:div.empty-placeholder
             (om/build common/flashes (get-in app state/error-message-path))
             (spinner)]

            [:div
             (om/build build-head/build-head {:build-data (dissoc build-data :container-data)
                                              :current-tab current-tab
                                              :branch (->> (state/project-branch-crumb-path app)
                                                           (get-in app)
                                                           :branch)
                                              :project-data project-data
                                              :user user
                                              :projects (get-in app state/projects-path)
                                              :scopes (get-in app state/project-scopes-path)
                                              :ssh-available? ssh-available?
                                              :tab-href #(build-path build-data % (state/current-container-id app))})
             [:div.card.col-sm-12

              (om/build common/flashes (get-in app state/error-message-path))

              (om/build notices {:build-data (dissoc build-data :container-data)
                                 :project-data project-data
                                 :invite-data invite-data
                                 :browser-settings (get-in app state/browser-settings-path)})

              (om/build container-pills {:container-data container-data
                                         :container-id (state/current-container-id app)
                                         :current-tab current-tab
                                         :scopes (get-in app state/project-scopes-path)
                                         :build-running? (build-model/running? build)
                                         :build build
                                         :project-data project-data})

              (transition-group {:name (om/get-state owner :action-transition-direction)
                                 :enter true
                                 :leave true
                                 :class "build-steps-animator"}
                                (om/build build-steps/container-build-steps
                                          (assoc container-data
                                            :selected-container-id (state/current-container-id app))
                                          {:key :selected-container-id}))]])])))))
