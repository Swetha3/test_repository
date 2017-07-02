(ns frontend.components.pieces.job
  (:require [frontend.components.common :as common]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.status :as status]
            [frontend.datetime :as datetime]
            [frontend.routes :as routes]
            [frontend.utils :refer-macros [component element html]]
            [frontend.utils.legacy :refer [build-legacy]]
            [om.next :as om-next :refer-macros [defui]]))

(defn- status-class [run-status type]
  (case run-status
    (:job-run-status/waiting
     :job-run-status/not-running)
    :status-class/waiting

    :job-run-status/running
    (if (= :job-type/approval type)
      :status-class/on-hold
      :status-class/running)

    :job-run-status/succeeded :status-class/succeeded

    (:job-run-status/failed :job-run-status/timed-out)
    :status-class/failed

    (:job-run-status/canceled
     :job-run-status/not-run)
    :status-class/stopped))

(defui ^:once Requires
  static om-next/IQuery
  (query [this]
    [:job/name])
  Object
  (render [this]
    (component
      (html
       [:div
        [:.heading "Requires"]
        [:ul.requirements
         (for [required-job (om-next/props this)]
           [:li.requirement (:job/name required-job)])]]))))

(def requires (om-next/factory Requires))

(defui ^:once Job
  static om-next/IQuery
  (query [this]
    [:job/id
     :job/status
     :job/type
     :job/started-at
     :job/stopped-at
     :job/name
     {:job/build [:build/vcs-type
                  :build/org
                  :build/repo
                  :build/number]}
     {:job/required-jobs (om-next/get-query Requires)}
     {:job/run [:run/id]}])
  Object
  (render [this]
    (component
      (let [{:keys [job/id
                    job/status
                    job/type
                    job/started-at
                    job/stopped-at
                    job/required-jobs]
             {:keys [build/vcs-type
                     build/org
                     build/repo
                     build/number]
              :as build} :job/build
             job-name :job/name
             {run-id :run/id} :job/run}
            (om-next/props this)]
        (card/full-bleed
         (element :content
           (html
            [:div
             [:.job-card-inner
              [:.body
               [:.status-name
                [:.status (status/icon (status-class status type))]
                (if (nil? build)
                  job-name
                  [:a {:href
                       (routes/v1-build-path vcs-type
                                             org
                                             repo
                                             nil
                                             number)}
                   job-name])]
               (when (= :job-type/approval type)
                 [:.approval
                  (button/button
                   {:kind :primary
                    :disabled? (not= :job-run-status/running status)
                    :on-click #(om-next/transact! this [`(job/approve {:job/id ~id :run/id ~run-id})])}
                   (case status
                     :job-run-status/succeeded "Approved"
                     :job-run-status/failed "Declined"
                     "Approve"))])
               (when (seq required-jobs)
                 [:.requires
                  (requires required-jobs)])]
              [:.job-metadata
               [:.metadata-row
                [:.metadata-item
                 [:i.material-icons "today"]
                 (if started-at
                   [:span {:title (str "Started: " (datetime/full-datetime started-at))}
                    (build-legacy common/updating-duration {:start started-at} {:opts {:formatter datetime/time-ago-abbreviated}})
                    [:span " ago"]]
                   "-")]
                [:.metadata-item
                 [:i.material-icons "timer"]
                 (if stopped-at
                   [:span {:title (str "Duration: " (datetime/as-duration (- stopped-at started-at)))}
                    (build-legacy common/updating-duration {:start started-at
                                                            :stop stopped-at})]
                   "-")]]]]])))))))

(def job (om-next/factory Job {:keyfn :job/id}))
