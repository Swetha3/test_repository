(ns frontend.models.test-plan
  (:require [cljs-time.core :as time]
            [frontend.test-utils :refer (example-plan)]
            [frontend.models.plan :as pm]
            [cljs.test :refer-macros [is deftest testing]]))

(deftest grandfathered-works
  (is (pm/grandfathered? (example-plan :grandfathered)))
  (is (pm/grandfathered? (example-plan :grandfathered :trial)))
  (is (pm/grandfathered? (example-plan :grandfathered :free)))
  (is (pm/grandfathered? (example-plan :grandfathered :free :trial)))
  (is (not (pm/grandfathered? (example-plan :trial))))
  (is (not (pm/grandfathered? (example-plan :free))))
  (is (not (pm/grandfathered? (example-plan :paid))))
  (is (not (pm/grandfathered? (example-plan :big-paid))))
  (is (not (pm/grandfathered? (example-plan :trial :free))))
  (is (not (pm/grandfathered? (example-plan :trial :paid))))
  (is (not (pm/grandfathered? (example-plan :trial :big-paid))))
  (is (not (pm/grandfathered? (example-plan :free :paid))))
  (is (not (pm/grandfathered? (example-plan :free :big-paid))))
  (is (not (pm/grandfathered? (example-plan :trial :free :paid)))))

(deftest osx-trial-days-left-works
  (testing "a plan with no trial end date returns 0"
    (is (= "0 days" (pm/osx-trial-days-left {}))))

  (testing "a plan which has already ended returns 0"
    (is (= "0 days" (pm/osx-trial-days-left {:osx_trial_end_date (time/yesterday)}))))

  (testing "a plan which started today and has 14 days left has 14 days left"
    (is (= "14 days" (pm/osx-trial-days-left {:osx_plan_started_on (time/now)
                                              :osx_trial_end_date (-> 14
                                                                      time/days
                                                                      time/from-now)}))))

  (testing "a plan which started a week ago and has 7 days left has 7 days left"
    (is (= "7 days" (pm/osx-trial-days-left {:osx_plan_started_on (-> 7
                                                                      time/days
                                                                      time/ago)
                                             :osx_trial_end_date (-> 7
                                                                     time/days
                                                                     time/from-now)})))))
