(ns frontend.models.test-feature
  (:require [bond.james :as bond :include-macros true]
            [cljs.test :refer-macros [is deftest testing]]
            [frontend.models.feature :as feature]))

(deftest enabled?-works
  (is (not (feature/enabled? :foo)))
  (feature/enable-in-cookie :foo)
  (is (feature/enabled? :foo))
  (feature/disable-in-cookie :foo)
  (is (not (feature/enabled? :foo)))

  ;; test query string overrides
  (with-redefs [feature/set-in-query-string? (constantly true)
                feature/enabled-in-query-string? (constantly true)]
    ;; cookie set to false, query set to true
    (is (feature/enabled? :foo))
    (feature/enable-in-cookie :foo)
    (with-redefs [feature/enabled-in-query-string? (constantly false)]
      ;; cookie set to true, query set to false
      (is (not (feature/enabled? :foo)))))
  (feature/disable-in-cookie :foo))


(deftest ab-test-treatments-works
  (bond/with-stub [[feature/enabled? (fn [feature]
                                       (= :x feature))]]

    (is (= {:x :x-yes :y :y-no}
           (feature/ab-test-treatments {:x {true :x-yes
                                            false :x-no}
                                        :y {true :y-yes
                                            false :y-no}})))))
