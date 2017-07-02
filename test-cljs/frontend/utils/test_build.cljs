(ns frontend.utils.test-build
   (:require [cljs.test :refer-macros [is deftest testing use-fixtures]]
             [frontend.utils.build :as build]
             [frontend.models.build :as build-model]))

(deftest test-default-tab-works
  (with-redefs [build-model/config-errors? (constantly false)
                build-model/ssh-enabled-now? (constantly false)
                build-model/in-usage-queue? (constantly false)
                build-model/running? (constantly false)]

    (testing "if there are config-errors, :config"
      (with-redefs [build-model/config-errors? (constantly true)]
        (is (= :config (build/default-tab {} {})))))

    (testing "if the build is ssh-enabled, :ssh-info"
      (with-redefs [build-model/ssh-enabled-now? (constantly true)]
        (is (= :ssh-info (build/default-tab {} {})))))

    (testing "if the build is in-usage-queue with :read-settings, :queue-placeholder"
      (with-redefs [build-model/in-usage-queue? (constantly true)]
        (is (= :queue-placeholder (build/default-tab {} {:read-settings true})))))

    (testing "if the build is in-usage-queue without :read-settings, :tests"
      (with-redefs [build-model/in-usage-queue? (constantly true)]
        (is (= :tests (build/default-tab {} {})))))

    (testing "if the build is running? with :read-settings, :queue-placeholder"
      (with-redefs [build-model/running? (constantly true)]
        (is (= :queue-placeholder (build/default-tab {} {:read-settings true})))))

    (testing "if the build is running? without :read-settings, :queue-placeholder"
      (with-redefs [build-model/running? (constantly true)]
        (is (= :queue-placeholder (build/default-tab {} {:read-settings true})))))

    (testing "default case is to return the :tests tab"
      (is (= :tests (build/default-tab {} {}))))))
