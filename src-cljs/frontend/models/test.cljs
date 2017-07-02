(ns frontend.models.test
  (:require [clojure.string :as string]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [goog.string :as gstring]
            goog.string.format))

(defn source [test]
  (:source_type test))

(defmulti format-test-name source)

(defmethod format-test-name :default [test]
  (->> [[(:name test)] [(:classname test) (:file test)]]
       (map (fn [s] (some #(when-not (string/blank? %) %) s)))
       (filter identity)
       (string/join " - in ")))

(defmethod format-test-name "lein-test" [test]
  (str (:classname test) "/" (:name test)))

(defmethod format-test-name "cucumber" [test]
  (if (string/blank? (:name test))
    (:classname test)
    (:name test)))

(defn pretty-source [source]
  (case source
    "cucumber" "Cucumber"
    "rspec" "RSpec"
    source))

(defn failed? [test]
  (not= "success" (:result test)))

(defn by-time-descending
  "Given a seq of tests, return them sorted by their run_time descending (so
  slowest test to fastest test)."
  [tests]
  (->> tests
       (filter (comp number? :run_time))
       (sort-by :run_time >)))

(defn slowest-n-tests
  "Given a seq of tests and a number n, return the n slowest test by descending
  time."
  [n tests]
  (take n (by-time-descending tests)))

(defn slowest-test
  "Given a seq of tests, return the slowest one."
  [tests]
  (first (slowest-n-tests 1 tests)))
