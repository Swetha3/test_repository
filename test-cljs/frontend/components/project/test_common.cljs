(ns frontend.components.project.test-common
  (:require [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            [frontend.test-utils :as test-utils]
            [frontend.components.project.common :as project-common]
            [frontend.utils.docs :as doc-utils]
            [frontend.stefon :as stefon]
            [frontend.config :as config]
            [goog.dom]
            [goog.string :as gstring]
            [goog.string.format]
            [om.core :as om :include-macros true]
            [cljs.test :refer-macros [is deftest testing]]))

(defn ->iso8601 [time]
  (time-format/unparse (time-format/formatters :date-time) time))

(deftest test-show-trial-notice?
  (let [projects {:org-oss      {:username "circleci" :reponame "mongofinil"    :feature_flags {:oss true}}
                  :org-private  {:username "circleci" :reponame "circle"        :feature_flags {}} ;; no feature should be equivalent to false
                  :user-oss     {:username "esnyder"  :reponame "dummy-oss"     :feature_flags {:oss true}}
                  :user-private {:username "esnyder"  :reponame "dummy-private" :feature_flags {:oss false}}}
        plans {:expired   {:trial {:template {:type "trial"}} :trial_end (->iso8601 (time/minus (time/now) (time/days 1)))
                           :msg "The %s project is covered by %s's plan, whose trial ended 1 day ago. "
                           :org_name "some-org"}
               :long      {:trial {:template {:type "trial"}} :trial_end (->iso8601 (time/plus (time/now) (time/days 100)))
                           :msg "The %s project is covered by %s's trial which expires in 100 days! "
                           :org_name "longnow"}
               :eleven-day {:trial {:template {:type "trial"}} :trial_end (->iso8601 (time/plus (time/now) (time/days 11)))
                            :msg "The %s project is covered by %s's trial, enjoy! (or check out "
                            :org_name "other-org"}
               :eight-day {:trial {:template {:type "trial"}} :trial_end (->iso8601 (time/plus (time/now) (time/days 8)))
                           :msg "The %s project is covered by %s's trial which has 8 days left. "
                           :org_name "plan-org"}
               :six-day   {:trial {:template {:type "trial"}} :trial_end (->iso8601 (time/plus (time/now) (time/days 6)))
                           :msg "The %s project is covered by %s's trial which has 6 days left. "
                           :org_name ""}
               :three-day {:trial {:template {:type "trial"}} :trial_end (->iso8601 (time/plus (time/now) (time/days 3)))
                           :msg "The %s project is covered by %s's trial which expires in 3 days! "
                           :link "Add a plan to keep running builds."
                           :org_name nil}
               :paid      {:paid {:template {:type "paid"}}  :trial_end nil :org_name "plan-org"}}
        dismissed false]

    (testing "1: never show trial notice for paid, regardless of oss status of project"
      (doseq [project (vals projects)]
        (is (not (project-common/show-trial-notice? project (:paid plans) dismissed )))))

    (testing "2: never show trial notice for oss, regardless of plan"
      (doseq [plan (vals plans)]
        (do
          (is (not (project-common/show-trial-notice? (:user-oss projects) plan dismissed)))
          (is (not (project-common/show-trial-notice? (:org-oss projects) plan dismissed))))))

    (testing "3: show for private projects on expired, eleven, eight, six and 3 day"
      (doseq [project-key [:org-private :user-private]
              plan-key [:expired :eleven-day :eight-day :six-day :three-day]
              :let [project (project-key projects)
                    plan (plan-key plans)]]
        (testing (str "with " project-key " and " plan-key)
          (do
            (is (project-common/show-trial-notice? project plan dismissed))
            (is (not (project-common/show-trial-notice? project plan true)))))))

    (testing "4: do not show for private projects on long or paid plans"
      (doseq [project-key [:org-private :user-private]
              plan-key [:long :paid]
              :let [project (project-key projects)
                    plan (plan-key plans)]]
        (testing (str "with " project-key " and " plan-key)
          (is (not (project-common/show-trial-notice? project plan dismissed))))))))
