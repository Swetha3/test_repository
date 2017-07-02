(ns frontend.test-parser
  (:require [cljs.test :refer-macros [is]]
            [frontend.parser :as parser])
  (:require-macros [devcards.core :as dc :refer [deftest]]))

(deftest subpage-route-data-works
  (let [query [{:app/subpage-route-data {:a-subpage [{:some/key [:key/deeper-key]}]
                                         :another-subpage [{:some/other-key [:other-key/deeper-key]}]}}]
        env-when-at-subpage-route (fn [subpage-route]
                                    {:state (atom {:app/subpage-route subpage-route
                                                   :some/key {:key/deeper-key "some value"}
                                                   :some/other-key {:other-key/deeper-key "some other value"}})})]
    (is (= {:app/subpage-route-data {:some/key {:key/deeper-key "some value"}}}
           (parser/parser (env-when-at-subpage-route :a-subpage) query nil)))
    (is (= {:app/subpage-route-data {:some/other-key {:other-key/deeper-key "some other value"}}}
           (parser/parser (env-when-at-subpage-route :another-subpage) query nil)))))
