(ns frontend.parser
  (:require [bodhi.aliasing :as aliasing]
            [bodhi.core :as bodhi]
            [bodhi.default-db :as default-db]
            [bodhi.link-traversing :as link-traversing]
            [bodhi.param-indexing :as param-indexing]
            [bodhi.query-mapping :as query-mapping]
            [compassus.core :as compassus]
            [frontend.analytics.core :as analytics]
            [frontend.components.app :as app]
            [frontend.parser.connection :as connection]
            [om.next :as om-next]))

(defn- legacy-state-read
  "Read middleware. Handles reads of :legacy/state. For local reads, it returns
  the entire legacy state without further processing (as queries don't really
  apply to it). For remote reads, filters the expression out of the remote
  query, as it's local-only."
  [next-read]
  (fn [{:keys [ast target state] :as env}]
    (if (= :legacy/state (:key ast))
      {target nil
       ;; Don't include :inputs; it's not meant to be passed into the top of the
       ;; legacy app, but instead is accessed directly by
       ;; frontend.components.inputs/get-inputs-from-app-state.
       :value (dissoc (:legacy/state @state) :inputs)}
      (next-read env))))


(def ^{:private true} filtered-keys
  #{:app/route-params
    ;; These keys only exist in the initial app state, added from the page's
    ;; renderContext.
    :user/login
    :user/github-oauth-scopes
    :user/bitbucket-authorized?})

(defn- remote-filtered-read
  "Read middleware. Filters specified keys out of remote queries."
  [next-read]
  (fn [{:keys [ast target state] :as env}]
    ;; Filter on :dispatch-key, not :key, to catch links and idents as well.
    (if (and target (contains? filtered-keys (:dispatch-key ast)))
      {target nil}
      (next-read env))))

(defn- subpage-read
  "Read middleware. Handles subpage queries much as Compassus handles page
  queries."
  [next-read]
  (fn [{:keys [ast target state query parser] :as env}]
    (case (:key ast)
      :app/subpage-route
      (if target
        ;; The subpage route is a purely local concern.
        {target nil}
        (next-read env))

      ;; :app/subpage-route-data expects to have a union query (a map), where each key
      ;; is a subpage route and each value is the query for that route's subpage.
      ;;
      ;; :app/subpage-route-data will read the query for whichever subpage route is
      ;; current, by calling the parser recursively. (This is exactly how Compassus works.)
      :app/subpage-route-data
      (if target
        ;; Subpage data can't read from the remote server yet.
        {target nil}
        (let [subpage-route (get @state :app/subpage-route)
              subpage-query (get query subpage-route)]
          ;; Run the subpage's query against the parser.
          {:value (parser (update env :read-path pop) subpage-query)}))

      (next-read env))))

;; Describes the mapping from :routed-entity/* keys, which refer to entities
;; specified by route params, to the queries that actually read those entities.
;; In the given functions, the first parameter is the subquery to be read from
;; that entity, and the second is the map of route params set by the routes. The
;; vector before the function is a path into the resulting data which will find
;; the entity.
;;
;; Note that we always alias (`:<`) the outermost expression we return in these,
;; in case the key is already used elsewhere in the same query. For instance, if
;; we use `:routed-entity/organization` and `:routed-entity/project` on the same
;; page and didn't alias the expression, we'd have two expressions reading
;; `:circleci/organization` in the same place, which would break.
(def ^:private routed-data-query-map
  {:routed-entity/organization
   [[:routed-organization]
    (fn [{:keys [organization/vcs-type
                 organization/name]} query]
      (when (and vcs-type name)
        `{(:routed-organization {:< :circleci/organization
                                 :organization/vcs-type ~vcs-type
                                 :organization/name ~name})
          ~query}))]

   :routed-entity/project
   [[:org-for-routed-project :organization/project]
    (fn [route-params query]
      `{(:org-for-routed-project
         ~(-> route-params
              (select-keys [:organization/vcs-type :organization/name])
              (assoc :< :circleci/organization)))
        [{(:organization/project
           ~(select-keys route-params [:project/name]))
          ~query}]})]

   :routed-entity/branch
   [[:org-for-routed-project :organization/project :project/branch]
    (fn [route-params query]
      (when (:branch/name route-params)
        `{(:org-for-routed-project
           ~(-> route-params
                (select-keys [:organization/vcs-type :organization/name])
                (assoc :< :circleci/organization)))
          [{(:organization/project
              ~(select-keys route-params [:project/name]))
            [{(:project/branch
                ~(select-keys route-params [:branch/name]))
               ~query}]}]}))]

   :routed-entity/run
   [[:routed-run]
    (fn [{:keys [run/id]} query]
      `{(:routed-run {:< :circleci/run
                      :run/id ~id})
        ~query})]

   :routed-entity/job
   [[:run-for-routed-job :run/job]
    (fn [route-params query]
      (when (:job/name route-params)
        `{(:run-for-routed-job
           ~(-> route-params
                (select-keys [:run/id])
                (assoc :< :circleci/run)))
          [{(:run/job
             ~(select-keys route-params [:job/name]))
            ~query}]}))]

   :routed/page
   [[:routed-page]
    (fn [{:keys [page/number]} query {:keys [page/connection page/count]}]
      (assert number "No page number set in route params.")
      ;; (dec number) because page numbers are 1-indexed.
      (let [offset (* (dec number) count)]
        `{(:routed-page {:< ~connection :connection/offset ~offset :connection/limit ~count})
          ~query}))]})

(def read (bodhi/read-fn
           (-> bodhi/basic-read
               default-db/read
               param-indexing/read
               connection/read
               (query-mapping/read :app/route-params routed-data-query-map)
               aliasing/read
               subpage-read
               remote-filtered-read
               legacy-state-read
               link-traversing/read)))

(defmulti mutate om-next/dispatch)

(defmethod mutate 'run/retry
  [_env _key _params]
  {:remote true})

(defmethod mutate 'run/cancel
  [_env _key _params]
  {:remote true})

(defmethod mutate 'job/approve
  [_env _key _params]
  {:remote true})

(defn- legacy-route?
  "Is this route a Om (now) route?"
  [state route]
  (= route :route/legacy-page))

(defn- maybe-clear-nav-data
  "There is currently a bug with the org-picker navigation whose symptoms are:
  When you are on a Om-next page, and change orgs via the org-picker, Om will navigate
  back to the last Om-now page you viewed.

  This is a temporary fix, and work to remove it will follow immediately."
  [state route]
  (if (legacy-route? state route)
    state
    (-> state
        (assoc-in [:legacy/state :navigation-point] route)
        ;; Clean up the legacy state so it doesn't leak
        ;; from the previous page. This goes away when
        ;; the legacy state dies. In the Om Next world,
        ;; all route data is in :app/route-params, and is
        ;; replaced completely on each route change.
        (update :legacy/state dissoc
                :navigation-data))))

;; Sets the :app/route-params during navigation.
(defmethod mutate 'route-params/set
  [{:keys [state route] :as env} key {:keys [subpage route-params]}]
  {:action (fn []
             (swap! state (fn [state]
                            (-> state
                                (assoc :app/subpage-route subpage
                                       :app/route-params route-params)
                                (maybe-clear-nav-data route))))
             (analytics/track {:event-type :pageview
                               :navigation-point route
                               :subpage :default
                               :properties {:user (get-in @state [:app/current-user :user/login])
                                            :view route
                                            :org (get-in route-params [:organization/name])}}))})

(def parser (compassus/parser {:read read :mutate mutate :route-dispatch false}))
