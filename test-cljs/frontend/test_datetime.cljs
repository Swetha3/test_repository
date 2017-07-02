(ns frontend.test-datetime
  (:require [frontend.datetime :as datetime]
            [cljs.test :refer-macros [is deftest testing are]]))

(deftest milli-to-float-duration-works
  (testing "basic"
    (is (= (first (datetime/millis-to-float-duration 100))
           "100.0ms")))
  (testing "unit detection"
    (is (= (first (datetime/millis-to-float-duration 6000))
           "6.0s")))
  (testing "huge millis"
    (is (= (first (datetime/millis-to-float-duration 60000000))
           "16.7h")))
  (testing "tiny millis"
    (is (= (first (datetime/millis-to-float-duration 0.6))
           "0.6ms")))
  (testing "less greater or equal to 1 should stay at smaller unit"
    (is (= (first (datetime/millis-to-float-duration 1000))
           "1.0s")))
  (testing "zero"
    (is (= (first (datetime/millis-to-float-duration 0))
           "0")))
  (testing "accepts options"
    (is (= (first (datetime/millis-to-float-duration 60000000
                                                     {:unit :seconds
                                                      :decimals 0}))
           "60000s")))
  (testing "returns unit-hash"
    (is (= (-> (datetime/millis-to-float-duration 60000000)
               (last)
               (:unit))
           :hours))))


(deftest nice-floor-duration-works
  (testing "basic"
    (let [result (datetime/nice-floor-duration 6600)
          [result-str] (datetime/millis-to-float-duration result)]
      (is (= result-str
             "6.0s")))))

(deftest format-duration-works
  (testing "full units"
    (are [ms formatted] (= (datetime/time-ago ms) formatted)
      1000 "1 second"
      2000 "2 seconds"
      (* 60 1000) "1 minute"
      (* 60 2000) "2 minutes"
      (* 60 60 1000) "1 hour"
      (* 60 60 2000) "2 hours"
      (* 24 60 60 1000) "1 day"
      (* 24 60 60 2000) "2 days"
      (* 30 24 60 60 1000) "1 month"
      (* 30 24 60 60 2000) "2 months"
      (* 12 30 24 60 60 1000) "1 year"
      (* 12 30 24 60 60 2000) "2 years"))
  (testing "abbreviated units"
    (are [ms formatted] (= (datetime/time-ago-abbreviated ms) formatted)
      1000 "1 sec"
      2000 "2 sec"
      (* 60 1000) "1 min"
      (* 60 2000) "2 min"
      (* 60 60 1000) "1 hr"
      (* 60 60 2000) "2 hr"
      (* 24 60 60 1000) "1 day"
      (* 24 60 60 2000) "2 days"
      (* 30 24 60 60 1000) "1 month"
      (* 30 24 60 60 2000) "2 months"
      (* 12 30 24 60 60 1000) "1 year"
      (* 12 30 24 60 60 2000) "2 years")))
