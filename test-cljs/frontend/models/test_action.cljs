(ns frontend.models.test-action
  (:require [cljs.test :refer-macros [is deftest testing use-fixtures]]
            [frontend.models.action :as action]))

(deftest show-by-default?-works
  (testing "a non-successful action is shown by default"
    (is (action/show-by-default? {:status "not success"})))

  (testing "a successful action is not"
    (is (not (action/show-by-default? {:status "success"}))))

  (testing "a currently running action (no :status) is shown by default"
    (is (action/show-by-default? {})))

  (testing "an action with a seq of messages is shown by default"
    (is (action/show-by-default? {:status "success"
                                  :messages (list "here" "is" "some" "stuff")}))))

(deftest show-output?-works
  ;; (:show-output action) is set by a user clicking the expand/collapse arrow on the build step.
  ;; It is null if it has not explicitly been set by user action.
  (testing "When (:show-output action) is null, we use show-by-default? to determine whether or not to show the action."
    (with-redefs [action/show-by-default? (constantly true)]
      (is (action/show-output? {})))
    (with-redefs [action/show-by-default? (constantly false)]
      (is (not (action/show-output? {})))))

  (testing "We use :show-output if it is set"
    (with-redefs [action/show-by-default? (constantly false)]
      (is (action/show-output? {:show-output true})))
    (with-redefs [action/show-by-default? (constantly true)]
      (is (not (action/show-output? {:show-output false}))))))

(deftest visible-on-container-index?-works
  (testing "an action is visible on this container-index if they are the same"
    (with-redefs [action/ubiquitous-action? (constantly false)]
      (is (action/visible-on-container-index? {:index 15} 15))
      (is (not (action/visible-on-container-index? {:index 15} 5)))))

  (testing "a ubiquitous-action? is visible on every container"
     (with-redefs [action/ubiquitous-action? (constantly true)]
      (is (action/visible-on-container-index? {:index 15} 15))
      (is (action/visible-on-container-index? {:index 15} 5)))))

(deftest visible?-works
  (let [test-fn (fn [visible? show-output? visible-on-container-index?]
                  (with-redefs [action/visible-on-container-index? (constantly visible-on-container-index?)
                                action/show-output? (constantly show-output?)]
                    (is (= visible? (action/visible? {} 0)))))]

  (testing "an action is visible when show-output? and visible-on-container-index?"
    (test-fn true true true))

  (testing "an action is not visible if we do not show-output?"
    (test-fn false false true))

   (testing "an action is not visible if the action is not visible-on-container-index?"
     (test-fn false true false))))

(deftest visible-with-output?-works
  (testing "an action is visible-with-output if it :has_output and is visible?"
    (with-redefs [action/visible? (constantly true)]
      (is (action/visible-with-output? {:has_output true} 0))))

  (testing "an action is not visible-with-output if it doesnt :has_output"
    (with-redefs [action/visible? (constantly true)]
      (is (not (action/visible-with-output? {:has_output false} 0)))))

  (testing "an action is not visible-with-output if it is not visible?"
    (with-redefs [action/visible? (constantly false)]
      (is (not (action/visible-with-output? {:has_output true} 0))))))
