(ns frontend.test-elevio
  (:require [cljs.test :refer-macros [is deftest testing]]
            [frontend.elevio :as elevio]))

(deftest flatten-traits-works
  (let [test-map {:a {:b {:c "d"}
                      :d "e"}
                  :f {:h {:i "g"}
                      :j "k"}}
        expected-output {:a-b-c "d"
                         :a-d "e"
                         :f-h-i "g"
                         :f-j "k"}]
    (is (= (elevio/flatten-traits test-map)
           expected-output))))
