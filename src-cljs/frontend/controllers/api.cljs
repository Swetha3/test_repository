(ns frontend.controllers.api
  (:require [frontend.analytics.core :as analytics]
            [frontend.api :as api]
            [frontend.api.path :as api-path]
            [frontend.async :refer [put!]]
            [frontend.components.forms :as forms :refer [release-button!]]
            [frontend.elevio :as elevio]
            frontend.favicon
            [frontend.components.insights.project :as project-insights]
            [frontend.models.action :as action-model]
            [frontend.models.build :as build-model]
            [frontend.models.container :as container-model]
            [frontend.models.feature :as feature]
            [frontend.models.organization :as org]
            [frontend.models.project :as project-model]
            [frontend.models.repo :as repo-model]
            [frontend.models.test :as test-model]
            [frontend.models.user :as user-model]
            [frontend.notifications :as notifications]
            [frontend.pusher :as pusher]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :refer [merror mlog]]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.map :as map-utils]
            [frontend.utils.state :as state-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :as gstring]))

;; when a button is clicked, the post-controls will make the API call, and the
;; result will be pushed into the api-channel
;; the api controller will do assoc-in
;; the api-post-controller can do any other actions

;; --- API Multimethod Declarations ---

(defmulti api-event
  ;; target is the DOM node at the top level for the app
  ;; message is the dispatch method (1st arg in the channel vector)
  ;; args is the 2nd value in the channel vector)
  ;; state is current state of the app
  ;; return value is the new state
  (fn [target message status args state] [message status]))

(defmulti post-api-event!
  (fn [target message status args previous-state current-state comms] [message status]))

;; --- API Multimethod Implementations ---

(defmethod api-event :default
  [target message status args state]
  ;; subdispatching for state defaults
  (let [submethod (get-method api-event [:default status])]
    (if submethod
      (submethod target message status args state)
      (do (merror "Unknown api: " message args)
          state))))

(defmethod post-api-event! :default
  [target message status args previous-state current-state comms]
  ;; subdispatching for state defaults
  (let [submethod (get-method post-api-event! [:default status])]
    (if submethod
      (submethod target message status args previous-state current-state)
      (merror "Unknown api: " message status args))))

(defmethod api-event [:default :started]
  [target message status args state]
  (mlog "No api for" [message status])
  state)

(defmethod post-api-event! [:default :started]
  [target message status args previous-state current-state comms]
  (mlog "No post-api for: " [message status]))

(defmethod api-event [:default :success]
  [target message status args state]
  (mlog "No api for" [message status])
  state)

(defmethod post-api-event! [:default :success]
  [target message status args previous-state current-state comms]
  (mlog "No post-api for: " [message status]))

(defmethod api-event [:default :failed]
  [target message status args state]
  (mlog "No api for" [message status])
  state)

(defmethod post-api-event! [:default :failed]
  [target message status args previous-state current-state comms]
  (put! (:errors comms) [:api-error args])
  (mlog "No post-api for: " [message status]))

(defmethod api-event [:default :finished]
  [target message status args state]
  (mlog "No api for" [message status])
  state)

(defmethod post-api-event! [:default :finished]
  [target message status args previous-state current-state comms]
  (mlog "No post-api for: " [message status]))

(defmethod api-event [:projects :success]
  [target message status {:keys [resp]} {:keys [navigation-point] :as current-state}]
  (let [new-projects (map (fn [project] (update project :scopes #(set (map keyword %)))) resp)
        old-projects-lookup (into {} (for [old-p (get-in current-state state/projects-path)]
                                       [(-> old-p
                                            api/project-build-key
                                            (dissoc :branch))
                                        old-p]))
        processed-new-projects
        (for [new-project new-projects
              :let [matching-project (or (old-projects-lookup (-> new-project
                                                                  api/project-build-key
                                                                  (dissoc :branch)))
                                         {})]]
          (merge new-project
                 (select-keys matching-project [:recent-builds :build-timing])))]
    (assoc-in current-state state/projects-path processed-new-projects)))

(defmethod api-event [:projects :failed]
  [target message status args state]
  (mlog (str "PROJECT API FAILED: " (:status-text args)))
  state)

(defmethod post-api-event! [:projects :success]
  [target message status args previous-state {:keys [navigation-point] :as current-state} comms]
  (when (= navigation-point :build-insights)
    (let [projects (get-in current-state state/projects-path)
          build-keys (map api/project-build-key projects)
          api-ch (:api comms)]
      (api/get-build-insights-data build-keys api-ch))))

(defmethod api-event [:me :success]
  [target message status args state]
  (update-in state state/user-path merge (:resp args)))

(defmethod api-event [:recent-builds :success]
  [target message status args state]
  (if-not (and (= (get-in state [:navigation-data :org])
                  (get-in args [:context :org]))
               (= (get-in state [:navigation-data :repo])
                  (get-in args [:context :repo]))
               (= (get-in state [:navigation-data :branch])
                  (get-in args [:context :branch]))
               (= (get-in state [:navigation-data :query-params :page])
                  (get-in args [:context :query-params :page])))
    state
    (-> state
        (assoc-in state/recent-builds-path (:resp args))
        (assoc-in state/project-scopes-path (:scopes args))
        ;; Hack until we have organization scopes
        (assoc-in state/page-scopes-path (or (:scopes args) #{:read-settings})))))

(defmethod post-api-event! [:recent-builds :failure]
  [target message status args previous-state current-state comms]
  (put! (:nav comms) [:error args]))

(defmethod api-event [:insights-recent-builds :success]
  [target message status {page-of-recent-builds :resp, {target-key :project-id
                                                        page-result :page-result
                                                        all-page-results :all-page-results} :context} state]
  ;; Deliver the result to the result atom.
  (assert (nil? @page-result))
  (reset! page-result page-of-recent-builds)

  ;; If all page-results have been delivered, we're ready to update the state.
  (if-not (every? deref all-page-results)
    state
    (let [all-recent-builds (apply concat (map deref all-page-results))
          failed-builds (project-insights/failed-builds all-recent-builds)
          add-recent-build (fn [project]
                              (let [{:keys [branch]} target-key
                                    project-key (api/project-build-key project)]
                                (cond-> project
                                  (= (dissoc project-key :branch) (dissoc target-key :branch))
                                  (assoc-in (state/recent-builds-branch-path branch)
                                            all-recent-builds))))
          state (if (empty? failed-builds)
                  (assoc-in state state/failed-builds-tests-path [])
                  state)]
      (if (= (:navigation-point state) :project-insights)
        (update-in state state/project-path add-recent-build)
        (update-in state state/projects-path (partial map add-recent-build))))))

(defmethod post-api-event! [:insights-recent-builds :success]
  [target message status args previous-state current-state comms]
  (if (= (:navigation-point current-state) :project-insights)
    (let [branch (-> current-state :navigation-data :branch)
          project (get-in current-state state/project-path)
          failed-builds (project-insights/failed-builds (project-insights/branch-builds project branch))]
      (when (nil? (get-in current-state state/failed-builds-junit-enabled?-path))
        (doseq [failed-build failed-builds]
          (api/get-build-tests (merge failed-build {:vcs_type (project-model/vcs-type project)})
            (:api comms) :failed-build-tests))))))

(defmethod api-event [:branch-build-times :success]
  [target message status {timing-data :resp, {:keys [target-key]} :context} state]
  (let [add-timing-data (fn [project]
                          (let [{:keys [branch]} target-key
                                project-key (api/project-build-key project)]
                            (cond-> project
                              (= (dissoc project-key :branch) (dissoc target-key :branch))
                              (assoc-in [:build-timing branch] timing-data))))]
    (update-in state state/project-path add-timing-data)))

(defmethod api-event [:build-observables :success]
  [target message status {:keys [context resp]} state]
  (let [parts (:build-parts context)]
    (if (= parts (state-utils/build-parts (get-in state state/build-path)))
      (update-in state state/build-path merge resp)
      (if-let [index (state-utils/usage-queue-build-index-from-build-parts state parts)]
        (update-in state (state/usage-queue-build-path index) merge resp)
        state))))

(defmethod post-api-event! [:build-observables :success]
  [target message status args previous-state current-state comms]
  (let [build (get-in current-state state/build-path)
        previous-build (get-in previous-state state/build-path)]
    (frontend.favicon/set-color! (build-model/favicon-color build))
    (when (and (build-model/finished? build)
               (empty? (get-in current-state state/tests-path)))
      (when (and (not (build-model/finished? previous-build))
                 ;; TODO for V2 notifications we should consider reading from localstorage directly because
                 ;; storing it in state gets it out of sync with localstorage — or maybe this is reasonable
                 ;; behavior for our app?
                 (get-in current-state state/web-notifications-enabled-path))
        (notifications/notify-build-done build))
      (api/get-build-tests build (:api comms)))))

(defmethod post-api-event! [:build-fetch :success]
  [target message status args previous-state current-state comms]
  (put! (:api comms) [:build :success args]))

(defmethod post-api-event! [:build-fetch :failed]
  [target message status {:keys [status-code]} previous-state current-state comms]
  (put! (:nav comms) [:error {:status status-code :inner? false}]))

(defn reset-state-build
  [state build]
  (-> state
      (assoc-in state/build-path
                build)
      (assoc-in state/containers-path
                (vec (build-model/containers build)))))

(defn reset-state-scopes
  [state scopes]
  (-> state
      (assoc-in state/project-scopes-path scopes)
      (assoc-in state/page-scopes-path scopes)))

(defmethod api-event [:build :success]
  [target message status {build :resp :keys [scopes context]} state]
  (let [{:keys [build-num project-name]} context
        branch (some-> build :branch utils/encode-branch)
        tag (some-> build :vcs_tag utils/encode-branch)
        crumb-path (state/project-branch-crumb-path state)
        tag-crumb-path (conj crumb-path :tag)
        active-crumb-path (conj crumb-path :active)
        branch-crumb-path (conj crumb-path :branch)
        reset? (not= (build-model/id build)
                     (build-model/id (get-in state state/build-path)))]
    (if (or (not (and (= build-num (build-model/num build))
                      (= project-name (vcs-url/project-name (build-model/vcs-url build)))))
            (not reset?))
      state
      (cond-> state
        (and branch (not tag) (every? identity branch-crumb-path))
        (assoc-in branch-crumb-path branch)

        tag (assoc-in tag-crumb-path tag)
        tag (assoc-in active-crumb-path true)

        true (reset-state-scopes scopes)
        true (reset-state-build build)))))

(defn maybe-set-containers-filter!
  "Depending on the status and outcome of the build, set active
  container filter to failed."
  [state comms build-changed?]
  (let [build (get-in state state/build-path)
        containers (get-in state state/containers-path)
        build-running? (not (build-model/finished? build))
        failed-containers (filter #(= :failed (container-model/status % build-running?))
                                  containers)
        container-id (state/current-container-id state :allow-nils? true)
        failed-filter-valid? (or (not container-id)
                                 (some #(= container-id (container-model/id %)) failed-containers))
        controls-ch (:controls comms)]
    ;; set filter
    (when (and (not build-running?)
               (seq failed-containers)
               failed-filter-valid?
               build-changed?)
      (put! controls-ch [:container-filter-changed {:new-filter :failed
                                                    :containers failed-containers}]))))

(defn fetch-visible-output
  "Only fetch the output that is shown by default and is shown in this container."
  [current-state comms build-num vcs-url]
  (let [visible-actions-with-output (->> (get-in current-state state/containers-path)
                                         (mapcat :actions)
                                         (filter #(action-model/visible-with-output? % (state/current-container-id current-state)))
                                         ;; Some steps, like deployment, were run on conainer 0 but are on every container.
                                         ;; These steps will be returned every time, since they are duplicated of eachother, and therefore always are visible?
                                         ;; To keep from calling the api once for each duplicate, only call it on distinct actions.
                                         (distinct))]
    (doseq [action visible-actions-with-output]
      (api/get-action-output {:vcs-url vcs-url
                              :build-num build-num
                              :step (:step action)
                              :index (:index action)}
                             (:api comms)))))

(defmethod post-api-event! [:build :success]
  [target message status {build :resp :keys [scopes context] :as args} previous-state current-state comms]
  (let [api-ch (:api comms)
        {:keys [build-num project-name]} context
        {:keys [org repo vcs_type]} (state/navigation-data current-state)
        {:keys [read-settings]} scopes
        plan-url (gstring/format "/api/v1.1/project/%s/%s/plan" vcs_type project-name)
        build-changed? (not (= (-> previous-state :current-build-data) (-> current-state :current-build-data)))]
    ;; This is slightly different than the api-event because we don't want to have to
    ;; convert the build from steps to containers again.
    (analytics/track {:event-type :view-build
                      :current-state current-state
                      :build build})
    ;; Preemptively make the usage-queued API call if the build is in the
    ;; usage queue and the user has access to the info
    (when (and read-settings
               (build-model/in-usage-queue? build))
      (api/get-usage-queue build api-ch))
    (when (and (not (get-in current-state state/project-path))
               repo
               read-settings)
      (ajax/ajax :get
                 (api-path/project-info vcs_type org repo)
                 :project-settings
                 api-ch
                 :context {:project-name project-name
                           :vcs-type vcs_type}))
    (when (and (not (get-in current-state state/project-plan-path))
               repo
               read-settings)
      (ajax/ajax :get
                 plan-url
                 :project-plan
                 api-ch
                 :context {:project-name project-name
                           :vcs-type vcs_type}))
    ;; Attach information about this build so support knows the last build a user
    ;; was viewing before the sent in a support ticket.
    (elevio/add-user-traits! {:last-build-viewed (merge (select-keys build [:vcs_url :build_url :build_num :branch :platform])
                                                        {:repo-name (:reponame build)
                                                         :org-name (-> build :vcs_url vcs-url/org-name)
                                                         :scopes scopes})})
    (when (build-model/finished? build)
      (api/get-build-tests build api-ch))
    (when (and (= build-num (get-in args [:resp :build_num]))
               (= project-name (vcs-url/project-name (get-in args [:resp :vcs_url]))))
      (fetch-visible-output current-state comms build-num (get-in args [:resp :vcs_url]))
      (frontend.favicon/set-color! (build-model/favicon-color (get-in current-state state/build-path)))
      (maybe-set-containers-filter! current-state comms build-changed?))))

(defmethod api-event [:workflow-status :success]
  [target message status {:keys [resp]} state]
  state)

(defmethod api-event [:cancel-build :success]
  [target message status {:keys [context resp]} state]
  (let [build-id (:build-id context)]
    (if-not (= build-id (build-model/id (get-in state state/build-path)))
      state
      (update-in state state/build-path merge resp))))

(defn- setup-project-projects [resp current-val]
  (->> resp
       (remove repo-model/requires-invite?)
       (remove repo-model/building-on-circle?)
       (map-utils/coll-to-map :vcs_url)
       (merge current-val)))

(defmethod api-event [:github-repos :success]
  [target message status {:keys [resp]} state]
  (if (empty? resp)
    ;; this is the last api request, update the loading flag.
    (assoc-in state state/github-repos-loading-path false)
    ;; Add the items on this page of results to the state.
    (-> state
        (update-in state/repos-path #(into % resp))
        (update-in state/setup-project-projects-path #(setup-project-projects resp %)))))

(defmethod post-api-event! [:github-repos :success]
  [target message status args previous-state current-state comms]
  (when-not (empty? (:resp args))
    ;; fetch the next page
    (let [page (-> args :context :page)]
      (api/get-github-repos (:api comms) :page (inc page)))))

(defmethod api-event [:github-repos :failed]
  [target message status {:keys [status-code]} state]
  (cond-> state
    (= 401 status-code) (update-in state/user-path user-model/deauthorize-github)
    true (assoc-in state/github-repos-loading-path false)))

(defmethod api-event [:bitbucket-repos :success]
  [target message status args state]
  (-> state
      (assoc-in state/bitbucket-repos-loading-path nil)
      (update-in state/repos-path #(into % (:resp args)))))

(defmethod api-event [:bitbucket-repos :failed]
  [target message status {:keys [status-code]} state]
  (cond-> state
    (= 401 status-code) (update-in state/user-path user-model/deauthorize-bitbucket)
    true (assoc-in state/bitbucket-repos-loading-path false)))

(defn filter-piggieback [orgs]
  "Return subset of orgs that aren't covered by piggyback plans."
  (let [covered-orgs (into #{} (apply concat (map :piggieback_orgs orgs)))]
    (remove (comp covered-orgs :login) orgs)))

(defmethod api-event [:organizations :success]
  [target message status {orgs :resp} state]
  (-> state
      (assoc-in state/user-organizations-path orgs)
      (state-utils/update-selected-org)))

(defmethod post-api-event! [:organizations :success]
  [target message status args previous-state current-state comms]
  (when (feature/enabled? "top-bar-ui-v-1")
    (let [nav-point (get-in current-state state/current-view-path)]
      (when (not (get-in current-state state/selected-org-path))
        ;; If org not provided in the url, we need to capture a user's
        ;; default org before we can navigate to it.
        (put! (:nav comms) [:navigate! {:path (routes/org-centric-path {:current-org (state-utils/last-visited-or-default-org current-state)
                                                                        :nav-point nav-point})}])))))

(defmethod api-event [:tokens :success]
  [target message status args state]
  (print "Tokens received: " args)
  (assoc-in state state/user-tokens-path (:resp args)))

(defmethod api-event [:usage-queue :success]
  [target message status args state]
  (let [usage-queue-builds (:resp args)
        build-id (:context args)]
    (if-not (= build-id (build-model/id (get-in state state/build-path)))
      state
      (assoc-in state state/usage-queue-path usage-queue-builds))))

(defmethod post-api-event! [:usage-queue :success]
  [target message status args previous-state current-state comms]
  (let [usage-queue-builds (get-in current-state state/usage-queue-path)
        ws-ch (:ws comms)]
    (doseq [build usage-queue-builds
            :let [parts (state-utils/build-parts build)]]
      (put! ws-ch [:subscribe {:channel-name (pusher/build-all-channel parts)
                               :messages [:build/update]}])
      (put! ws-ch [:subscribe {:channel-name (pusher/obsolete-build-channel parts)
                               :messages [:build/update]}]))))


(defmethod api-event [:build-artifacts :success]
  [target message status args state]
  (let [artifacts (:resp args)
        build-id (:context args)]
    (if-not (= build-id (build-model/id (get-in state state/build-path)))
      state
      (assoc-in state state/artifacts-path artifacts))))

(defmethod api-event [:failed-build-tests :success]
  [target message status {{:keys [tests]} :resp :as args} state]
  (let [failed-builds-tests (get-in state state/failed-builds-tests-path [])
        failed-tests (filter test-model/failed? tests)
        junit-enabled? (not (empty? tests))]
    (-> state
        (assoc-in state/failed-builds-tests-path (concat failed-builds-tests failed-tests))
        (assoc-in state/failed-builds-junit-enabled?-path junit-enabled?))))

(defmethod api-event [:build-tests :success]
  [target message status {{:keys [tests exceptions]} :resp :as args} state]
  (let [build-id (:context args)]
    (cond-> state
      (= build-id (build-model/id (get-in state state/build-path)))
      (-> (update-in state/tests-path (fn [old-tests]
                                        ;; prevent om from thrashing while doing comparisons
                                        (if (> (count tests) (count old-tests))
                                          tests
                                          (vec old-tests))))
          (assoc-in state/tests-parse-errors-path exceptions)))))

(defn- same-build-page?
  "Helper function for the :action-steps events to tell if the
  result returned is from the same build page as the one the user
  is currently on."
  [{:keys [args state]}]
  (let [build (get-in state state/build-path)
        {:keys [build-num project-name new-container-id]} (:context args)]
    (and (= build-num (:build_num build))
         (= project-name (vcs-url/project-name (:vcs_url build)))
         (= new-container-id (get-in state state/current-container-path)))))

(defmethod api-event [:action-steps :success]
  [target message status args state]
  (let [build (get-in state state/build-path)]
    (if-not (same-build-page? {:args args
                               :state state})
      state
      (let [build (assoc build :steps (:resp args))]
        (-> state
            (assoc-in state/build-path build)
            (assoc-in state/containers-path (vec (build-model/containers build))))))))

(defn update-pusher-subscriptions
  [state comms old-index new-index]
  (let [ws-ch (:ws comms)
        build (get-in state state/build-path)]
    (put! ws-ch [:unsubscribe (-> build
                                  (state-utils/build-parts old-index)
                                  (pusher/build-container-channel))])
    (put! ws-ch [:subscribe {:channel-name (-> build
                                               (state-utils/build-parts new-index)
                                               (pusher/build-container-channel))
                             :messages pusher/container-messages}])))

(defmethod post-api-event! [:action-steps :success]
  [target message status args previous-state current-state comms]
  (let [build (get-in current-state state/build-path)
        vcs-url (:vcs_url build)
        build-num (:build_num build)
        old-container-id (get-in previous-state state/current-container-path)
        new-container-id (get-in current-state state/current-container-path)]
    (when (same-build-page? {:args args
                             :state current-state})
      (fetch-visible-output current-state comms build-num vcs-url)
      (update-pusher-subscriptions current-state comms old-container-id new-container-id)
      (frontend.favicon/set-color! (build-model/favicon-color build)))))

(defmethod api-event [:action-log :success]
  [target message status args state]
  (let [action-log (:resp args)
        {action-index :step container-index :index} (:context args)
        build (get-in state state/build-path)
        vcs-url (:vcs_url build)]
    (-> state
        (assoc-in (state/action-output-path container-index action-index) action-log)
        (update-in (state/action-path container-index action-index)
                   (fn [action]
                     (if (some :truncated action-log)
                       (assoc action :truncated-client-side? true)
                       action)))
        (assoc-in (conj (state/action-path container-index action-index) :user-facing-output-filename)
                  (gstring/format "build_%s_step_%s_container_%s.txt"
                                  (:build_num build)
                                  action-index
                                  container-index))
        (assoc-in (conj (state/action-path container-index action-index) :user-facing-output-url)
                  (api-path/action-output-file
                   (vcs-url/vcs-type vcs-url)
                   (vcs-url/project-name vcs-url)
                   (:build_num build)
                   action-index
                   container-index))
        (update-in (state/action-path container-index action-index) action-model/format-all-output))))

(def ^:private project-settings-flash-message
  {[:jira-added :success] "JIRA integration added successfully."
   [:jira-added :failed] "We encountered an error adding the JIRA integration."
   [:jira-removed :success] "JIRA integration removed successfully."
   [:jira-removed :failed] "We encountered an error removing the JIRA integration."})

(defn- add-project-settings-flash
  "Mechanism for showing flash notifications on `:project-settings`
  api response. Takes state, `flash-context` (supplied to api
  controller as part of context map), and `response-status` (api
  response status). Only shows a flash notification if there is one
  defined in `project-settings-flash-message`."
  [state flash-context response-status]
  (let [flash-message (project-settings-flash-message [flash-context response-status])]
    (cond-> state
      flash-message (state/add-flash-notification flash-message))))

(defmethod api-event [:project-settings :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-name context) (str (get-in state [:navigation-data :org]) "/" (get-in state [:navigation-data :repo])))
    state
    (-> state
        (update-in state/project-path merge resp)
        (add-project-settings-flash (:flash context) status))))

(defmethod post-api-event! [:project-settings :failed]
  [target message status args previous-state current-state comms]
  (put! (:nav comms) [:error {:status (:status-code args)}]))

;; TODO: add project settings API failed handler here. Also, make sure
;; to do this without interfering with the old school flash
;; notifications.

(defmethod post-api-event! [:project-settings :success]
  [target message status args previous-state {:keys [navigation-data navigation-point] :as state} comms]
  (when (= navigation-point :project-insights)
    (let [build-key (api/project-build-key navigation-data)
          api-ch (:api comms)]
      (api/get-project-insights-data build-key api-ch))))

(defmethod api-event [:project-plan :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-name context) (str (get-in state [:navigation-data :org]) "/" (get-in state [:navigation-data :repo])))
    state
    (assoc-in state state/project-plan-path resp)))

(defmethod api-event [:project-token :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-name context) (:project-settings-project-name state))
    state
    (assoc-in state state/project-tokens-path resp)))


(defmethod api-event [:project-checkout-key :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-name context) (:project-settings-project-name state))
    state
    (assoc-in state state/project-checkout-keys-path resp)))


(defmethod api-event [:project-envvar :success]
  [target message status {:keys [resp context]} state]
  (let [{:keys [project-name on-success]} context]
    (if-not (= project-name (:project-settings-project-name state))
      state
      (-> state
          (assoc-in state/project-envvars-path
                    (into {} (map (juxt :name :value)) resp))
          (cond->
            on-success (on-success state))))))


(defmethod api-event [:update-project-parallelism :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (get-in state state/project-path)))
    state
    (assoc-in state (conj state/project-data-path :parallelism-edited) true)))

(defn update-cache-clear-state
  [state {:keys [resp context]} k success?]
  (if-not (= (:project-id context)
             (project-model/id (get-in state state/project-path)))
    state
    (assoc-in state
              (conj state/project-data-path k)
              {:success? success?
               :message (:status resp
                                 (if success?
                                   "cache cleared"
                                   "cache not cleared"))})))

(defmethod api-event [:clear-build-cache :success]
  [target message status args state]
  (update-cache-clear-state state args :build-cache-clear true))

(defmethod api-event [:clear-build-cache :failed]
  [target message status args state]
  (update-cache-clear-state state args :build-cache-clear false))

(defmethod api-event [:clear-source-cache :success]
  [target message status args state]
  (update-cache-clear-state state args :source-cache-clear true))

(defmethod api-event [:clear-source-cache :failed]
  [target message status args state]
  (update-cache-clear-state state args :source-cache-clear false))

(defmethod post-api-event! [:clear-build-cache :success]
  [_ _ _ {:keys [context]} _ _]
  (release-button! (:uuid context) :success))

(defmethod post-api-event! [:clear-build-cache :failed]
  [_ _ _ {:keys [context]} _ _]
  (release-button! (:uuid context) :failed))

(defmethod post-api-event! [:clear-source-cache :success]
  [_ _ _ {:keys [context]} _ _]
  (release-button! (:uuid context) :success))

(defmethod post-api-event! [:clear-source-cache :failed]
  [_ _ _ {:keys [context]} _ _]
  (release-button! (:uuid context) :failed))


(defmethod api-event [:create-env-var :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (get-in state state/project-path)))
    state
    (-> state
        (update-in state/project-envvars-path (fnil conj {}) [(:name resp) (:value resp)])
        (assoc-in (conj state/inputs-path :new-env-var-name) "")
        (assoc-in (conj state/inputs-path :new-env-var-value) "")
        (state/add-flash-notification "Environment variable added successfully."))))

(defmethod post-api-event! [:create-env-var :success]
  [target message status {:keys [context]} previous-state current-state comms]
  ((:on-success context)))


(defmethod api-event [:import-env-vars :failed]
  [target message status {:keys [resp context]} state]
  (when (= (:dest-vcs-url context) (:vcs_url (get-in state state/project-path)))
    (state/add-flash-notification state "Sorry! You must have write permissions on both projects to import variables. Please contact an administrator at your organization to proceed.")))

(defmethod post-api-event! [:import-env-vars :success]
  [target message status {:keys [context]} previous-state current-state comms]
  (let [vcs-url (:dest-vcs-url context)
        vcs-type (vcs-url/vcs-type vcs-url)
        project-name (vcs-url/project-name vcs-url)]
    (ajax/ajax :get
               (gstring/format "/api/v1.1/project/%s/%s/envvar" vcs-type project-name)
               :project-envvar
               (:api comms)
               :context {:project-name project-name
                         :on-success #(state/add-flash-notification % "You’ve successfully imported your environment variables.")})
    ((:on-success context))))

(defmethod api-event [:delete-env-var :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (get-in state state/project-path)))
    state
    (-> state
        (update-in state/project-envvars-path dissoc (:env-var-name context))
        (state/add-flash-notification (gstring/format "Environment variable '%s' deleted successfully."
                                                      (:env-var-name context))))))


(defmethod api-event [:save-ssh-key :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (get-in state state/project-path)))
    state
    (-> state
        (assoc-in (conj state/project-data-path :new-ssh-key) {})
        (state/add-flash-notification "Your key has been successfully added."))))

(defmethod post-api-event! [:save-ssh-key :success]
  [target message status {:keys [context resp]} previous-state current-state comms]
  (when (= (:project-id context) (project-model/id (get-in current-state state/project-path)))
    ((:on-success context))
    (let [project-name (vcs-url/project-name (:project-id context))
          api-ch (:api comms)
          vcs-type (vcs-url/vcs-type (:project-id context))
          org-name (vcs-url/org-name (:project-id context))
          repo-name (vcs-url/repo-name (:project-id context))]
      (ajax/ajax :get
                 (api-path/project-settings vcs-type org-name repo-name)
                 :project-settings
                 api-ch
                 :context {:project-name project-name}))))


(defmethod api-event [:delete-ssh-key :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (get-in state state/project-path)))
    state
    (let [{:keys [hostname fingerprint]} context]
      (-> state
          (update-in (conj state/project-path :ssh_keys)
                     (fn [keys]
                       (remove #(and (= (:hostname %) hostname)
                                     (= (:fingerprint %) fingerprint))
                               keys)))
          (state/add-flash-notification "Your key has been successfully deleted.")))))


(defmethod api-event [:save-project-api-token :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (get-in state state/project-path)))
    state
    (-> state
        (assoc-in (conj state/project-data-path :new-api-token) {})
        (update-in state/project-tokens-path (fnil conj []) resp)
        (state/add-flash-notification "Your token has been successfully added."))))


(defmethod post-api-event! [:save-project-api-token :success]
  [target message status {:keys [context]} previous-state current-state comms]
  (forms/release-button! (:uuid context) status)
  ((:on-success context)))


(defmethod api-event [:delete-project-api-token :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (get-in state state/project-path)))
    state
    (-> state
        (update-in state/project-tokens-path
                   (fn [tokens]
                     (remove #(= (:token %) (:token context))
                             tokens)))
        (state/add-flash-notification "Your token has been successfully deleted."))))


(defmethod api-event [:set-heroku-deploy-user :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (get-in state state/project-path)))
    state
    (assoc-in state (conj state/project-path :heroku_deploy_user) (:login context))))


(defmethod api-event [:remove-heroku-deploy-user :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (get-in state state/project-path)))
    state
    (assoc-in state (conj state/project-path :heroku_deploy_user) nil)))


(defmethod api-event [:first-green-build-github-users :success]
  [target message status {:keys [resp context]} state]
  (if-not (and (= (:project-name context)
                  (vcs-url/project-name (:vcs_url (get-in state state/build-path))))
               (= (:vcs-type context)
                  (get-in state (conj state/build-path :vcs_type))))
    state
    (assoc-in state
              state/build-invite-members-path
              (vec (map-indexed (fn [i u] (assoc u :index i)) resp)))))

(defmethod api-event [:invite-team-members :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-name context) (vcs-url/project-name (:vcs_url (get-in state state/build-path))))
    state
    (assoc-in state state/dismiss-invite-form-path true)))


(defmethod api-event [:get-org-members :success]
  [target message status {:keys [resp context]} state]
  (let [{:keys [vcs-type org-name]} context]
    (assoc-in state
              (state/vcs-users-path vcs-type org-name)
              resp)))

(defmethod api-event [:get-org-members :failed]
  [target message status {:keys [resp context]} state]
  (let [{:keys [vcs-type org-name]} context]
    (assoc-in state (state/vcs-users-path vcs-type org-name) {})))

(defmethod api-event [:enable-project :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (get-in state state/project-path)))
    state
    (update-in state state/project-path merge (select-keys resp [:has_usable_key]))))


(defmethod api-event [:follow-project :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (get-in state state/project-path)))
    state
    (assoc-in state (conj state/project-path :following) true)))

(defmethod api-event [:unfollow-project :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-id context) (project-model/id (get-in state state/project-path)))
    state
    (assoc-in state (conj state/project-path :following) false)))

(defmethod api-event [:stop-building-project :success]
  [target message status {:keys [resp context]} state]
  (update-in state state/projects-path (fn [projects] (remove #(= (:project-id context) (project-model/id %)) projects))))

(defmethod post-api-event! [:stop-building-project :success]
  [target message status {{:keys [on-success]} :context} previous-state current-state comms]
  (put! (:nav comms) [:navigate! {:path (routes/v1-dashboard)}])
  (on-success))

(defn org-selectable?
  [state org-name vcs-type]
  (let [org-settings (get-in state state/org-settings-path)
        add-projects-selected-org (get-in state state/add-projects-selected-org-path)]
    (or (and (= org-name (:org-name org-settings))
             (= vcs-type (:vcs_type org-settings)))
        (and (= org-name (:login add-projects-selected-org))
             (= vcs-type (:vcs_type add-projects-selected-org))))))

(defmethod api-event [:org-plan :success]
  [target message status {:keys [resp context]} state]
  (let [{:keys [org-name vcs-type]} context]
    (if-not (org-selectable? state org-name vcs-type)
      state
      (let [{piggieback-orgs :piggieback_org_maps
             :as plan}
            resp]
        (-> state
            (assoc-in state/org-plan-path plan)
            (assoc-in state/org-admin?-path true)
            (assoc-in state/selected-piggieback-orgs-path (set piggieback-orgs)))))))

(defmethod api-event [:org-plan :failed]
  [target message status {:keys [resp context]} state]
  (assoc-in state state/org-admin?-path false))

(defmethod api-event [:org-check :success]
  [target message status {:keys [resp context]} state]
  (let [org (:org resp)]
    (if (org/same? (org/get-from-map context)
                   org)
      (-> state
          ;; Remove when top-bar-ui-v-1 applied
          ;; universally. Point all references to state/selected-org-path
          (assoc-in state/add-projects-selected-org-path org)
          (state-utils/change-selected-org org))
      state)))

(defmethod post-api-event! [:org-check :failed]
  [target message status args previous-state current-state comms]
  (put! (:nav comms) [:error {:status (:status-code args)}]))

(defmethod api-event [:org-settings :success]
  [target message status {:keys [resp context]} state]
  (let [{:keys [org-name vcs-type]} context
        org {:login org-name
             :vcs_type vcs-type}
        state (state-utils/change-selected-org state org)]
    (if-not (org-selectable? state org-name vcs-type)
      state
      (-> state
          (update-in state/org-data-path merge resp)
          (assoc-in state/org-loaded-path true)))))

(defmethod post-api-event! [:org-settings :failed]
  [target message status args previous-state current-state comms]
  (put! (:nav comms) [:error {:status (:status-code args)}]))

(defmethod api-event [:follow-repo :success]
  [target message status {:keys [resp context]} state]
  (if-let [repo-index (state-utils/find-repo-index (get-in state state/repos-path)
                                                   (:login context)
                                                   (:name context))]
    (assoc-in state (conj (state/repo-path repo-index) :following) true)
    state))

(defmethod post-api-event! [:follow-repo :success]
  [target message status args previous-state current-state comms]
  (api/get-projects (:api comms))
  (if-let [first-build (get-in args [:resp :first_build])]
    (let [nav-ch (:nav comms)
          build-path (-> first-build
                         :build_url
                         (goog.Uri.)
                         (.getPath)
                         (subs 1))]
      (put! nav-ch [:navigate! {:path build-path}]))
    (when (repo-model/should-do-first-follower-build? (:context args))
      (ajax/ajax :post
                 (gstring/format "/api/v1.1/project/%s/%s"
                                 (-> args :context :vcs_type)
                                 (vcs-url/project-name (:vcs_url (:context args))))
                 :start-build
                 (:api comms)))))

(defmethod api-event [:unfollow-repo :success]
  [target message status {:keys [resp context]} state]
  (if-let [repo-index (state-utils/find-repo-index (get-in state state/repos-path)
                                                   (:login context)
                                                   (:name context))]
    (assoc-in state (conj (state/repo-path repo-index) :following) false)
    state))

(defmethod post-api-event! [:unfollow-repo :success]
  [target message status args previous-state current-state comms]
  (api/get-projects (:api comms)))


(defmethod post-api-event! [:start-build :success]
  [target message status args previous-state current-state comms]
  (let [nav-ch (:nav comms)
        build-url (-> args :resp :build_url (goog.Uri.) (.getPath) (subs 1))]
    (put! nav-ch [:navigate! {:path build-url}])))

(defmethod api-event [:retry-build :success]
  [target message status {:keys [resp context]} state]
  (let [{:keys [reponame]} context]
    (state/add-flash-notification
     state
     (gstring/format "Rebuilding %s..." reponame))))

(defmethod post-api-event! [:retry-build :success]
  [target message status {:keys [resp context] :as args} previous-state current-state comms]
  (let [build-url (-> resp :build_url (goog.Uri.) (.getPath) (subs 1))
        {:keys [button-uuid no-cache? ssh?]} context]
    (release-button! button-uuid status)
    (put! (:nav comms) [:navigate! {:path build-url}])))

(defmethod post-api-event! [:retry-build :failed]
  [target message status {:keys [context]} previous-state current-state comms]
  (release-button! (:button-uuid context) :failed))


(defmethod post-api-event! [:save-dependencies-commands :success]
  [target message status {:keys [context resp]} previous-state current-state comms]
  (when (and (= (project-model/id (get-in current-state state/project-path))
                (:project-id context))
             (= :setup (-> current-state :navigation-data :subpage)))
    (let [nav-ch (:nav comms)
          org (vcs-url/org-name (:project-id context))
          repo (vcs-url/repo-name (:project-id context))
          vcs-type (vcs-url/vcs-type (:project-id context))]
      (put! nav-ch [:navigate! {:path (routes/v1-project-settings-path {:org org
                                                                        :repo repo
                                                                        :vcs_type vcs-type
                                                                        :_fragment "tests"})}]))))


(defmethod post-api-event! [:save-test-commands-and-build :success]
  [target message status {:keys [context resp]} previous-state current-state comms]
  (let [controls-ch (:controls comms)]
    (put! controls-ch [:started-edit-settings-build context])))


(defmethod api-event [:plan-card :success]
  [target message status {:keys [resp context]} state]
  (let [{:keys [org-name vcs-type]} context]
    (if-not (org-selectable? state org-name vcs-type)
      state
      (let [card (or resp {})] ; special case in case card gets deleted
        (assoc-in state state/stripe-card-path card)))))


(defmethod api-event [:create-plan :success]
  [target message status {:keys [resp context]} state]
  (let [{:keys [org-name vcs-type]} context]
    (if-not (org-selectable? state org-name vcs-type)
      state
      (assoc-in state state/org-plan-path resp))))

(defmethod post-api-event! [:create-plan :success]
  [target message status {:keys [resp context]} previous-state current-state comms]
  (let [{:keys [org-name vcs-type]} context]
    (when (org-selectable? current-state org-name vcs-type)
      (let [nav-ch (:nav comms)]
        (put! nav-ch [:navigate! {:path (routes/v1-org-settings-path {:org org-name
                                                                      :vcs_type vcs-type
                                                                      :_fragment (name (get-in current-state state/org-settings-subpage-path))})
                                  :replace-token? true}])))))

(defmethod api-event [:update-plan :success]
  [target message status {:keys [resp context]} state]
  (let [{:keys [org-name vcs-type]} context]
    (cond-> state
      (and (= org-name (get-in state state/project-plan-org-name-path))
           (= vcs-type (get-in state state/project-plan-vcs-type-path)))
      (assoc-in state/project-plan-path resp)

      (org-selectable? state org-name vcs-type)
      (update-in state/org-plan-path merge resp))))

(defmethod api-event [:update-heroku-key :success]
  [target message status {:keys [resp context]} state]
  (assoc-in state (conj state/user-path :heroku-api-key-input) ""))

(defmethod api-event [:create-api-token :success]
  [target message status {:keys [resp context]} state]
  (let [label (:label context)
        label (if (empty? label)
                label
                (str "'" label "'"))]
    (-> state
        (assoc-in state/new-user-token-path "")
        (update-in state/user-tokens-path conj resp)
        (state/add-flash-notification (gstring/format "API token %s added successfully."
                                                      label)))))

(defmethod post-api-event! [:create-api-token :success]
  [target message status {:keys [context]} previous-state current-state comms]
  ((:on-success context))
  (forms/release-button! (:uuid context) status))

(defmethod api-event [:delete-api-token :success]
  [target message status {:keys [resp context]} state]
  (let [deleted-token (:token context)
        label (:label deleted-token)
        label (if (empty? label)
                label
                (str "'" label "'"))]
    (-> state
        (update-in state/user-tokens-path (fn [tokens]
                                            (vec (remove #(= (:token %) (:token deleted-token)) tokens))))
        (state/add-flash-notification (gstring/format "API token %s deleted successfully."
                                                      label)))))

(defmethod api-event [:plan-invoices :success]
  [target message status {:keys [resp context]} state]
  (utils/mlog ":plan-invoices API event: " resp)
  (let [{:keys [org-name vcs-type]} context]
    (if-not (org-selectable? state org-name vcs-type)
      state
      (assoc-in state state/org-invoices-path resp))))

(defmethod api-event [:build-state :success]
  [target message status {:keys [resp]} state]
  (assoc-in state state/build-state-path resp))

(defmethod api-event [:fleet-state :success]
  [target message status {:keys [resp]} state]
  (assoc-in state state/fleet-state-path resp))

(defmethod api-event [:get-all-system-settings :success]
  [_ _ _ {:keys [resp]} state]
  (assoc-in state state/system-settings-path resp))

(defmethod api-event [:build-system-summary :success]
  [target message status {:keys [resp]} state]
  (assoc-in state state/build-system-summary-path resp))

(defmethod api-event [:license :success]
  [target message status {:keys [resp]} state]
  (assoc-in state state/license-path resp))

(defmethod api-event [:all-users :success]
  [_ _ _ {:keys [resp]} state]
  (assoc-in state state/all-users-path resp))

(defmethod api-event [:all-projects :success]
  [_ _ _ {:keys [resp]} state]
  (assoc-in state state/all-projects-path resp))

(defmethod api-event [:set-user-admin-state :success]
  [_ _ _ {user :resp} state]
  (assoc-in state
            state/all-users-path
            (map #(if (= (:login %) (:login user))
                    user
                    %)
                 (get-in state state/all-users-path))))

(defmethod api-event [:system-setting-set :success]
  [_ _ _ {updated-setting :resp} state]
  (update-in state
             state/system-settings-path
             (fn [settings]
               (mapv #(if (= (:name  %) (:name updated-setting))
                        updated-setting
                        %)
                     settings))))

(defmethod api-event [:system-setting-set :failed]
  [_ _ _ {{{:keys [error setting]} :message} :resp} state]
  (update-in state
             state/system-settings-path
             (fn [settings]
               (mapv #(if (= (:name %) (:name setting))
                        (assoc setting :error error)
                        %)
                     settings))))

(defmethod api-event [:user-plans :success]
  [target message status {:keys [resp]} state]
  (assoc-in state state/user-plans-path resp))

(defmethod api-event [:get-code-signing-keys :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-name context) (:project-settings-project-name state))
    state
    (assoc-in state state/project-osx-keys-path (:data resp))))

(defmethod api-event [:set-code-signing-keys :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-name context) (:project-settings-project-name state))
    state
    (-> state
        (assoc-in state/error-message-path nil)
        (state/add-flash-notification "Your key has been successfully added."))))

(defmethod post-api-event! [:set-code-signing-keys :success]
  [target message status {:keys [context]} previous-state current-state comms]
  (api/get-project-code-signing-keys (:project-name context) (:vcs-type context) (:api comms))
  ((:on-success context))
  (forms/release-button! (:uuid context) status))

(defmethod post-api-event! [:set-code-signing-keys :failed]
  [target message status {:keys [context] :as args} previous-state current-state comms]
  (put! (:errors comms) [:api-error args])
  (forms/release-button! (:uuid context) status))

(defmethod api-event [:delete-code-signing-key :success]
  [target message status {:keys [context]} state]
  (if-not (= (:project-name context) (:project-settings-project-name state))
    state
    (-> state
        (update-in state/project-osx-keys-path (partial remove #(and (:id %) ; figure out why we get nil id's
                                                                     (= (:id context) (:id %)))))
        (state/add-flash-notification "Your key has been successfully removed."))))

(defmethod post-api-event! [:delete-code-signing-keys :success]
  [target message status {:keys [context]} previous-state current-state comms]
  (forms/release-button! (:uuid context) status))

(defmethod post-api-event! [:delete-code-signing-keys :failed]
  [target message status {:keys [context] :as args} previous-state current-state comms]
  (put! (:errors comms) [:api-error args])
  (forms/release-button! (:uuid context) status))

(defmethod api-event [:get-provisioning-profiles :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-name context) (:project-settings-project-name state))
    state
    (assoc-in state state/project-osx-profiles-path (:data resp))))

(defmethod api-event [:set-provisioning-profiles :success]
  [target message status {:keys [resp context]} state]
  (if-not (= (:project-name context) (:project-settings-project-name state))
    state
    (-> state
        (assoc-in state/error-message-path nil)
        (state/add-flash-notification "Your provisioning profile has been successfully added."))))

(defmethod post-api-event! [:set-provisioning-profiles :success]
  [target message status {:keys [context]} previous-state current-state comms]
  (api/get-project-provisioning-profiles (:project-name context) (:vcs-type context) (:api comms))
  ((:on-success context))
  (forms/release-button! (:uuid context) status))

(defmethod post-api-event! [:set-provisioning-profiles :failed]
  [target message status {:keys [context] :as args} previous-state current-state comms]
  (put! (:errors comms) [:api-error args])
  (forms/release-button! (:uuid context) status))

(defmethod api-event [:delete-provisioning-profile :success]
  [target message status {:keys [context]} state]
  (if-not (= (:project-name context) (:project-settings-project-name state))
    state
    (-> state
        (assoc-in state/error-message-path nil)
        (state/add-flash-notification "Your provisioning profile has been successfully removed."))))

(defmethod post-api-event! [:delete-provisioning-profile :success]
  [target message status {:keys [context] :as args} previous-state current-state comms]
  (api/get-project-provisioning-profiles (:project-name context) (:vcs-type context) (:api comms))
  (forms/release-button! (:uuid context) status))

(defmethod post-api-event! [:delete-provisioning-profile :failed]
  [target message status {:keys [context] :as args} previous-state current-state comms]
  (put! (:errors comms) [:api-error args])
  (forms/release-button! (:uuid context) status))

(defmethod api-event [:org-settings-normalized :success]
  [_ _ _ {:keys [resp]} state]
  (let [{:keys [name vcs_type]} resp]
    (-> state
      (update-in state/org-data-path merge resp)
      (update-in [:organization/by-vcs-type-and-name [vcs_type name]] merge resp))))

(defmethod post-api-event! [:org-settings-normalized :failed]
  [target message status {:keys [context] :as args} previous-state current-state comms]
  ;; We currently use the org-settings endpoint to get a list of projects for that org
  ;; for the Import Variables feature on env-vars. However, you can have read and write
  ;; access to projects which belong to orgs you are not a member of (not sure how but
  ;; I did confirm that a thing).
  (when-not (= (get-in current-state state/current-view-path) :project-settings)
    (put! (:nav comms) [:error {:status (:status-code args)}])))

(defmethod api-event [:enterprise-site-status :success]
  [target message status {:keys [resp]} state]
  (assoc-in state [:enterprise :site-status] resp))

(defmethod api-event [:get-jira-projects :success]
  [target message status {:keys [resp]} state]
  (assoc-in state state/jira-projects-path resp))

(defmethod api-event [:get-jira-issue-types :success]
  [target message status {:keys [resp]} state]
  (assoc-in state state/jira-issue-types-path resp))

(defmethod post-api-event! [:create-jira-issue :success]
  [target message status {:keys [context]} previous-state current-state comms]
  (forms/release-button! (:uuid context) status)
  ((:on-success context))
  (analytics/track {:event-type :create-jira-issue-success
                    :current-state current-state}))

(defmethod post-api-event! [:create-jira-issue :failed]
  [target message status {:keys [context] :as args} previous-state current-state comms]
  (put! (:errors comms) [:api-error args])
  (forms/release-button! (:uuid context) status)
  (analytics/track {:event-type :create-jira-issue-failed
                    :current-state current-state
                    :properties {:status-code (:status-code context)
                                 :message (-> context :resp :message)}}))

(defmethod api-event [:all-repos :success]
  [target message status {:keys [resp context] :as args} state]
  (let [vcs (:vcs context)
        state (if (or (= :bitbucket vcs)
                      (empty? resp))
                ;; this is the last api request, update the loading flag.
                (assoc-in state (state/all-repos-loaded-path vcs) true)
                state)
        process-resp (fn [action current-val]
                       (->> resp
                            (remove repo-model/requires-invite?)
                            (#(if (feature/enabled? :onboarding-v1)
                               (filter repo-model/building-on-circle? %)
                               %))
                            (map #(assoc % :checked true))
                            (map-utils/coll-to-map :vcs_url)
                            (merge current-val)))]
    ;; Add the items on this page of results to the state.
    (-> state
        (update-in state/repos-building-path #(process-resp filter %))
        (update-in state/setup-project-projects-path #(setup-project-projects resp %)))))

(defmethod api-event [:all-repos :failed]
  [target message status {:keys [context]} state]
  (let [vcs (:vcs context)]
    (assoc-in state (state/all-repos-loaded-path vcs) true)))

(defmethod post-api-event! [:all-repos :success]
  [target message status {:keys [resp context]} previous-state current-state comms]
  (when-not (empty? resp)
    ;; fetch the next page
    (let [page (-> context :page inc)
          vcs (:vcs context)
          api-ch (:api comms)]
      (when (= vcs :github)
        (api/get-github-repos api-ch
                              :page page
                              :message :all-repos)))))

(defmethod post-api-event! [:follow-projects :success]
  [_ _ status {:keys [context]} previous-state current-state comms]
  (let [api-ch (:api comms)]
    (forms/release-button! (:uuid context) status)
    (if (feature/enabled? :onboarding-v1)
      ; To display a success confirmation note after successfully follow the projects.
      ((:on-success context))
      (do
        (api/get-dashboard-builds {} api-ch)
        (api/get-projects api-ch)
        (api/get-me api-ch)))))

(defmethod post-api-event! [:follow-projects :failed]
  [_ _ status {:keys [context]} previous-state current-state comms]
  (let [api-ch (:api comms)]
    (forms/release-button! (:uuid context) status)
    ;; Below is a hack because of the fact that with the current flow we have
    ;; no way of knowing whether or not a user is an admin on projects when we
    ;; show them the NUX Bootstrap experience on the dashboard page. So, it is possible
    ;; that we receive a 403 as one project is unable to be followed although others were
    ;; followed. So, do a fetch to get the builds and projects in case the user was followed
    ;; to some but not all projects.
    (api/get-dashboard-builds {} api-ch)
    (api/get-projects api-ch)
    (api/get-me api-ch)))
