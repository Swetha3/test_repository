(ns frontend.test-utils
  (:require [clojure.string :as string]
            [cljs.test :refer-macros [is]]
            [om.core :as om :include-macros true]
            [om.dom]
            [goog.dom]
            [secretary.core :as secretary]
            [cljs.core.async :as async]
            [frontend.routes :as routes]
            [cljs-time.core :as time]
            [cljs-time.format :as time-format]))

(def test-utils js/React.addons.TestUtils)

(defn is-re [regex string]
  (is (re-find regex string)))

(defn simulate [key component-or-node & [event-data]]
  (let [simulate-obj (.-Simulate test-utils)]
    (.call (aget simulate-obj (name key)) simulate-obj component-or-node event-data)))

(defn render-into-document [component]
  (. test-utils (renderIntoDocument component)))

(defn render-to-string
  "Much like ReactDOMServer.renderToString, but without needing ReactDOMServer."
  [component]
  (let [div (goog.dom/createElement "div")]
    (om.dom/render component div)
    (.-innerText div)))

(def ^:dynamic *nav-ch*)

;;(defn render-into-document [ommish app-state]
;;  (om/build ommish app-state))

;; (defn mock-component)
;; (defn is-component-of-type)
;; (defn is-dom-component)
;; (defn is-composite-component)
;; (defn is-composite-component-with-type)
;; (defn is-text-component)
;; (defn find-all-in-rendered-tree)
;; (defn scry-rendered-dom-components-with-class)
;; (defn find-rendered-dom-component-with-class)
;; (defn scry-rendered-dom-components-with-tag)
;; (defn find-rendered-dom-component-with-tag)
;; (defn scry-rendered-components-with-type)
;; (defn find-rendered-component-with-type)

(defn example-plan [& keys]
  (as-> {:trial {:trial {:template {:free_containers 6
                                    :id "t1"
                                    :price 0
                                    :type "trial"}}
                 :amount 0
                 :trial_end (->> 5
                                 (time/days)
                                 (time/from-now)
                                 (time-format/unparse (:basic-date-time time-format/formatters)))}
         :expired-trial {:trial {:template {:free_containers 6
                                            :id "t1"
                                            :price 0
                                            :type "trial"}}
                         :amount 0
                         :trial_end (->> 5
                                         (time/days)
                                         (time/ago)
                                         (time-format/unparse (:basic-date-time time-format/formatters)))}
         :free {:free {:template {:id "f1"
                                  :free_containers 1
                                  :type "free"}}
                :amount 0
                :containers 4}
         :paid {:paid {:template {:id "p18"
                                  :free_containers 0
                                  :price 0
                                  :container_cost 50
                                  :type "containers"}}
                :amount (* 4 5000)
                :containers 4}
         :big-paid {:paid {:template {:id "p18"
                                      :free_containers 0
                                      :price 0
                                      :container_cost 50
                                      :type "containers"}}
                    :amount (* 60 5000)
                    :containers 60}
         :grandfathered {:paid {:template {:id "p18"
                                           :free_containers 0
                                           :price 0
                                           :container_cost 50
                                           :type "containers"}}
                         :containers 4
                         :amount 200 ;; $2
                         }
         :osx {:osx {:template {:name "OS X Starter"
                                :id "osx-starter"
                                :max-containers 1
                                :free-containers 0
                                :container-cost 0
                                :price 79
                                :type :osx}}}}
        %
        (map % keys)
        (conj % {:org_name "circleci"
                 :org {:name "circleci"
                       :vcs_type "github"}})
        (apply merge %)))

(def example-piggieback-plan
  (-> (example-plan :paid)
      (dissoc :org_name)
      (assoc :piggieback_orgs ["circleci"]
             :piggieback_org_maps [{:org_name "circleci"
                                    :vcs_type "github"}]
             :org_name "test-org")))

(defn- make-example-user-plans [plans]
  (-> {:org_name "circleci"
       :vcs_type "github"}
      (assoc :plans plans)
      (vector)))

;; The three functions below are to mimic the /api/v1/user/organizations/plans
;; payload for different possible cases.
(def example-user-plans-free (make-example-user-plans [(example-plan :free)]))

(def example-user-plans-paid (make-example-user-plans [(example-plan :paid)]))

(def example-user-plans-osx (make-example-user-plans [(example-plan :osx)]))

(def example-user-plans-trial (make-example-user-plans [(example-plan :trial)]))

(def example-user-plans-expired-trial (make-example-user-plans [(example-plan :expired-trial)]))

(def example-user-plans-piggieback (make-example-user-plans [example-piggieback-plan (example-plan :free)]))

(defn state [{:keys [view user repo org state primary-email]}]
  (merge {:current-user {:login (or user "test-user")
                         :selected_email (or primary-email "email@test.com")}
          :navigation-point (or view :test-view)
          :navigation-data {:repo (or repo "test-view")
                            :org (or org "test-org")}}
         state))

(defn fails-schema-validation
  "Ensure that a fn fails schema validation."
  [f]
  (is (thrown-with-msg? js/Error #"Input to .* does not match schema:" (f))))
