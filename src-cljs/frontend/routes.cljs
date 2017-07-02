(ns frontend.routes
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [compassus.core :as compassus]
            [frontend.async :refer [put!]]
            [frontend.config :as config]
            [frontend.utils.vcs :as vcs]
            [goog.string :as gstring]
            [om.next :as om-next]
            [secretary.core :as sec :refer-macros [defroute]]))

(defn open!
  "Navigate to a (non-legacy) route.

  app   - The Compassus application.
  route - The route keyword to navigate to.
  data  - (optional) Route data for the `set-data mutation, including:
          :subpage      - (optional) The subpage route keyword to navigate to.
          :route-params - (optional) Additional route data gleaned from the URL."
  ([app route] (open! app route {}))
  ([app route data]
   ;; Note: We always call set-data: if values aren't given, we still want to
   ;; set them to `nil`.
   (compassus/set-route!
    app route
    {:tx (into [`(route-params/set ~data)]
               ;; Compassus doesn't re-read the wrapper query on navigation,
               ;; only the page query. This re-reads the wrapper query too.
               ;; That's important, because the wrapper query can depend on the
               ;; route params, which change during navigation. (For instance
               ;; ,the org picker needs to update to show the current org.)
               (om-next/transform-reads (compassus/get-reconciler app) [::compassus/mixin-data]))})))

(defn open-to-inner!
  "Navigate to a legacy route. As pages move from legacy to Om Next, their
  routes move from using `open-to-inner!` to using `open!`."
  [app nav-ch navigation-point args]
  (open! app :route/legacy-page)
  (put! nav-ch [navigation-point (assoc args :inner? true)]))

(defn logout! [nav-ch]
  (put! nav-ch [:logout]))

(defn parse-build-page-fragment [fragment]
  ;; do return any empty strings - return nil instead
  (let [fragment-str (str fragment)
        url-regex-str "([a-zA-Z0-9-]*)((/containers/(\\d+))(/actions/(\\d+))?)?"
        [_ tab-name _ _ container-num _ action-id] (re-find (re-pattern url-regex-str) fragment-str)
        container-id (some-> container-num
                             not-empty
                             js/parseInt)
        tab (some-> tab-name
                    not-empty
                    keyword)
        action-id (some-> action-id
                          not-empty
                          js/parseInt)]
    {:tab tab
     :action-id action-id
     :container-id container-id}))

(defn build-page-fragment [tab container-id action-id]
  (cond
    (and tab container-id action-id)
    (gstring/format "%s/containers/%s/actions/%s" (name tab) container-id action-id)

    (and tab container-id)
    (gstring/format "%s/containers/%s" (name tab) container-id)

    tab (name tab)
    :else nil))

(defn v1-build-path
  "Temporary helper method for v1-build until we figure out how to make
   secretary's render-route work for regexes"
  ([vcs_type org repo workflow-id build-num]
   (v1-build-path vcs_type org repo workflow-id build-num nil nil nil))
  ([vcs_type org repo workflow-id build-num tab]
   (v1-build-path vcs_type org repo workflow-id build-num tab nil nil))
  ([vcs_type org repo workflow-id build-num tab container-id]
   (v1-build-path vcs_type org repo workflow-id build-num tab container-id nil))
  ([vcs_type org repo workflow-id build-num tab container-id action-id]
   (let [fragment (build-page-fragment tab container-id action-id)]
     (str "/" (vcs/->short-vcs vcs_type) "/" org "/" repo "/"
          (when workflow-id
            (str "workflows/" workflow-id "/jobs/"))
          build-num (when fragment (str "#" fragment))))))

(defn v1-project-workflows-path
  ([vcs_type org repo] (v1-project-workflows-path vcs_type org repo nil))
  ([vcs_type org repo page]
   (str "/" (vcs/->short-vcs vcs_type) "/" org  "/workflows/" repo
        (when (and page (not= 1 page)) (str "?page=" page)))))

(defn v1-project-branch-workflows-path
  ([vcs_type org repo branch]
   (v1-project-branch-workflows-path vcs_type org repo branch nil))
  ([vcs_type org repo branch page]
   (str (v1-project-workflows-path vcs_type org repo)
        "/tree/"
        (gstring/urlEncode branch)
        (when (and page (not= 1 page)) (str "?page=" page)))))

(defn v1-run-path
  [workflow-id]
  (str "/workflow-run/" workflow-id))

(defn v1-org-workflows-path
  ([vcs_type org] (v1-org-workflows-path vcs_type org nil))
  ([vcs_type org page]
   (str "/" (vcs/->short-vcs vcs_type) "/" org "/workflows"
        (when (and page (not= 1 page)) (str "?page=" page)))))

(defn v1-job-path
  ([workflow-id job-name] (v1-job-path workflow-id job-name nil))
  ([workflow-id job-name {:keys [route-params/tab route-params/container-id route-params/action-id]}]
   (let [fragment (build-page-fragment tab container-id action-id)]
     (str "/workflow-run/" workflow-id "/" job-name (when fragment (str "#" fragment))))))

(defn v1-dashboard-path
  "Temporary helper method for v1-*-dashboard until we figure out how to
   make secretary's render-route work for multiple pages"
  [{:keys [vcs_type org repo branch page]}]
  (let [url (cond branch (str "/" (vcs/->short-vcs vcs_type) "/" org "/" repo "/tree/" branch)
                  repo (str "/" (vcs/->short-vcs vcs_type) "/" org "/" repo)
                  org (str "/" (vcs/->short-vcs vcs_type) "/" org)
                  :else "/dashboard")]
    (str url (when page (str "?page=" page)))))

(defn generate-url-str [format-str {:keys [vcs_type _fragment] :as params}]
  (let [short-vcs-type (if vcs_type
                         (vcs/->short-vcs vcs_type)
                         "gh")
        new-params (assoc params :vcs_type short-vcs-type)
        url (sec/render-route format-str new-params)
        new-fragment (when _fragment
                       (name _fragment))]
    (if new-fragment
      (str url "#" new-fragment)
      url)))

(defn v1-organization-dashboard-path
  "Generate URL string from params."
  [params]
  (generate-url-str "/:vcs_type/:org" params))

(defn v1-org-settings-path
  "Generate URL string from params."
  [params]
  (generate-url-str "/:vcs_type/organizations/:org/settings" params))

(defn v1-projects-path
  "Generate URL string from params."
  [params]
  (generate-url-str "/projects" params))

(defn v1-organization-projects-path
  "Generate URL string from params."
  [params]
  (generate-url-str "/projects/:vcs_type/:org" params))

(defn v1-project-dashboard-path
  "Generate URL string from params."
  [params]
  (generate-url-str "/:vcs_type/:org/:repo" params))

(defn v1-project-settings-path
  "Generate URL string from params."
  [params]
  (generate-url-str "/:vcs_type/:org/:repo/edit" params))

(defn v1-project-insights-path
  "Generate URL string from params."
  [params]
  (generate-url-str "/build-insights/:vcs_type/:org/:repo/:branch" params))

(defn v1-organization-insights-path
  [params]
  (generate-url-str "/build-insights/:vcs_type/:org" params))

(defn v1-add-projects-path
  [params]
  (generate-url-str "/add-projects/:vcs_type/:org" params))

(defn v1-setup-project-path
  [params]
  (generate-url-str "/setup-project/:vcs_type/:org" params))

(defn v1-team-path
  [params]
  (generate-url-str "/team/:vcs_type/:org" params))

(defn v1-admin-fleet-state-path
  [params]
  (generate-url-str "/admin/fleet-state" params))

(defn define-admin-routes! [app nav-ch]
  (defroute v1-admin "/admin" []
    (open-to-inner! app nav-ch :admin-settings {:admin true
                                                :subpage :overview}))
  (defroute v1-admin-fleet-state "/admin/fleet-state" [_fragment]
    (open-to-inner! app nav-ch :admin-settings {:admin true
                                                :subpage :fleet-state
                                                :tab (keyword _fragment)}))
  (when-not (config/enterprise?)
    (defroute v1-admin-switch "/admin/switch" []
      (open-to-inner! app nav-ch :admin-settings {:admin true
                                                  :subpage :switch})))
  (when (config/enterprise?)
    (defroute v1-admin-users "/admin/users" []
      (open-to-inner! app nav-ch :admin-settings {:admin true
                                                  :subpage :users}))
    (defroute v1-all-projects "/admin/projects" []
      (open-to-inner! app nav-ch :admin-settings {:admin true
                                                  :subpage :projects}))
    (defroute v1-admin-config "/admin/system-settings" []
      (open-to-inner! app nav-ch :admin-settings {:admin true
                                                  :subpage :system-settings}))
    (defroute v1-admin-system-management "/admin/management-console" []
      (.replace js/location
                ;; System management console is served at port 8800
                ;; with replicated and it's always https
                (str "https://" js/window.location.hostname ":8800/")))

    (defroute v1-admin-license "/admin/license" []
      (open-to-inner! app nav-ch :admin-settings {:admin true
                                                  :subpage :license}))))

(defn define-user-routes! [app nav-ch authenticated?]
  (defroute v1-org-settings #"/(gh|bb)/organizations/([^/]+)/settings"
    [short-vcs-type org _ maybe-fragment]
    (open-to-inner! app nav-ch :org-settings {:vcs_type (vcs/->lengthen-vcs short-vcs-type)
                                              :org org
                                              :subpage (keyword (or (:_fragment maybe-fragment)
                                                                    "overview"))}))

  (defroute v1-org-dashboard-alternative #"/(gh|bb)/organizations/([^/]+)" [short-vcs-type org params]
    (open-to-inner! app nav-ch :dashboard (merge params
                                             {:vcs_type (vcs/->lengthen-vcs short-vcs-type)
                                              :org org})))

  (defroute v1-org-dashboard #"/(gh|bb)/([^/]+)" [short-vcs-type org params]
    (open-to-inner! app nav-ch :dashboard (merge params
                                             {:vcs_type (vcs/->lengthen-vcs short-vcs-type)
                                              :org org})))

  (defroute v1-workflows "/workflows" []
    (open! app :route/workflows))

  (defroute v1-project-workflows #"/(gh|bb)/([^/]+)/workflows/([^/]+)"
    [short-vcs-type org-name project-name params]
    (let [page-str (get-in params [:query-params :page])]
      (open! app
             :route/project-workflows
             {:route-params
              {:organization/vcs-type (vcs/short-to-long-vcs short-vcs-type)
               :organization/name org-name
               :project/name project-name
               :page/number (let [page (js/parseInt page-str)]
                              (if (js/isNaN page)
                                1
                                page))}})))

  (defroute v1-project-branch-workflows #"/(gh|bb)/([^/]+)/workflows/([^/]+)/tree/([^/]+)"
    [short-vcs-type org-name project-name branch params]
    (let [page-str (get-in params [:query-params :page])]
      (open! app
             :route/project-branch-workflows
             {:route-params
              {:organization/vcs-type (vcs/short-to-long-vcs short-vcs-type)
               :organization/name org-name
               :project/name project-name
               :branch/name (gstring/urlDecode branch)
               :page/number (let [page (js/parseInt page-str)]
                              (if (js/isNaN page)
                                1
                                page))}})))

  (defroute v1-org-workflows #"/(gh|bb)/([^/]+)/workflows"
    [short-vcs-type org-name params]
    ;; Show splash screen until org-level view exists again.
    (open! app :route/workflows
           {:route-params
              {:organization/vcs-type (vcs/short-to-long-vcs short-vcs-type)
               :organization/name org-name}})
    #_(let [page-str (get-in params [:query-params :page])]
        (open! app
               :route/org-workflows
               {:route-params
                {:organization/vcs-type (vcs/short-to-long-vcs short-vcs-type)
                 :organization/name org-name
                 :page/number (let [page (js/parseInt page-str)]
                                (if (js/isNaN page)
                                  1
                                  page))}})))

  (defroute v1-project-dashboard #"/(gh|bb)/([^/]+)/([^/]+)" [short-vcs-type org repo params]
    (open-to-inner! app nav-ch :dashboard (merge params
                                             {:vcs_type (vcs/->lengthen-vcs short-vcs-type)
                                              :org org
                                              :repo repo})))

  (defroute v1-project-branch-dashboard #"/(gh|bb)/([^/]+)/([^/]+)/tree/(.+)" ; workaround secretary's annoying auto-decode
    [short-vcs-type org repo branch params]
    (open-to-inner! app nav-ch :dashboard (merge params
                                             {:vcs_type (vcs/->lengthen-vcs short-vcs-type)
                                              :org org
                                              :repo repo
                                              :branch branch})))

  (defroute v1-build #"/(gh|bb)/([^/]+)/([^/]+)/(\d+)"
    [short-vcs-type org repo build-num _ maybe-fragment]
    ;; normal destructuring for this broke the closure compiler
    (let [fragment-args (-> maybe-fragment
                            :_fragment
                            parse-build-page-fragment
                            (select-keys [:tab :action-id :container-id]))]
      (open-to-inner! app nav-ch :build (merge fragment-args
                                               {:vcs_type (vcs/->lengthen-vcs short-vcs-type)
                                                :project-name (str org "/" repo)
                                                :build-num (js/parseInt build-num)
                                                :org org
                                                :repo repo}))))

  (defroute v1-run #"/workflow-run/([^/]+)"
    [run-id]
    (open! app :route/run {:route-params {:run/id (uuid run-id)}}))

  (defroute v1-job #"/workflow-run/([^/]+)/([^/]+)"
    [run-id job-name _ maybe-fragment]
    (let [fragment-args (-> maybe-fragment
                            :_fragment
                            parse-build-page-fragment
                            (select-keys [:tab :action-id :container-id])
                            (set/rename-keys {:tab :route-params/tab
                                              :action-id :route-params/action-id
                                              :container-id :route-params/container-id}))]
      (open! app :route/run {:route-params (assoc fragment-args
                                                  :run/id (uuid run-id)
                                                  :job/name job-name)})))

  (defroute v1-project-settings #"/(gh|bb)/([^/]+)/([^/]+)/edit" [short-vcs-type org repo _ maybe-fragment]
    (open-to-inner! app nav-ch :project-settings {:vcs_type (vcs/->lengthen-vcs short-vcs-type)
                                                  :project-name (str org "/" repo)
                                                  :subpage (keyword (or (:_fragment maybe-fragment)
                                                                        "overview"))
                                                  :org org
                                                  :repo repo}))

  (defroute v1-add-projects "/add-projects" {:keys [_fragment]}
    (open-to-inner! app nav-ch :add-projects {:tab _fragment}))

  (defroute v1-setup-project "/setup-project" {:keys [_fragment]}
    (open-to-inner! app nav-ch :setup-project {:tab _fragment}))

  (defroute v1-organization-add-projects "/add-projects/:short-vcs-type/:org-name" {:keys [short-vcs-type org-name _fragment]}
    (open-to-inner! app nav-ch :add-projects {:tab _fragment :vcs_type (vcs/->lengthen-vcs short-vcs-type) :login org-name}))

  (defroute v1-organization-setup-project "/setup-project/:short-vcs-type/:org-name" {:keys [short-vcs-type org-name _fragment]}
    (open-to-inner! app nav-ch :setup-project {:tab _fragment :vcs_type (vcs/->lengthen-vcs short-vcs-type) :login org-name}))

  (defroute v1-insights "/build-insights" []
    (open-to-inner! app nav-ch :build-insights {}))
  (defroute v1-organization-insights "/build-insights/:short-vcs-type/:org-name" {:keys [short-vcs-type org-name _fragment]}
    (open-to-inner! app nav-ch :build-insights {:vcs_type (vcs/->lengthen-vcs short-vcs-type) :login org-name}))

  (defroute v1-insights-project #"/build-insights/(gh|bb)/([^/]+)/([^/]+)/([^/]+)" [short-vcs-type org repo branch]
    (open-to-inner! app nav-ch :project-insights {:org org :repo repo :branch branch :vcs_type (vcs/->lengthen-vcs short-vcs-type)}))
  (defroute v1-account "/account" []
    (open! app :route/account))
  (defroute v1-account-subpage "/account/:subpage" [subpage]
    (open! app :route/account {:subpage (keyword subpage)}))
  (defroute v1-organization-projects "/projects/:short-vcs-type/:org-name" {:keys [short-vcs-type org-name]}
    (open! app :route/projects {:route-params
                                {:organization/vcs-type (vcs/short-to-long-vcs short-vcs-type)
                                 :organization/name org-name}}))
  (defroute v1-projects "/projects" []
    (open! app :route/projects))
  (defroute v1-team "/team" []
    (open-to-inner! app nav-ch :team {}))
  (defroute v1-organization-team "/team/:short-vcs-type/:org-name" {:keys [short-vcs-type org-name]}
    (open-to-inner! app nav-ch :team {:vcs_type (vcs/->lengthen-vcs short-vcs-type) :login org-name}))
  (defroute v1-logout "/logout" []
    (logout! nav-ch))

  (defroute v1-root "/" {:as params}
    (if authenticated?
      (open-to-inner! app nav-ch :dashboard params)
      (open-to-inner! app nav-ch :landing (assoc params :_canonical "/"))))

  (defroute v1-dashboard "/dashboard" {:as params}
    (open-to-inner! app nav-ch :dashboard params)))

(defn define-routes! [current-user app nav-ch]
  (let [authenticated? (boolean current-user)]
    (define-user-routes! app nav-ch authenticated?)
    (when (:admin current-user)
      (define-admin-routes! app nav-ch))))

(defn parse-uri [uri]
  (let [[uri-path fragment] (str/split (sec/uri-without-prefix uri) "#")
        [uri-path query-string] (str/split uri-path  #"\?")
        uri-path (sec/uri-with-leading-slash uri-path)]
    [uri-path query-string fragment]))

(defn dispatch!
  "Dispatch an action for a given route if it matches the URI path."
  ;; Based on secretary.core: https://github.com/gf3/secretary/blob/579bc224f23e6c26a2299a2e5a48491fd3792faf/src/secretary/core.cljs#L314
  [uri]
  (let [[uri-path query-string fragment] (parse-uri uri)
        query-params (when query-string
                       {:query-params (sec/decode-query-params query-string)})
        {:keys [action params]} (sec/locate-route uri-path)
        action (or action identity)
        params (merge params query-params {:_fragment fragment})]
    (action params)))

(defn om-next-nav-point?
  "Would be better to match a map to frontend.components.app/routes
   but that creates all sorts of circular dependencies"
  [nav-point]
  (= "route" (namespace nav-point)))

(defn org-centric-path
  "Generate url to current navigation-point for new org
   Routes that are not org-specic (e.g. landing) return
    nil. Nil responses are ignored."
  [{:keys [nav-point current-org]}]
  (let [org {:org (:login current-org)
             :vcs_type (:vcs_type current-org)}]
    (case nav-point
      :admin-settings nil
      :add-projects (v1-add-projects-path org)
      :build-insights (v1-organization-insights-path org)
      :dashboard (v1-organization-dashboard-path org)
      :error nil
      :invite-teammates (v1-team-path org)
      :landing nil
      :logout nil
      :org-settings (v1-org-settings-path org)
      :project-insights (v1-organization-insights-path org)
      :route/account (v1-account)
      :route/projects (v1-organization-projects-path org)
      :setup-project (v1-setup-project-path org)
      :team (v1-team-path org)

      (:route/workflows
       :route/org-workflows
       :route/project-workflows
       :route/project-branch-workflows
       :route/run)
      (v1-org-workflows-path (:vcs_type org)
                             (:org org))

      (v1-organization-dashboard-path org))))
