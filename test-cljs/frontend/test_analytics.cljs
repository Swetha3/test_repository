(ns frontend.test-analytics
  (:require [bond.james :as bond]
            [cljs.test :refer-macros [is deftest testing]]
            [frontend.analytics :as analytics]
            [om.next :as om-next :refer-macros [defui]]))

(defui GreatGrandparent
  analytics/Properties
  (properties [this]
    {:foo "foo from great-grandparent"
     :bar "bar from great-grandparent"}))

(defui Grandparent)

(defui Parent
  analytics/Properties
  (properties [this]
    {:bar "bar from parent"
     :baz "baz from parent"}))

(defui Child
  analytics/Properties
  (properties [this]
    {:baz "baz from child"
     :qux "qux from child"}))

(deftest event-works
  (let [great-grandparent (GreatGrandparent. #js {:omcljs$parent nil})
        grandparent (Grandparent. #js {:omcljs$parent great-grandparent})
        parent (Parent. #js {:omcljs$parent grandparent})
        child (Child. #js {:omcljs$parent parent})]
    (testing "merges its properties with properties up the parent chain"
      (is (= {:event-type :something-happened
              :properties {:foo "foo from great-grandparent"
                           :bar "bar from great-grandparent"
                           :qux "qux from arg"}}
             (analytics/event great-grandparent {:event-type :something-happened
                                           :properties {:qux "qux from arg"}})))
      (is (= {:event-type :something-happened
              :properties {:foo "foo from great-grandparent"
                           :bar "bar from parent"
                           :baz "baz from parent"
                           :qux "qux from arg"}}
             (analytics/event parent {:event-type :something-happened
                                      :properties {:qux "qux from arg"}})))
      (is (= {:event-type :something-happened
              :properties {:foo "foo from great-grandparent"
                           :bar "bar from parent"
                           :baz "baz from child"
                           :qux "qux from arg"}}
             (analytics/event child {:event-type :something-happened
                                     :properties {:qux "qux from arg"}}))))
    (testing "adds :properties to the event if none is given"
      (is (= {:event-type :something-happened
              :properties {:foo "foo from great-grandparent"
                           :bar "bar from parent"
                           :baz "baz from child"
                           :qux "qux from child"}}
             (analytics/event child {:event-type :something-happened}))))))
