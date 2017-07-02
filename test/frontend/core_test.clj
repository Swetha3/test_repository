(ns frontend.core-test
  (:require [clojure.test :refer :all]
            [frontend.core :refer :all]))

(deftest a-test
  (testing "IGNOREME, I pass."
    (is (= 1 1))))

(deftest backend-lookup-works
  (is (= {:host "dev.circlehost:8080" :proto "http"}
         (backend-lookup {:server-name "dev.circlehost"})))
  (is (= {:host "circleci.com" :proto "https"}
         (backend-lookup {:server-name "prod.circlehost"})))


;; generic
  (is (= {:host "staging.circleci.com" :proto "https"}
         (backend-lookup {:server-name "staging.circlehost"})))
  (is (= {:host "foo.circleci.com" :proto "https"}
         (backend-lookup {:server-name "foo.circlehost"}))))
