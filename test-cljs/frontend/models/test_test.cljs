(ns frontend.models.test-test
  (:require [cljs.test :refer-macros [is deftest testing]]
            [frontend.models.test :as test-model]))

(def slowest-test {:run_time 100000})

(def list-of-tests
  (conj [{:run_time 2}
         {:run_time 37}
         {:run_time 47}
         {:run_time 3}]
        slowest-test))

(deftest by-time-descending-works
  (testing "by-time-descending returns tests in descending order by time"
    (is (= (sort-by :run_time > list-of-tests)
           (test-model/by-time-descending list-of-tests))))

  (testing "by-time-descending removes when run_time is not a number"
    (is (= (set list-of-tests)
           (->> {:run_time "256"}
                (conj list-of-tests)
                (test-model/by-time-descending)
                (set))))))

(deftest slowest-n-tests-works
  (testing "given an empty seq, returns an empty seq"
    (let [slowest-tests (test-model/slowest-n-tests 100 [])]
      (is (empty? slowest-tests))
      (is (not (nil? slowest-tests)))))

  (testing "we return slowest tests in descending order"
    (is (= (test-model/by-time-descending list-of-tests)
           (test-model/slowest-n-tests 1000 list-of-tests))))

  (testing "we return the slowest tests"
    (is (= (take 2 (test-model/by-time-descending list-of-tests))
           (test-model/slowest-n-tests 2 list-of-tests)))))

(deftest slowest-test-works
  (testing "we return nil when no tests"
    (is (nil? (test-model/slowest-test []))))

  (testing "we return the slowest test"
    (is (= slowest-test (test-model/slowest-test list-of-tests)))))
