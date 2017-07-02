(ns frontend.models.build
  (:require [frontend.datetime :as datetime]
            [frontend.models.project :as proj]
            [frontend.state :as state]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils.vcs :as vcs]
            [frontend.utils.github :as github]
            [goog.string :as gstring]
            goog.string.format))

(defn id [build]
  (:build_url build))

(defn owners [build]
  (:owners build))

(defn num [build]
  (:build_num build))

(defn vcs-url [build]
  (:vcs_url build))

;; TODO paths should use secretary
(defn path-for [project build]
  (str "/" (-> project proj/vcs-type vcs/->short-vcs) "/" (proj/project-name project) "/" (num build)))

(defn github-revision [build]
  (when (:vcs_revision build)
    (subs (:vcs_revision build) 0 7)))

(defn commit-url [build]
  (when (:vcs_revision build)
    (gstring/format
      (case (-> build vcs-url vcs-url/vcs-type)
       "github" "%s/commit/%s"
       "bitbucket" "%s/commits/%s")
      (vcs-url build) (:vcs_revision build))))

(defn vcs-ref-name [build]
  (cond
    (:branch build) (.replace (:branch build) (js/RegExp. "^remotes\\/origin\\/") "")
    (:vcs_tag build) (:vcs_tag build)
    :else "unknown"))

(defn author [build]
  (or (:author_name build) (:author_email build)))

(defn committer [build]
  (or (:committer_name build) (:committer_email build)))

(defn author-isnt-committer [{:keys [committer_email author_email committer_name author_name] :as build}]
  (or (not= committer_email author_email)
      (not= committer_name author_name)))

(defn ui-user [build]
  (let [user (:user build)]
    (cond
      (not-empty (:name user)) (:name user)
      ;; Currently, if there's no user associated with a build, we get a "user",
      ;; but its login is "none". Until we have a better representation,
      ;; recognize that as a sentinel value.
      (not= "none" (:login user)) (:login user))))

(defn running? [build]
  (and (:start_time build)
       (not (:stop_time build))))

(defn duration [{:keys [start_time stop_time] :as build}]
  (let [start-time (when start_time (js/Date.parse start_time))
        stop-time (when stop_time (js/Date.parse stop_time))]
    (cond (= "canceled" (:status build)) "canceled"
          (and start-time stop-time) (datetime/as-duration (- stop-time start-time))
          start-time (datetime/as-duration (- (.getTime (js/Date.)) start-time))
          :else nil)))

(defn pretty-start-time [build]
  (str (datetime/time-ago (js/Date.parse (:start_time build)))
       " ago"))

(defn finished? [build]
  (= (:lifecycle build) "finished"))

(defn in-usage-queue? [build]
  (and (not (finished? build))
       (not (:queued_at build))))

(defn in-run-queue? [build]
  (and (not (finished? build))
       (:queued_at build)
       (not (:start_time build))))

(defn run-queued-time [{:keys [start_time stop_time queued_at] :as build}]
  (cond (and start_time queued_at) (- (js/Date.parse start_time) (js/Date.parse queued_at))
        ;; canceled before left queue
        (and queued_at stop_time) (- (js/Date.parse stop_time) (js/Date.parse queued_at))
        queued_at (- (.getTime (js/Date.)) (js/Date.parse queued_at))
        :else 0))

(defn usage-queued-time [{:keys [stop_time queued_at usage_queued_at] :as build}]
  (cond (and usage_queued_at queued_at) (- (js/Date.parse queued_at) (js/Date.parse usage_queued_at))
        ;; canceled before left queue
        (and usage_queued_at stop_time) (- (js/Date.parse stop_time) (js/Date.parse usage_queued_at))
        usage_queued_at (- (.getTime (js/Date.)) (js/Date.parse usage_queued_at))
        :else 0))

(defn queued-time [build]
  (+ (usage-queued-time build)
     (run-queued-time build)))

(defn queued-time-summary [build]
  (if (> 0 (run-queued-time build))
    (gstring/format "%s waiting + %s in queue"
                    (datetime/as-duration (usage-queued-time build))
                    (datetime/as-duration (run-queued-time build)))
    (gstring/format "%s waiting for builds to finish"
                    (datetime/as-duration (usage-queued-time build)))))

(defn build-status
  "Calculates the build status for a build."
  [build]
  (case (:status build)
    "canceled" :build-status/canceled
    "failed" :build-status/failed
    "fixed" :build-status/fixed
    "killed" :build-status/killed
    "no_tests" :build-status/no-tests
    "not_run" (case (:dont_build build)
                ("ci-skip" "branch-blacklisted" "branch-not-whitelisted") :build-status/skipped
                ("org-not-paid" "user-not-paid") :build-status/not-paid
                :build-status/not-run)
    "not_running" :build-status/not-running
    "queued" :build-status/queued
    "retried" :build-status/retried
    "running" :build-status/running
    "scheduled" :build-status/scheduled
    "success" :build-status/success
    "timedout" :build-status/timed-out
    "infrastructure_fail" :build-status/infrastructure-fail
    ;; This list should be exhaustive. If we get an unknown status, default to
    ;; something that'll look okay.
    (do
      (js/console.error "Unexpected build :status" (pr-str (:status build)))
      :build-status/not-run)))

(defn status-class
  "Maps a build status to a status class."
  [build-status]
  (case build-status
    (:build-status/failed
     :build-status/timed-out
     :build-status/no-tests)
    :status-class/failed

    (:build-status/success
     :build-status/fixed)
    :status-class/succeeded

    :build-status/running :status-class/running

    (:build-status/not-running
     :build-status/queued
     :build-status/scheduled)
    :status-class/waiting

    (:build-status/infrastructure-fail
     :build-status/killed
     :build-status/not-run
     :build-status/retried
     :build-status/canceled
     :build-status/not-paid
     :build-status/skipped)
    :status-class/stopped))

(defn favicon-color [build]
  (cond (#{"failed" "timedout" "no_tests"} (:status build)) "red"
        (#{"infrastructure_fail" "killed" "not_run"} (:status build)) "orange"
        (= "success" (:outcome build)) "green"
        (= "running" (:status build)) "blue"
        (#{"queued" "not_running" "scheduled" "retried"} (:status build)) "grey"
        ;; undefined is the default dark blue
        :else "undefined"))

(defn can-cancel? [build]
  (and (not= "canceled" (:status build))
       (#{"not_running" "running" "queued" "scheduled"} (:lifecycle build))))

(defn has-pull-requests? [build]
  (boolean (seq (:pull_requests build))))

(defn is-latest-head-commit? [build]
  (= (:vcs_revision build) (-> build
                               :pull_requests
                               last
                               :head_sha)))

(defn pull-request-numbers [build]
  (map (fn [pr]
         (-> pr
             :url
             github/pull-request-number)) 
       (:pull_requests build)))

(defn current-user-ssh?
  "Whether the given user has SSH access to the build"
  [build user]
  (let [cmp (fn [m] (select-keys m [:type :external_id]))
        identities (->> user
                        :identities
                        vals
                        (map cmp)
                        (into #{}))
        build-identities (->> build
                              :ssh_users
                              (map cmp))]
    (or (some identities build-identities)
        (->> build :ssh_users (map :github_id) (some #{(:github_id user)})))))

(defn someone-else-ssh?
  "Whether a user other than the given one has SSH access to the build"
  [build user]
  (not (or (empty? (:ssh_users build))
           (current-user-ssh? build user))))

(defn ssh-enabled-now?
  "Whether anyone can currently SSH into the build"
  [build]
  (and (seq (:ssh_users build))
       (:node build)
       (or (running? build)
           (every? :ssh_enabled (:node build)))))

(defn display-build-invite [build]
  (:is_first_green_build build))

(defn config-errors [build]
  (-> build :circle_yml :errors seq))

(defn config-errors? [build]
  (-> build
      config-errors
      boolean))

(defn filtered-action
  [index step]
  {:index index
   :step step
   :status "running"
   :filler-action true
   :parallel true})

(defn fill-steps
  "Canceled builds can have missing intermediate steps"
  [build]
  (let [parallel (or (:parallel build) 1)
        last-step-index (-> build :steps last :actions first :step)]
    (if (= last-step-index (dec (count (:steps build))))
      build
      (let [step-by-step-index (reduce (fn [step-by-step step]
                                         (assoc step-by-step
                                           (-> step :actions first :step) step))
                                       {} (:steps build))]
        (update-in build [:steps] (fn [steps]
                                    (vec (map (fn [i]
                                                (or (get step-by-step-index i)
                                                    {:actions [(filtered-action 0 i)]}))
                                              (range (inc last-step-index))))))))))

(defn containers [build]
  (let [steps (-> build fill-steps :steps)
        parallel (:parallel build)
        actions (reduce (fn [groups {:keys [actions]}]
                          (map-indexed (fn [i group]
                                         (conj group (or (first (filter #(= i (:index %)) actions))
                                                         (first (filter #(= false (:parallel %)) actions))
                                                         (filtered-action i (-> actions first :step)))))
                                       groups))
                        (repeat (or parallel 1) []) steps)]
    (map-indexed (fn [i actions] {:actions actions
                                  :index i})
                 actions)))

(defn fill-containers
  "Actions can arrive out of order, but we need to maintain the indices in the
  containers array and actions array for the given container so that we can
  find the action on updates."
  [state container-index action-index]
  (-> state
      (update-in state/containers-path
                 (fnil identity (vec (map (fn [i] {:index i})
                                          (range (:parallel (get-in state state/build-path)))))))
      (update-in (state/actions-path container-index)
                 (fn [actions]
                   (if-not (> action-index (count actions))
                     actions
                     (vec (concat actions
                                  (map (fn [i]
                                         {:index action-index
                                          :step i
                                          :status "running"
                                          :filler-action true})
                                       (range (count actions) action-index)))))))))

(defn owner? [build user]
  (->> build (owners) (some #{(:login user)})))

(defn dependency-cache? [build]
  (not (:no_dependency_cache build)))

(defn build-args [build]
  {:build-id  (id build)
   :vcs-url   (vcs-url build)
   :build-num (num build)
   :ref-name (vcs-ref-name build)
   :reponame (:reponame build)})

(defn new-pull-request-url [build]
  (when-let [vcs-url (vcs-url build)]
    (case (vcs-url/vcs-type vcs-url)
      "github"
      (str (:vcs_url build) "/compare/" (:branch build))

      "bitbucket"
      (letfn [(branch-url [build branch]
                (-> build :vcs_url vcs-url/project-name (str "::" branch)))]
        (str (:vcs_url build)
             "/pull-requests/new?"
             "source=" (branch-url build (:branch build))
             "&dest=" (branch-url build "master"))))))
