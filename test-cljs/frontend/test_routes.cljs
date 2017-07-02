(ns frontend.test-routes
  (:require [clojure.string :as string]
            [om.core :as om :include-macros true]
            [om.dom]
            [cljs.test :refer-macros [is deftest testing]]
            [secretary.core :as secretary]
            [cljs.core.async :as async]
            [frontend.routes :as routes]))

(deftest parse-uri-works
  (testing "a basic uri"
    (is (= ["/page/subpage" nil nil] (routes/parse-uri "/page/subpage")))
    (is (= ["/page" nil nil] (routes/parse-uri "/page"))))

  (testing "a uri with query parameters"
    (is (= ["/page" "abc=123&xyz=789" nil] (routes/parse-uri "/page?abc=123&xyz=789"))))

  (testing "a uri with an anchor"
    (is (= ["/page" nil "here"] (routes/parse-uri "/page#here")))
    (is (= ["/page" nil "here?partOf=anchor"] (routes/parse-uri "/page#here?partOf=anchor"))))

  (testing "a uri with both anchor and query parameters"
    (is (= ["/page" "abc=123" "here"] (routes/parse-uri "/page?abc=123#here")))))

(deftest v1-project-branch-workflows-path-works
  (testing "url-encodes branch names"
    (is (= "/gh/circleci/workflows/frontend-private/tree/frank%2Fbranch-runs-list"
           (routes/v1-project-branch-workflows-path :github
                                                    "circleci"
                                                    "frontend-private"
                                                    "frank/branch-runs-list")))))
