(ns frontend.models.test-build
  (:require [frontend.models.build :as build-model]
            [cljs.test :refer-macros [deftest is testing are]]))

(deftest containers-test
  (testing "should not modify complete containers"
    (let [build {:parallel 2
                 :steps [{:actions [{:index 0 :step 0} {:index 1 :step 0}]}
                         {:actions [{:index 0 :step 1} {:index 1 :step 1}]}]}]
      
      (is (= [{:index 0 :actions [{:index 0 :step 0} {:index 0 :step 1}]}
              {:index 1 :actions [{:index 1 :step 0} {:index 1 :step 1}]}]
             (build-model/containers build)))))
  
  (testing "should add dummy step for containers without some steps"
    (let [build {:parallel 2
                 :steps [{:actions [{:index 0 :step 0} {:index 1 :step 0}]}
                         {:actions [{:index 0 :step 1}]}
                         {:actions [{:index 0 :step 2} {:index 1 :step 2}]}]}]
      
      (is (= [{:index 0 :actions [{:index 0 :step 0} {:index 0 :step 1} {:index 0 :step 2}]}
              {:index 1 :actions [{:index 1 :step 0} (build-model/filtered-action 1 1) {:index 1 :step 2}]}]
             (build-model/containers build)))))

  (testing "should replicate not parallel steps"
    (let [build {:parallel 2
                 :steps [{:actions [{:index 0 :step 0} {:index 1 :step 0}]}
                         {:actions [{:index 0 :step 1 :parallel false}]}
                         {:actions [{:index 0 :step 2} {:index 1 :step 2}]}]}]
      
      (is (= [{:index 0 :actions [{:index 0 :step 0} {:index 0 :step 1 :parallel false} {:index 0 :step 2}]}
              {:index 1 :actions [{:index 1 :step 0} {:index 0 :step 1 :parallel false} {:index 1 :step 2}]}]
             (build-model/containers build)))))

  (testing "should fill skipped steps"
    (let [build {:parallel 2
                 :steps [{:actions [{:index 0 :step 0} {:index 1 :step 0}]}
                         {:actions [{:index 0 :step 2} {:index 1 :step 2}]}]}]
      
      (is (= [{:index 0 :actions [{:index 0 :step 0} (build-model/filtered-action 0 1) {:index 0 :step 2}]}
              {:index 1 :actions [{:index 1 :step 0} (build-model/filtered-action 1 1) {:index 1 :step 2}]}]
             (build-model/containers build))))))
