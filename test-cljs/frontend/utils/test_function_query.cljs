(ns frontend.utils.test-function-query
  (:require [cljs.test :refer-macros [is deftest testing]]
            [frontend.utils.function-query :as fq :include-macros true]
            [frontend.utils.test-function-query.pet :as pet]))

;; A simple function query.
(defn age
  {::fq/queries {:person [:person/date-of-birth]}}
  [person]
  (- (.getFullYear (js/Date.))
     (.getFullYear (:person/date-of-birth person))))

;; A function query which composes other queries.
(defn age-difference
  {::fq/queries {:person-1 (fq/get age :person)
                 :person-2 (fq/get age :person)}}
  [person-1 person-2]
  (- (age person-1)
     (age person-2)))

;; A function query which merges two queries.
(defn age-difference-from-parent
  {::fq/queries {:child (fq/merge [{:person/parent (fq/get age-difference :person-1)}]
                                  (fq/get age-difference :person-2))}}
  [child]
  (age-difference (:person/parent child) child))

(defn has-same-favorite-color-as-parent?
  {::fq/queries {:child [{:person/parent [:person/favorite-color]}
                         :person/favorite-color]}}
  [child]
  (= (:person/favorite-color (:person/parent child))
     (:person/favorite-color child)))

;; A function which merges two queries with a key in common.
(defn satisfies-oddly-specific-criteria?
  {::fq/queries {:person (fq/merge (fq/get has-same-favorite-color-as-parent? :child)
                                   (fq/get age-difference-from-parent :child))}}
  [person]
  (and (has-same-favorite-color-as-parent? person)
       (< 25 (age-difference-from-parent person))))

;; A function which gets queries from other namespaces.
(defn has-satisfactory-pet?
  {::fq/queries {:person [{:person/pet (fq/get pet/satisfactory? :pet)}]}}
  [person]
  (pet/satisfactory? (:person/pet person)))

;; A function with a quoted query
(defn everything-about
  {::fq/queries {:person '[*]}}
  [person]
  person)


(deftest get-and-merge-work
  (testing "A simple function query."
    (is (= [:person/date-of-birth]
           (fq/get age :person))))

  (testing "A function query which composes other queries."
    (is (= [:person/date-of-birth]
           (fq/get age-difference :person-1)))
    (is (= [:person/date-of-birth]
           (fq/get age-difference :person-2))))

  (testing "A function query which merges two queries."
    (is (= [{:person/parent [:person/date-of-birth]}
            :person/date-of-birth]
           (fq/get age-difference-from-parent :child))))

  (testing "A function which merges two queries with a key in common."
    (is (= [{:person/parent [:person/favorite-color
                             :person/date-of-birth]}
            :person/favorite-color
            :person/date-of-birth]
           (fq/get satisfies-oddly-specific-criteria? :person))))

  (testing "A function which gets queries from other namespaces."
    (is (= [{:person/pet [:pet/cuteness
                          :pet/fuzz]}]
           (fq/get has-satisfactory-pet? :person))))

  (testing "A function with a quoted query"
    (is (= '[*]
           (fq/get everything-about :person)))))
