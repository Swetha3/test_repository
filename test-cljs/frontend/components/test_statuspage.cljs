(ns frontend.components.test-statuspage
  (:require [frontend.components.statuspage :as sp]
            [cljs.test :refer-macros [deftest is testing run-tests]]))

(deftest finds-the-right-severtity
  (is (= "none" (sp/severity-class {})))
  (is (= "critical" (sp/severity-class {:status {:indicator "critical"}})))
  (is (= "none" (sp/severity-class {:status {:indicator "unexpected-status"}})))
  (is (= "major" (sp/severity-class {:status {:indicator "minor"}
                                        :incidents [{:impact "minor"}
                                                    {:impact "major"}
                                                    {:impact "minor"}]}))))
