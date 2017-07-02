(ns frontend.utils.test-seq
  (:require [cljs.test :refer-macros [is deftest testing use-fixtures]]
            [frontend.utils.seq :as seq-utils]))

(deftest test-dedupe-by-works
  (let [items [{:a 1 :b 1}
               {:a 1 :b 2}
               {:a 2 :b 1}
               {:a 2 :b 2}]]

    (testing "with a collection, returning a sequence"
      ;; Each item is different, so deduping by identity returns all the items.
      (is (= items
             (seq-utils/dedupe-by identity items)))

      ;; Deduping by :a returns only the first with each value of :a.
      (is (= [{:a 1 :b 1}
              {:a 2 :b 1}]
             (seq-utils/dedupe-by :a items)))

      ;; There are no consecutive duplicate :b values, so deduping by :b returns
      ;; all the items.
      (is (= items
             (seq-utils/dedupe-by :b items))))

    (testing "without a collection, returning a transducer"
      ;; Each item is different, so deduping by identity returns all the items.
      (is (= items
             (into [] (seq-utils/dedupe-by identity) items)))

      ;; Deduping by :a returns only the first with each value of :a.
      (is (= [{:a 1 :b 1}
              {:a 2 :b 1}]
             (into [] (seq-utils/dedupe-by :a) items)))

      ;; There are no consecutive duplicate :b values, so deduping by :b returns
      ;; all the items.
      (is (= items
             (into [] (seq-utils/dedupe-by :b) items))))))

(deftest average-of-fn-works
  (testing "we correctly return the sum of a keyfn"
    (is (= 2 (seq-utils/average-of-fn :a [{:a 1} {:a 2} {:a 3}])))))
