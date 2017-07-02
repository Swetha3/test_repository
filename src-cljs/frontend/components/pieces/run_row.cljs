(ns frontend.components.pieces.run-row
  (:require [cljs-time.coerce :as time-coerce]
            [cljs-time.core :as time]
            [clojure.spec :as s :include-macros true]
            [clojure.string :as string]
            [clojure.test.check.generators :as gen]
            [frontend.analytics :as analytics]
            [frontend.components.common :as common]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.icon :as icon]
            [frontend.datetime :as datetime]
            [frontend.devcards.faker :as faker]
            [frontend.devcards.morphs :as morphs]
            [frontend.models.build :as build-model]
            [frontend.routes :as routes]
            [frontend.timer :as timer]
            [frontend.utils :refer-macros [component element html]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.legacy :refer [build-legacy]]
            [frontend.utils.vcs-url :as vcs-url]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [devcards.core :as dc :refer [defcard]]))

(defn loading-placeholder [width]
  (component (html [:div {:style {:width width}}])))

(defn loading-circle []
  (component (html [:span (icon/simple-circle)])))

(defn- status-class [run-status]
  (case run-status
    :run-status/running :status-class/running
    :run-status/succeeded :status-class/succeeded
    :run-status/failed :status-class/failed
    (:run-status/canceled :run-status/not-run) :status-class/stopped
    :run-status/needs-setup :status-class/setup-needed
    :run-status/on-hold :status-class/on-hold))

(def ^:private cancelable-statuses #{:run-status/not-run
                                     :run-status/running
                                     :run-status/on-hold})

(def ^:private rerunnable-statuses #{:run-status/succeeded
                                     :run-status/failed
                                     :run-status/canceled})

(def ^:private rerunnable-from-start-statuses #{:run-status/failed})

(defui PRs
  Object
  (render [this]
    (let [urls (map :pull-request/url (om-next/props this))]
      (html
       [:span
        (interpose
         ", "
         (for [url urls
               ;; WORKAROUND: We have/had a bug where a PR URL would be reported as nil.
               ;; When that happens, this code blows up the page. To work around that,
               ;; we just skip the PR if its URL is nil.
               :when url]
           [:a {:href url
                :on-click #(analytics/track! this {:event-type :pr-link-clicked})}
            "#"
            (gh-utils/pull-request-number url)]))]))))

(def prs (om-next/factory PRs))

(defn- run-prs
  "A om-next compatible version of
  `frontend.components.builds-table/pull-requests`."
  [pull-requests]
  (when (seq pull-requests)
    (html
     [:span.metadata-item.pull-requests {:title "Pull Requests"}
      (icon/git-pull-request)
      (prs pull-requests)])))

(defn- commit-link
  "Om Next compatible version of `frontend.components.builds-table/commits`."
  [parent-component vcs-type org repo sha]
  (when (and vcs-type org repo sha)
    (let [pretty-sha (build-model/github-revision {:vcs_revision sha})]
      (html
       [:span.metadata-item.revision
        [:i.octicon.octicon-git-commit]
        [:a {:title pretty-sha
             :href (build-model/commit-url {:vcs_revision sha
                                            :vcs_url (vcs-url/vcs-url vcs-type
                                                                      org
                                                                      repo)})
             :on-click #(analytics/track! parent-component
                                          {:event-type :revision-link-clicked})}
         pretty-sha]]))))

(defn- transact-run-mutate [component mutation]
  (om-next/transact!

   ;; We transact on the reconciler, not the component; otherwise the
   ;; component's props render as nil for a moment. This is odd.
   ;;
   ;; It looks like the transaction drops the run from the app state.
   ;; Transacting on the component means the component immediately re-reads, so
   ;; it immediately renders nil. Moments later, the query is read from the
   ;; server again, delivering new data to the app state, and the component
   ;; renders with data again.
   ;;
   ;; When we transact on the reconciler, we simply avoid rendering the first
   ;; time, during the window when the run is missing. Of course, it shouldn't
   ;; be missing in the first place.
   ;;
   ;; tl;dr: there's a bug in here, but it's not clear what, and this works fine
   ;; for now.
   (om-next/get-reconciler component)

   ;; It's not clear why we have to manually transform-reads---Om should do that
   ;; for us if we give a simple keyword---but it doesn't appear to be doing it,
   ;; so we do it. This is another bug we're punting on.
   (om-next/transform-reads
    (om-next/get-reconciler component)
    [mutation
     ;; We queue the entire page to re-read using :compassus.core/route-data.
     ;; Ideally we'd know what specifically to re-run, but there are now
     ;; several keys the new run could show up under. (Aliases also complicate
     ;; this, and solving that problem is not something we want to spend time
     ;; on yet.) Re-reading the entire query here seems like a good idea
     ;; anyhow.
     :compassus.core/route-data])))

(defn- transact-run-retry
  [component run-id jobs]
  (transact-run-mutate component `(run/retry {:run/id ~run-id :run/jobs ~jobs})))

(defn- transact-run-cancel
  [component run-id vcs-type org-name project-name]
  (transact-run-mutate component `(run/cancel {:run/id ~run-id})))


(defui ^:once RunRow
  ;; NOTE: this is commented out until bodhi handles queries for components with idents first
  ;; static om-next/Ident
  ;; (ident [this props]
  ;;   [:run/by-id (:run/id props)])
  static om-next/IQuery
  (query [this]
    [:run/id
     :run/name
     :run/status
     :run/started-at
     :run/stopped-at
     {:run/errors [:workflow-error/message]}
     {:run/jobs [:job/id]}
     {:run/trigger-info [:trigger-info/vcs-revision
                         :trigger-info/subject
                         :trigger-info/body
                         :trigger-info/branch
                         {:trigger-info/pull-requests [:pull-request/url]}]}
     {:run/project [:project/name
                    {:project/organization [:organization/name
                                            :organization/vcs-type]}]}])
  Object
  (render [this]
    (component
      (let [{:keys [::loading?]} (om-next/get-computed this)
            {:keys [run/id
                    run/errors
                    run/status
                    run/started-at
                    run/stopped-at
                    run/trigger-info
                    run/jobs]
             run-name :run/name
             {project-name :project/name
              {org-name :organization/name
               vcs-type :organization/vcs-type} :project/organization} :run/project}
            (om-next/props this)
            {commit-sha :trigger-info/vcs-revision
             commit-body :trigger-info/body
             commit-subject :trigger-info/subject
             pull-requests :trigger-info/pull-requests
             branch :trigger-info/branch} trigger-info
            run-status-class (when-not loading?
                               (status-class status))]

        (card/full-bleed
         (element :content
           (html
            [:div (when loading? {:class "loading"})
             [:.inner-content
              [:.status-and-button
               [:div.status {:class (if loading? "loading" (name run-status-class))}
                [(if id :a.exception :div)
                 (when id {:href (routes/v1-run-path id)
                           :on-click #(analytics/track!
                                       this
                                       {:event-type :run-status-clicked})})
                 [:span.status-icon
                  (if loading?
                    (icon/simple-circle)
                    (case run-status-class
                      :status-class/failed (icon/status-failed)
                      :status-class/setup-needed (icon/status-setup-needed)
                      :status-class/stopped (icon/status-canceled)
                      :status-class/succeeded (icon/status-passed)
                      :status-class/running (icon/status-running)
                      :status-class/on-hold (icon/status-on-hold)))]
                 [:.status-string
                  (when-not loading?
                    (string/replace (name status) #"-" " "))]]]
               (cond
                 loading? [:div.action-button [:svg]]
                 (contains? cancelable-statuses status)
                 [:div.action-button.cancel-button
                  {:on-click (fn [_]
                               (transact-run-cancel this id vcs-type org-name project-name)
                               (analytics/track! this {:event-type :cancel-clicked}))}
                  (icon/status-canceled)
                  [:span "cancel"]]
                 (contains? rerunnable-statuses status)
                 [:div.action-button.rebuild-button.dropdown
                  (icon/rebuild)
                  [:span "Rerun"]
                  [:i.fa.fa-chevron-down.dropdown-toggle {:data-toggle "dropdown"}]
                  [:ul.dropdown-menu.pull-right
                   (when (rerunnable-from-start-statuses status)
                     [:li
                      [:a
                       {:on-click (fn [_event]
                                    (transact-run-retry this id [])
                                    (analytics/track! this
                                                      {:event-type :rerun-clicked
                                                       :properties {:is-from-failed true}}))}
                       "Rerun failed jobs"]])
                   [:li
                    [:a
                     {:on-click (fn [_event]
                                  (transact-run-retry this id jobs)
                                  (analytics/track! this
                                                    {:event-type :rerun-clicked
                                                     :properties {:is-from-failed false}}))}
                     "Rerun from beginning"]]]])]
              [:div.run-info
               [:div.build-info-header
                [:div.contextual-identifier
                 [(if id :a :span)
                  (when id {:href (routes/v1-run-path id)
                            :on-click #(analytics/track! this
                                                         {:event-type :run-link-clicked})})
                  (if loading?
                    (loading-placeholder 300)
                    [:span branch " / " run-name])]]]
               [:div.recent-commit-msg
                [:span.recent-log
                 {:title (when commit-body
                           commit-body)}
                 (if loading?
                   (loading-placeholder 200)
                   (when commit-subject
                     commit-subject))]]]
              [:div.metadata
               [:div.metadata-row.timing
                [:span.metadata-item.recent-time.start-time
                 (if loading?
                   (loading-circle)
                   [:i.material-icons "today"])
                 (if started-at
                   [:span {:title (str "Started: " (datetime/full-datetime started-at))}
                    (build-legacy common/updating-duration {:start started-at} {:opts {:formatter datetime/time-ago-abbreviated}})
                    [:span " ago"]]
                   "-")]
                [:span.metadata-item.recent-time.duration
                 (if loading?
                   (loading-circle)
                   [:i.material-icons "timer"])
                 (if stopped-at
                   [:span {:title (str "Duration: "
                                       (datetime/as-duration (- (.getTime stopped-at)
                                                                (.getTime started-at))))}
                    (build-legacy common/updating-duration {:start started-at
                                                            :stop stopped-at})]
                   "-")]]
               [:div.metadata-row.pull-revision
                (if loading?
                  [:span.metadata-item.pull-requests (loading-circle)]
                  (run-prs pull-requests))
                (if loading?
                  [:span.metadata-item.revision (loading-circle)]
                  (commit-link this
                               vcs-type
                               org-name
                               project-name
                               commit-sha))]]]])))))))

(def run-row (om-next/factory RunRow {:keyfn :run/id}))

(def loading-run-row* (om-next/factory RunRow))
(defn loading-run-row [] (loading-run-row* (om-next/computed {} {::loading? true})))

(dc/do
  (s/def :run/entity (s/and
                      (s/keys :req [:run/id
                                    :run/name
                                    :run/status
                                    :run/started-at
                                    :run/stopped-at
                                    :run/trigger-info
                                    :run/project])
                      (fn [{:keys [:run/status :run/started-at :run/stopped-at]}]
                        (case status
                          (:run-status/running
                           :run-status/not-run
                           :run-status/needs-setup
                           :run-status/on-hold)
                          (and started-at (nil? stopped-at))

                          (:run-status/succeeded
                           :run-status/failed
                           :run-status/canceled)
                          (and started-at stopped-at (< started-at stopped-at))))))

  (s/def :run/trigger-info (s/keys :req [:trigger-info/vcs-revision
                                         :trigger-info/subject
                                         :trigger-info/body
                                         :trigger-info/branch
                                         :trigger-info/pull-requests]))

  (s/def :run/project (s/keys :req [:project/name
                                    :project/organization]))

  (s/def :project/organization (s/keys :req [:organization/name
                                             :organization/vcs-type]))

  (s/def :run/id uuid?)
  (s/def :run/name (s/and string? seq))
  (s/def :run/status #{:run-status/running
                       :run-status/succeeded
                       :run-status/failed
                       :run-status/canceled
                       :run-status/not-run
                       :run-status/needs-setup
                       :run-status/on-hold})
  (s/def :run/started-at (s/nilable inst?))
  (s/def :run/stopped-at (s/nilable inst?))
  (s/def :trigger-info/vcs-revision (s/with-gen
                                      (s/and string? (partial re-matches #"[0-9a-f]{40}"))
                                      #(gen/fmap (fn [n] (.toString n 16))
                                                 (gen/choose 0 (dec (Math/pow 2 160))))))
  (s/def :trigger-info/subject string?)
  (s/def :trigger-info/body string?)
  (s/def :trigger-info/branch (s/and string? seq))
  (s/def :trigger-info/pull-requests (s/every :pull-request/entity))
  (s/def :pull-request/entity (s/keys :req [:pull-request/url]))
  ;; NOTE: :pull-request/url does not currently validate that it's a real URL,
  ;; only that it's a string we can extract a PR number from.
  (s/def :pull-request/url (s/with-gen
                             (s/and string? (partial re-matches #".*/\d+"))
                             #(gen/fmap (fn [[s n]] (str s "/" n)) (gen/tuple gen/string gen/nat))))
  (s/def :project/name (s/and string? seq))
  (s/def :organization/name (s/and string? seq))
  (s/def :organization/vcs-type #{:github :bitbucket})

  (s/fdef prs
    :args (s/cat :prs (s/every :pull-request/entity)))

  (s/fdef run-row
    :args (s/cat :run :run/entity))


  ;; https://stackoverflow.com/questions/25324082/index-of-vector-in-clojurescript/32885837#32885837
  (defn- index-of [coll value]
    (some (fn [[idx item]] (if (= value item) idx))
          (map-indexed vector coll)))

  (defn- gen-inst-in
    "Generates insts in the range from start to end (inclusive)."
    [start end]
    (gen/fmap #(js/Date. %)
              (gen/choose (inst-ms start) (inst-ms end))))

  (defn- gen-time-in-last-day []
    (gen-inst-in (time-coerce/to-date (time/ago (time/days 1)))
                 (js/Date.)))

  (defcard run-rows
    (let [statuses [:run-status/needs-setup
                    :run-status/not-run
                    :run-status/running
                    :run-status/on-hold
                    :run-status/succeeded
                    :run-status/failed
                    :run-status/canceled]]
      (morphs/render #'run-row {:run/name #(faker/snake-case-identifier)
                                ;; ::s/pred targets the case where the value is non-nil.
                                [:run :run/started-at ::s/pred] #(gen-time-in-last-day)
                                [:run :run/stopped-at ::s/pred] #(gen-time-in-last-day)
                                :trigger-info/branch #(faker/snake-case-identifier)
                                :trigger-info/subject #(faker/sentence)
                                :trigger-info/pull-requests #(gen/vector (s/gen :pull-request/entity) 0 2)}
                     (fn [morphs]
                       (binding [om-next/*shared* {:timer-atom (timer/initialize)}]
                         (card/collection (->> morphs
                                               (sort-by #(-> % first :run/trigger-info :trigger-info/pull-requests count))
                                               (sort-by #(->> % first :run/status (index-of statuses)))
                                               (map (partial apply run-row)))))))))

  (defcard prs
    (morphs/render #'prs {[:prs] (gen/vector (s/gen :pull-request/entity) 0 5)}
                   (fn [morphs]
                     (card/collection (->> morphs
                                           (sort-by (comp count first))
                                           (map (partial apply prs)))))))

  (defcard loading-run-row
    (loading-run-row)))
