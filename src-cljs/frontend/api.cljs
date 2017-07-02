(ns frontend.api
  (:require [clojure.set :as set]
            [frontend.api.path :as path]
            [frontend.models.action :as action-model]
            [frontend.models.build :as build-model]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :as gstring]
            [secretary.core :as sec]))

(def build-keys-mapping {:username :org
                         :reponame :repo
                         :default_branch :branch
                         :vcs-type :vcs_type})

(defn project-build-key
  "Takes project hash and filter down to keys that identify the build."
  ([project]
   (-> project
       (set/rename-keys build-keys-mapping)
       (select-keys (vals build-keys-mapping)))))

(defn get-me [api-ch]
  (ajax/ajax :get "/api/v1/me" :me api-ch :context {}))

(defn get-projects [api-ch & {:as context}]
  (ajax/ajax :get "/api/v1/projects?shallow=true" :projects api-ch :context context))

(defn get-github-repos [api-ch & {:keys [page message] :or {page 1
                                                            message :github-repos}}]
  (ajax/ajax :get (str "/api/v1/user/repos?page=" page)
             message
             api-ch
             :context {:page page
                       :vcs :github}))

(defn follow-projects [vcs-urls api-ch uuid on-success]
  (ajax/ajax :post (str "/api/v1.1/user/follow-projects")
             :follow-projects
             api-ch
             :params {:vcs-urls vcs-urls}
             :context {:uuid uuid
                       :on-success on-success}))

(defn get-bitbucket-repos [api-ch & {:keys [message] :or {message :bitbucket-repos}}]
  (ajax/ajax :get (str "/api/v1.1/user/repos/bitbucket")
             message
             api-ch
             :context {:vcs :bitbucket}))

(defn get-all-repos [api-ch]
  (get-bitbucket-repos api-ch :message :all-repos)
  (get-github-repos api-ch :message :all-repos))

(defn get-orgs [api-ch & {:keys [include-user?]}]
  (ajax/ajax :get (str "/api/v1/user/organizations" (when include-user? "?include-user=true"))
             :organizations
             api-ch))

(defn get-org-members [org-name vcs-type api-ch]
  (ajax/ajax :get (path/org-members vcs-type org-name)
             :get-org-members
             api-ch
             :context {:org-name org-name
                       :vcs-type vcs-type}))

(defn get-org-plan [org-name vcs-type api-ch message]
  "Used both to get plan info (message :org-plan) and
   as proxy for whether or not a user has access to
   an organization (message :org-check)"
  (ajax/ajax :get
             (gstring/format "/api/v1.1/organization/%s/%s/plan"
                             vcs-type
                             org-name)
             message
             api-ch
             :context {:org-name org-name
                       :vcs-type vcs-type}))

(defn get-workflow-status [api-ch run-id]
  (ajax/ajax :post
             "/query-api"
             :uncatchable-by-old-controller
             api-ch
             :format :transit
             :params
             {:type :get-workflow-status
              :params {:run/id run-id}}))

(defn get-project-workflows [api-ch vcs-type org-name repo-name opts]
  (ajax/ajax :post
             "/query-api"
             :uncatchable-by-old-controller
             api-ch
             :format :transit
             :params
             {:type :get-project-workflows
              :params {:organization/vcs-type (keyword vcs-type)
                       :organization/name org-name
                       :project/name repo-name
                       :opts opts}}))

(defn get-branch-workflows [api-ch vcs-type org-name repo-name branch-name opts]
  (ajax/ajax :post
             "/query-api"
             :uncatchable-by-old-controller
             api-ch
             :format :transit
             :params
             {:type :get-branch-workflows
              :params {:organization/vcs-type (keyword vcs-type)
                       :organization/name org-name
                       :project/name repo-name
                       :branch/name branch-name
                       :opts opts}}))

(defn get-org-workflows [api-ch vcs-type org-name opts]
  (ajax/ajax :post
             "/query-api"
             :uncatchable-by-old-controller
             api-ch
             :format :transit
             :params
             {:type :get-org-workflows
              :params {:organization/name org-name
                       :organization/vcs-type (keyword vcs-type)
                       :opts opts}}))

(defn mutate [api-ch mutation]
  (ajax/ajax :post
             "/query-api"
             :uncatchable-by-old-controller
             api-ch
             :format :transit
             :params mutation))

(defn get-user-plans [api-ch]
  (ajax/ajax :get "/api/v1/user/organizations/plans"
             :user-plans
             api-ch))

(defn get-usage-queue [{:keys [vcs_url vcs_type build_num] :as build} api-ch]
  (ajax/ajax :get
             (gstring/format "/api/v1.1/project/%s/%s/%s/%s/usage-queue"
                             vcs_type
                             (vcs-url/org-name vcs_url)
                             (vcs-url/repo-name vcs_url)
                             build_num)
             :usage-queue
             api-ch
             :context (build-model/id build)))

;; Note that dashboard-builds-url can take a :page (within :query-params)
;; and :builds-per-page, or :limit and :offset directly.
(defn dashboard-builds-url [{vcs-type :vcs_type
                             :keys [branch repo org admin deployments query-params builds-per-page offset limit all?]
                             :as args}]
  (let [offset (or offset (* (get query-params :page 0) builds-per-page))
        limit (or limit builds-per-page)
        url (cond admin "/api/v1/admin/recent-builds"
                  deployments "/api/v1/admin/deployments"
                  branch (gstring/format "/api/v1.1/project/%s/%s/%s/tree/%s" vcs-type org repo branch)
                  repo (gstring/format "/api/v1.1/project/%s/%s/%s" vcs-type org repo)
                  org (gstring/format "/api/v1.1/organization/%s/%s" vcs-type org)
                  :else "/api/v1/recent-builds")]
    (str url "?" (sec/encode-query-params (merge {:shallow true
                                                  :offset offset
                                                  :limit limit
                                                  :mine (= all? false)}
                                                 query-params)))))

(defn get-dashboard-builds [{:keys [branch repo org query-params] :as args} api-ch]
  (let [url (dashboard-builds-url args)]
    (ajax/ajax :get url
               :recent-builds api-ch
               :context {:branch branch
                         :repo repo
                         :org org
                         :query-params query-params})))

(defn get-admin-dashboard-builds
  [tab api-ch]
  (get-dashboard-builds
    {:admin true
     :query-params {:status (case tab
                              :running-builds "running"
                              :queued-builds "scheduled,queued")
                    :order "asc"}}
    api-ch))

;; This is defined in the API.
(def max-allowed-page-size 100)

(defn- get-insights-recent-build* [build-key build-count api-ch]
  ;; Assemble a list of pages descriptions and result atoms to deliver them to.
  (let [page-starts (range 0 build-count max-allowed-page-size)
        page-ends (concat (rest page-starts) [build-count])
        pages (for [[start end] (map vector page-starts page-ends)]
                {:offset start
                 :limit (- end start)
                 :page-result (atom nil)})
        page-results (map :page-result pages)]
    (doseq [{:keys [offset limit page-result]} pages]
      (let [url (dashboard-builds-url (assoc build-key :offset offset :limit limit))]
        ;; Fire off an ajax call for the page. The API controllers will
        ;; deliver the response to page-result, and put the full data in the
        ;; state once all of the page-results are delivered.
        (ajax/ajax :get url :insights-recent-builds api-ch
                   :context {:project-id build-key
                             :page-result page-result
                             :all-page-results page-results}
                   :params {:filter "build-insights"})))))

(defn get-build-insights-data [build-keys api-ch]
  (doseq [build-key build-keys]
    (get-insights-recent-build* build-key 60 api-ch)))

(defn branch-build-times-url [target-key]
  (sec/render-route "/api/v1/project/:vcs_type/:org/:repo/build-timing/:branch" target-key))

(defn- get-branch-build-times [{:keys [org repo branch] :as target-key} api-ch]
  (let [url (branch-build-times-url target-key)]
    (ajax/ajax :get url :branch-build-times api-ch :context {:target-key target-key} :params {:days 90})))

(defn get-project-insights-data [build-key api-ch]
  (get-insights-recent-build* build-key 100 api-ch)
  (get-branch-build-times build-key api-ch))

(defn get-action-output [{:keys [vcs-url build-num step index]
                          :as args} api-ch]
  (let [url (path/action-output (vcs-url/vcs-type vcs-url)
                                (vcs-url/project-name vcs-url)
                                build-num
                                step
                                index
                                action-model/max-output-size)]
    (ajax/ajax :get url :action-log api-ch :context args)))

(defn get-action-steps [{:keys [vcs-url build-num] :as args} api-ch]
  (let [vcs-type (vcs-url/vcs-type vcs-url)
        url (gstring/format "/api/v1.1/project/%s/%s/%s/action-steps"
                            vcs-type
                            (vcs-url/project-name vcs-url)
                            build-num)]
    (ajax/ajax :get url :action-steps api-ch :context args)))

(defn get-org-settings [org-name vcs-type api-ch]
  (ajax/ajax :get
             (gstring/format "/api/v1.1/organization/%s/%s/settings" vcs-type org-name)
             :org-settings
             api-ch
             :context {:org-name org-name
                       :vcs-type vcs-type}))

(defn get-org-settings-normalized
  "Issues an API call for the org's settings. The settings will be stored in a
  normalized table at the root of the app state."
  ([org-name vcs-type api-ch] (get-org-settings-normalized org-name vcs-type api-ch nil []))
  ([org-name vcs-type api-ch params initial-users]
   (ajax/ajax :get
              (gstring/format "/api/v1.1/organization/%s/%s/settings" vcs-type org-name)
              :org-settings-normalized
              api-ch
              :params params
              :context {:org_name org-name :vcs_type vcs-type :users initial-users})))

(defn get-build-tests [build api-ch & [msg]]
  (let [vcs-type (:vcs_type build)
        tests-url (gstring/format "/api/v1.1/project/%s/%s/%s/tests"
                                  vcs-type
                                  (vcs-url/project-name (:vcs_url build))
                                  (:build_num build))
        msg (or msg :build-tests)]
    (ajax/ajax :get
               tests-url
               msg
               api-ch
               :context (build-model/id build))))

(defn get-build-observables [{:keys [username project build-num vcs-type] :as parts} api-ch]
  (let [url (gstring/format "/api/v1.1/project/%s/%s/%s/%d/observables"
                            vcs-type username project build-num)]
    (ajax/ajax :get url :build-observables api-ch :context {:build-parts parts})))

(defn get-build-state [api-ch]
  (ajax/ajax :get "/api/v1/admin/build-state" :build-state api-ch))

(defn get-fleet-state [api-ch]
  (ajax/ajax :get "/api/v1/admin/build-state-summary" :fleet-state api-ch)
  (ajax/ajax :get "/api/v1/admin/build-system-summary" :build-system-summary api-ch))

(defn get-all-users [api-ch]
  (ajax/ajax :get "/api/v1/admin/users" :all-users api-ch))

(defn get-all-projects [api-ch]
  (ajax/ajax :get "/api/v1/admin/projects" :all-projects api-ch))

(defn get-all-system-settings [api-ch]
  (ajax/ajax :get "/api/v1/admin/settings" :get-all-system-settings api-ch))

(defn set-user-suspension [login suspended? api-ch]
  (ajax/ajax :post
             (gstring/format "/api/v1/admin/user/%s" login)
             :set-user-admin-state
             api-ch
             :params {:suspended suspended?}))

(defn set-user-admin-scope [login scope api-ch]
  (ajax/ajax :post
             (gstring/format "/api/v1/admin/user/%s" login)
             :set-user-admin-state
             api-ch
             :params {:admin scope}))

(defn set-system-setting [name value api-ch]
  (ajax/ajax :post
             (gstring/format "/api/v1/admin/setting/%s" name)
             :system-setting-set
             api-ch
             :params value))

(defn get-project-settings [vcs-type org repo api-ch]
  (ajax/ajax :get
             (path/project-settings vcs-type org repo)
             :project-settings
             api-ch
             :context {:project-name (str org "/" repo)}))

(defn get-project-code-signing-keys [project-name vcs-type api-ch]
  (ajax/ajax :get
             (gstring/format "%s/%s/code-signing/osx-keys" (path/base-project-url-path vcs-type) project-name)
             :get-code-signing-keys
             api-ch
             :context {:project-name project-name}))

(defn set-project-code-signing-keys [project-name vcs-type file-content file-name password description api-ch uuid on-success]
  (ajax/ajax :post
             (gstring/format "%s/%s/code-signing/osx-keys" (path/base-project-url-path vcs-type) project-name)
             :set-code-signing-keys
             api-ch
             :params {:file-content file-content
                      :file-name file-name
                      :password password
                      :description description}
             :context {:project-name project-name
                       :vcs-type vcs-type
                       :uuid uuid
                       :on-success on-success}))

(defn get-project-provisioning-profiles [project-name vcs-type api-ch]
  (ajax/ajax :get
             (gstring/format "%s/%s/code-signing/osx-profiles" (path/base-project-url-path vcs-type) project-name)
             :get-provisioning-profiles
             api-ch
             :context {:project-name project-name}))

(defn set-project-provisioning-profiles [project-name vcs-type file-content file-name description api-ch uuid on-success]
  (ajax/ajax :post
             (gstring/format "%s/%s/code-signing/osx-profiles" (path/base-project-url-path vcs-type) project-name)
             :set-provisioning-profiles
             api-ch
             :params {:file-content file-content
                      :file-name file-name
                      :description description}
             :context {:project-name project-name
                       :vcs-type vcs-type
                       :uuid uuid
                       :on-success on-success}))

(defn delete-project-code-signing-key [project-name vcs-type id api-ch on-success uuid]
  (ajax/ajax :delete
             (gstring/format "%s/%s/code-signing/osx-keys/%s" (path/base-project-url-path vcs-type) project-name id)
             :delete-code-signing-key
             api-ch
             :context {:project-name project-name
                       :id id
                       :uuid uuid
                       :on-success on-success}))

(defn delete-project-provisioning-profile [project-name vcs-type profile-uuid api-ch on-success button-uuid]
  (ajax/ajax :delete
             (gstring/format "%s/%s/code-signing/osx-profiles/%s" (path/base-project-url-path vcs-type) project-name profile-uuid)
             :delete-provisioning-profile
             api-ch
             :context {:project-name project-name
                       :vcs-type vcs-type
                       :uuid button-uuid
                       :on-success on-success}))

(defn get-enterprise-site-status [api-ch]
  (ajax/ajax :get "/api/v1/enterprise/site-status" :enterprise-site-status api-ch))

(defn get-jira-projects [project-name vcs-type api-ch]
  (ajax/ajax :get
             (path/jira-projects vcs-type project-name)
             :get-jira-projects
             api-ch
             :context {:project-name project-name
                       :vcs-type vcs-type}))

(defn get-jira-issue-types [project-name vcs-type api-ch]
  (ajax/ajax :get
             (path/jira-issue-types vcs-type project-name)
             :get-jira-issue-types
             api-ch
             :context {:project-name project-name
                       :vcs-type vcs-type}))

(defn create-jira-issue [project-name vcs-type {:keys [project type summary description]} api-ch uuid on-success]
  (ajax/ajax :post
             (path/jira-issue vcs-type project-name)
             :create-jira-issue
             api-ch
             :params {:jira-project project
                      :issue-type type
                      :summary summary
                      :description description}
             :context {:project-name project-name
                       :vcs-type vcs-type
                       :uuid uuid
                       :on-success on-success}))
