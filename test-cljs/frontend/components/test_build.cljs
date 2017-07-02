(ns frontend.components.test-build
  (:require [cljs.test :refer-macros [deftest is]]
            [frontend.components.build :as build]
            [frontend.routes :as routes]))

(deftest test-build-path
  (is (= (routes/v1-build-path "github" "circleci" "frontend" nil 123 "usage-queue" 3)
         (build/build-path {:build {:vcs_url "https://github.com/circleci/frontend"
                                    :username "circleci"
                                    :reponame "frontend"
                                    :build_num 123}}
                           :usage-queue
                           3))))
