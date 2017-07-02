(ns frontend.utils.test-expr-ast
  (:require [clojure.test :refer [deftest testing is]]
            [frontend.utils.expr-ast :as expr-ast]))

(deftest get-works
  (testing "returns `nil` when ast has no children"
    (is (nil? (expr-ast/get {:type :prop :key :foo} :some-key))))
  (testing "returns first matching expr-ast when ast's children contains child with key"
    (let [expr-ast {:key :child-foo :type :prop}]
      (is (= expr-ast
             (expr-ast/get {:key :parent
                                        :type :join
                                        :children [{:type :prop :key :child-bar}
                                                   expr-ast
                                                   (assoc expr-ast
                                                          :type :join
                                                          :children [])]}
                                       (:key expr-ast))))))
  (testing "returns `nil` when ast does not contain child with key"
    (is (nil? (expr-ast/get {:key :parent
                                         :type :join
                                         :children [{:type :prop :key :a-child}
                                                    {:type :prop :key :another-child}]}
                                        :some-child)))))

(deftest get-in-works
  (let [example {:key :grandparent
                 :type :join
                 :children [{:key :parent
                             :type :join
                             :children [{:type :prop :key :child-bar}
                                        {:type :join :key :child-foo :children []}]}]}]
    (testing "returns alternative when thing at key path not found"
      (is (= ::not-found
             (expr-ast/get-in example
                                     [:parent :nonexistent-child]
                                     ::not-found)))
      (is (= ::not-found
             (expr-ast/get-in example
                                     [:parent :child-bar :nonexistent]
                                     ::not-found)))
      (is (= ::not-found
             (expr-ast/get-in example
                                     [:parent :child-foo :nonexistent]
                                     ::not-found)))
      (is (= ::not-found
             (expr-ast/get-in example
                                     [:nonexistent-parent]
                                     ::not-found)))
      (is (= ::not-found
             (expr-ast/get-in example
                                     []
                                     ::not-found))))
    (testing "returns thing when thing at key path exists"
      (is (= {:key :parent
              :type :join
              :children [{:type :prop :key :child-bar}
                         {:type :join :key :child-foo :children []}]}
             (expr-ast/get-in example
                                     [:parent])))
      (is (= {:type :prop, :key :child-bar}
             (expr-ast/get-in example
                                     [:parent :child-bar])))
      (is (= {:type :join, :key :child-foo :children []}
             (expr-ast/get-in example
                              [:parent :child-foo]))))))

(deftest has-children?-works
  (let [example {:key :parent
                 :type :join
                 :children [{:type :prop
                             :key :foo}
                            {:type :prop
                             :key :bar}
                            {:type :join
                             :key :baz
                             :children []}]}]
    (testing "returns true when children's keys matches key set"
      (is (= true
             (expr-ast/has-children? example
                                     #{:foo :bar :baz})))
      (is (= true
             (expr-ast/has-children? (assoc example
                                            :children
                                            [{:type :join
                                              :key :baz
                                              :children []}
                                             {:type :prop
                                              :key :bar}
                                             {:type :prop
                                              :key :foo}])
                                     #{:foo :bar :baz})))
      (is (= true
             (expr-ast/has-children? (update example :children pop)
                                     #{:foo :bar}))))
    (testing "returns false when children's keys do not match key set"
      (is (= false
             (expr-ast/has-children? (update example :children pop)
                                     #{:foo :bar :baz})))
      (is (= false
             (expr-ast/has-children? (update example
                                             :children
                                             (comp pop
                                                   pop))
                                     #{:foo :bar :baz}))))
    (testing "returns false when expression has no children"
      (is (= false
             (expr-ast/has-children? {:key :some-prop
                                      :type :prop}
                                     #{:foo}))))))
