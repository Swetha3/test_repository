(ns frontend.components.pages.run
  (:require [compassus.core :as compassus]
            [frontend.components.common :as common]
            [frontend.components.pieces.job :as job]
            [frontend.components.pieces.run-row :as run-row]
            [frontend.components.templates.main :as main-template]
            [frontend.utils :refer-macros [component element html]]
            [goog.string :as gstring]
            [om.next :as om-next :refer-macros [defui]]))

(defn job-cards-row
  "A set of cards to layout together"
  [cards]
   (component
     (html
       [:div
        (for [card cards]
          ;; Reuse the card's key. Thus, if each card is built with a unique key,
          ;; each .item will be built with a unique key.
          [:.item (when-let [react-key (and card (.-key card))]
                    {:key react-key})
           card])])))

(defui ^:once Page
  static om-next/IQuery
  (query [this]
    ['{:legacy/state [*]}
     {:app/route-params [:route-params/tab :route-params/container-id]}
     `{:routed-entity/run
       [:run/id
        {:run/project [:project/name
                       {:project/organization [:organization/vcs-type
                                               :organization/name]}]}
        {:run/trigger-info [:trigger-info/branch]}
        {:run/errors [:workflow-error/message]}
        :error/type]}
     `{(:run-for-row {:< :routed-entity/run})
       ~(om-next/get-query run-row/RunRow)}
     `{(:run-for-jobs {:< :routed-entity/run})
       [{:run/jobs ~(om-next/get-query job/Job)}]}
     {:routed-entity/job [:job/name
                          {:job/build [:build/vcs-type
                                       :build/org
                                       :build/repo
                                       :build/number]}]}])
  ;; TODO: Add the correct analytics properties.
  #_analytics/Properties
  #_(properties [this]
      (let [props (om-next/props this)]
        {:user (get-in props [:app/current-user :user/login])
         :view :projects
         :org (get-in props [:app/route-data :route-data/organization :organization/name])}))
  Object
  ;; TODO: Title this page.
  #_(componentDidMount [this]
      (set-page-title! "Projects"))
  (componentWillUpdate [this next-props _next-state]
    (when (= :error/not-found (get-in next-props [:routed-entity/run :error/type]))
      (compassus/set-route! this :route/not-found)))
  (render [this]
    (let [{{project-name :project/name
            {org-name :organization/name
             vcs-type :organization/vcs-type} :project/organization} :run/project
           {branch-name :trigger-info/branch} :run/trigger-info
           id :run/id
           errors :run/errors}
          (:routed-entity/run (om-next/props this))]
      (component
       (main-template/template
        {:app (:legacy/state (om-next/props this))
         :crumbs [{:type :workflows}
                  {:type :org-workflows
                   :username org-name
                   :vcs_type vcs-type}
                  {:type :project-workflows
                   :username org-name
                   :project project-name
                   :vcs_type vcs-type}
                  {:type :branch-workflows
                   :username org-name
                   :project project-name
                   :vcs_type vcs-type
                   :branch branch-name}
                  {:type :workflow-run
                   :run/id id}]
         :main-content
         (element :main-content
                  (let [run (:run-for-row (om-next/props this))
                        jobs (-> this
                                 om-next/props
                                 :run-for-jobs
                                 :run/jobs)]
                    (html
                     [:div
                      ;; We get the :run/id for free in the route params, so even
                      ;; before the run has loaded, we'll have the :run/id here. So
                      ;; dissoc that and see if we have anything else; when we do, we
                      ;; should have enough to render it.
                      (when-not (empty? (dissoc run :run/id))
                        (run-row/run-row run))

                      (if (seq errors)
                        [:div.alert.alert-warning.iconified
                         [:div [:img.alert-icon {:src (common/icon-path "Info-Warning")}]]
                         [:div
                          [:div "We weren't able to start this workflow."]
                          (for [{:keys [workflow-error/message]} errors]
                            [:div message])
                          [:div
                           [:span "For more examples see the "]
                           [:a {:href "/docs/2.0/workflows"}
                            "Workflows documentation"]
                           [:span "."]]]]
                        [:.jobs
                         [:div.jobs-header
                          [:.hr-title
                           [:span (gstring/format "%s jobs in this workflow" (count jobs))]]]
                         (job-cards-row
                          (map job/job jobs))])])))})))))
