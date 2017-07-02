(ns frontend.state)

(def initial-state
  {:error-message nil
   :general-message nil
   ;; A/B test instructions:
   ;; 1. Don't define a test with null as one of the options
   ;; 2. If you change a test's options, you must also change the test's name
   ;; 3. Record your tests here: https://docs.google.com/a/circleci.com/spreadsheet/ccc?key=0AiVfWAkOq5p2dE1MNEU3Vkw0Rk9RQkJNVXIzWTAzUHc&usp=sharing

   ;; Please kebab-case and not snak_case tests and treatments
   :ab-test-definitions {:a-is-a [true false]
                         :multi-test-equal-variants [:a :b :c :d]
                         :auth-button-vs-page [:button :page]
                         ;; TODO: The below are ab tests that have been running since December 2014. We should figure out if they are being
                         ;; tracked, which are the winners, launch them, and delete the dead code.
                         :pay_now_button [true false]
                         :follow_notice [true false]
                         :new_usage_queued_upsell [true false]}
   :environment "development"
   :settings {:projects {}            ; hash of project-id to settings
              :organizations  {:circleci  {:plan {}}}
              :add-projects {:repo-filter-string ""
                             :selected-org {:login nil
                                            :type :org}
                             :show-forks true}
              :browser-settings {:expanded-repos #{}
                                 :show-all-builds true}}
   :selected-home-technology-tab nil
   :modal-video-id nil
   :builds-per-page 30
   :current-user nil
   :crumbs nil
   :render-context nil
   :projects nil
   :recent-builds nil
   :project-settings-project-name nil
   :org-settings {:subpage nil
                  :org-name nil
                  :vcs_type nil}
   :admin-settings-subpage nil
   :dashboard-data {:branch nil
                    :repo nil
                    :org nil}
   :current-project-data {:project nil
                          :plan nil
                          :settings {}
                          :tokens nil
                          :checkout-keys nil
                          :envvars nil}
   :current-org-data {:plan nil
                      :projects nil
                      :users nil
                      :invoices nil
                      :name nil}
   :instrumentation []
   :hamburger-menu "closed"
   ;; This isn't passed to the components, it can be accessed though om/get-shared :_app-state-do-not-use
   :inputs nil
   :insights {:selected-filter :all
              :selected-sorting :alphabetical}})

(def user-path [:current-user])

(def build-data-path [:current-build-data])

(def recent-builds :recent-builds)
(def recent-builds-path [recent-builds])

(defn recent-builds-branch-path [branch]
  (conj recent-builds-path branch))

;; state related to the first-green-build invite box
(def build-invite-data-path (conj build-data-path :invite-data))
(def build-invite-members-path (conj build-invite-data-path :org-members))
(defn build-invite-member-path [index] (conj build-invite-members-path index))

(def build-path [:current-build-data :build])
(def dismiss-invite-form-path (conj build-invite-data-path :dismiss-invite-form))
(def dismiss-config-errors-path (conj build-data-path :dismiss-config-errors))
(def invite-logins-path (conj build-data-path :invite-data :invite-logins))
(defn invite-login-path [login] (conj invite-logins-path login))

(def usage-queue-path [:current-build-data :usage-queue-data :builds])
(defn usage-queue-build-path [build-index] (conj usage-queue-path build-index))
(def show-usage-queue-path [:current-build-data :usage-queue-data :show-usage-queue])

(def artifacts-path [:current-build-data :artifacts-data :artifacts])
(def show-artifacts-path [:current-build-data :artifacts-data :show-artifacts])

(def tests-path [:current-build-data :tests-data :tests])
(def tests-parse-errors-path [:current-build-data :tests-data :exceptions])

(def show-config-path [:current-build-data :config-data :show-config])

(def navigation-data :navigation-data)
(def navigation-data-path [navigation-data])
(def inner?-path (conj navigation-data-path :inner?))
(def navigation-repo-path (conj navigation-data-path :repo))
(def navigation-org-path (conj navigation-data-path :org))
(def navigation-subpage-path (conj navigation-data-path :subpage))
(def navigation-tab-path (conj navigation-data-path :tab))
(def current-action-id-path (conj navigation-data-path :action-id))

(def selected-org-path [:selected-org])

(def container-data-path [:current-build-data :container-data])
(def containers-path [:current-build-data :container-data :containers])
(def current-container-filter-path [:current-build-data :container-data :current-filter])
(def current-container-path (conj navigation-data-path :container-id))
(defn current-container-id
  [target-state & {:keys [allow-nils?]}]
  (or (get-in target-state current-container-path)
      (when-not allow-nils?
        0)))
(def container-paging-offset-path [:current-build-data :container-data :paging-offset])
(def build-header-tab-path [:current-build-data :selected-header-tab])

(def org-settings-path [:org-settings])
(def org-settings-subpage-path (conj org-settings-path :subpage))
(def org-settings-org-name-path (conj org-settings-path :org-name))
(def org-settings-vcs-type-path (conj org-settings-path :vcs_type))

(defn container-path [container-index] (conj containers-path container-index))
(defn actions-path [container-index] (conj (container-path container-index) :actions))
(defn action-path [container-index action-index] (conj (actions-path container-index) action-index))
(defn action-output-path [container-index action-index] (conj (action-path container-index action-index) :output))
(defn show-action-output-path [container-index action-index] (conj (action-path container-index action-index) :show-output))

(def project-data-path [:current-project-data])
(def project-plan-path (conj project-data-path :plan))
(def project-plan-org-path (conj project-plan-path :org))
(def project-plan-org-name-path (conj project-plan-org-path :name))
(def project-plan-vcs-type-path (conj project-plan-org-path :vcs_type))
(def project-tokens-path (conj project-data-path :tokens))
(def project-checkout-keys-path (conj project-data-path :checkout-keys))
(def project-envvars-path (conj project-data-path :envvars))
(def project-settings-branch-path (conj project-data-path :settings-branch))
(def project-path (conj project-data-path :project))
(def project-parallelism-path (conj project-path :parallel))
(def project-repo-path (conj project-path :reponame))
(def project-default-branch-path (conj project-path :default_branch))
(def project-scopes-path (conj project-data-path :project-scopes))
(def page-scopes-path [:page-scopes])
(def project-osx-keys-path (conj project-data-path :osx-keys))
(def project-osx-profiles-path (conj project-data-path :osx-profiles))

(def jira-data-path (conj project-path :jira))
(def jira-projects-path (conj jira-data-path :projects))
(def jira-issue-types-path (conj jira-data-path :issue-types))

(def project-new-ssh-key-path (conj project-data-path :new-ssh-key))
(def project-new-api-token-path (conj project-data-path :new-api-token))

(def crumbs-path [:crumbs])
(defn project-branch-crumb-path [state]
  (let [crumbs (get-in state crumbs-path)
        project-branch-crumb-index (->> crumbs
                                        (keep-indexed
                                          #(when (= (:type %2) :project-branch)
                                             %1))
                                        first)]
    (conj crumbs-path project-branch-crumb-index)))

;; TODO we probably shouldn't be storing repos in the user...
(def user-login-path (conj user-path :login))
(def user-organizations-path (conj user-path :organizations))
(def user-org-prefs-path (conj user-path :organization_prefs))
(def user-plans-path (conj user-path :plans))
(def user-tokens-path (conj user-path :tokens))
(def user-analytics-id-path (conj user-path :analytics_id))
(def user-sign-in-count-path (conj user-path :sign_in_count))
(def user-created-at-path (conj user-path :created_at))

(def repos-path (conj user-path :repos))
(defn repo-path [repo-index] (conj repos-path repo-index))

(def github-repos-loading-path (conj user-path :repos-loading :github))
(def bitbucket-repos-loading-path (conj user-path :repos-loading :bitbucket))

(defn all-repos-loaded-path [vcs-type]
  (conj user-path :nux vcs-type :repos-loaded))

(def repos-building-path (conj user-path :nux :repos))
(def setup-project-projects-path (conj user-path :setup-project :projects))
(def setup-project-selected-project-path (conj user-path :setup-project :selected-project))

(def user-email-prefs-key :basic_email_prefs)
(def user-email-prefs-path (conj user-path :basic_email_prefs))
(def user-selected-email-key :selected_email)
(def user-selected-email-path (conj user-path user-selected-email-key))

(def user-in-beta-key :in_beta_program)
(def user-in-beta-path (conj user-path user-in-beta-key))
(def user-betas-key :enrolled_betas)
(def user-betas-path (conj user-path user-betas-key))

(def org-data-path [:current-org-data])
(def org-name-path (conj org-data-path :name))
(def org-vcs_type-path (conj org-data-path :vcs_type))
(def org-plan-path (conj org-data-path :plan))
(def org-plan-balance-path (conj org-plan-path :account_balance))
(def stripe-card-path (conj org-data-path :card))
(def org-users-path (conj org-data-path :users))
(def org-projects-path (conj org-data-path :projects))
(def org-loaded-path (conj org-data-path :loaded))
(def org-admin?-path (conj org-data-path :admin?))
(def selected-containers-path (conj org-data-path :selected-containers))
;; Map of org login to boolean (selected or not selected)
(def selected-piggieback-orgs-path (conj org-data-path :selected-piggieback-orgs))
(def selected-transfer-org-path (conj org-data-path :selected-transfer-org))
(def org-invoices-path (conj org-data-path :invoices))
(def selected-cancel-reasons-path (conj org-data-path :selected-cancel-reasons))
;; Map of reason to boolean (selected or not selected)
(defn selected-cancel-reason-path [reason] (conj selected-cancel-reasons-path reason))
(def cancel-notes-path (conj org-data-path :cancel-notes))

(def settings-path [:settings])

(def projects-path [:projects])

(def instrumentation-path [:instrumentation])

(def browser-settings-path [:settings :browser-settings])
(def last-visited-org-path (conj browser-settings-path :last-visited-org))
(def show-instrumentation-line-items-path (conj browser-settings-path :show-instrumentation-line-items))
(def show-admin-panel-path (conj browser-settings-path :show-admin-panel))
(def show-all-branches-path (conj browser-settings-path :show-all-branches))
(def show-all-builds-path (conj browser-settings-path :show-all-builds))
(def expanded-repos-path (conj browser-settings-path :expanded-repos))
(def sort-branches-by-recency-path (conj browser-settings-path :sort-branches-by-recency))
(defn project-branches-collapsed-path [project-id] (conj browser-settings-path :projects project-id :branches-collapsed))
(def show-inspector-path (conj browser-settings-path :show-inspector))
(def statuspage-dismissed-update-path (conj browser-settings-path :statuspage-dismissed-update))
(def dismissed-osx-usage-level (conj browser-settings-path :dismissed-osx-usage-level))
(def dismissed-trial-offer-banner (conj browser-settings-path :dismissed-trial-offer-banner))
(def dismissed-trial-update-banner (conj browser-settings-path :dismissed-trial-update-banner))
(def signup-event-sent (conj browser-settings-path :signup-event-sent))

(def show-stripe-error-banner-path [:show-stripe-error-banner])
(def show-admin-error-banner-path [:show-admin-error-banner])

(def web-notifications-enabled-path (conj browser-settings-path :web-notifications-enabled?))
(def remove-web-notification-banner-path (conj browser-settings-path :remove-web-notification-banner?))
(def remove-web-notification-confirmation-banner-path (conj browser-settings-path :remove-web-notification-banner-follow-up?))

(def add-projects-settings-path (conj settings-path :add-projects))
(def add-projects-selected-org-path (conj add-projects-settings-path :selected-org))
(def add-projects-selected-org-login-path (conj add-projects-selected-org-path :login))

(def new-user-token-path (conj user-path :new-user-token))

(def flash-path [:render-context :flash])

(def error-data-path [:error-data])

(def selected-home-technology-tab-path [:selected-home-technology-tab])

(def modal-video-id-path [:modal-video-id])

(def language-testimonial-tab-path [:selected-language-testimonial-tab])

(def build-state-path [:build-state])

(def fleet-state-path [:build-system :builders])
(def build-system-summary-path [:build-system :queue-and-build-counts])

(def license-path [:render-context :enterprise_license])

(def all-users-path [:all-users])
(def all-projects-path [:all-projects])

(def error-message-path [:error-message])
(def general-message-path [:general-message])

(def inputs-path [:inputs])
(def input-settings-branch-path (conj inputs-path :settings-branch))

(def docs-data-path [:docs-data])
(def docs-search-path [:docs-query])
(def docs-articles-results-path [:docs-articles-results])
(def docs-articles-results-query-path [:docs-articles-results-query])

(def customer-logo-customer-path [:customer-logo-customer])

(def selected-toolset-path [:selected-toolset])

(def pricing-parallelism-path [:pricing-parallelism])

(def hamburger-menu-path [:hamburger-menu])

(def insights-path [:insights])
(def insights-filter-path (conj insights-path :selected-filter))
(def insights-sorting-path (conj insights-path :selected-sorting))
(def project-insights-path (conj insights-path :project))
(def failed-builds-tests-path (conj project-insights-path :failed-tests))
(def failed-builds-junit-enabled?-path (conj project-insights-path :junit-enabled?))

(def current-view :navigation-point)
(def current-view-path [current-view])

(def system-settings-path [:system-settings])
(def feature-flags-path (conj project-path :feature_flags))

(def flash-notification-path [:flash-notification])


(defn add-flash-notification
  "Adds a flash notification to the state."
  [state message]
  (update-in state flash-notification-path
             #(-> %
                  (update :number inc)
                  (assoc :message message))))


;--------- "Om Next" -----------
(defn org-ident [vcs-type org-name]
  [:organization/by-vcs-type-and-name [vcs-type org-name]])

(defn vcs-users-path [vcs-type org-name]
  (conj (org-ident vcs-type org-name) :vcs-users))

(def insights-routes #{:build-insights :project-insights})
(def dashboard-routes #{:dashboard :build})
(def workflows-routes #{:route/workflows
                        :route/project-workflows
                        :route/project-branch-workflows
                        :route/org-workflows
                        :route/run})
