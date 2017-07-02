(ns frontend.models.test-project
  (:require [frontend.models.project :as project]
            [frontend.test-utils :as test-utils]
            [frontend.config :as config]
            [cljs.test :refer-macros [is deftest testing]]))

(def oss-project
  {:oss true
   :vcs_url "https://github.com/circleci/circle"})

(def private-project
  {:oss false
   :vcs_url "https://github.com/circleci/circle"})

(deftest project-name-works
  (is (= "org/repo" (project/project-name {:vcs_url "https://github.com/org/repo"})))
  (is (= "org/repo" (project/project-name {:vcs_url "https://ghe.github.com/org/repo"}))))

(deftest test-show-build-timing-works
  (with-redefs [config/enterprise? (constantly false)]
    (is (= (project/show-build-timing? oss-project (test-utils/example-plan :free)) true))
    (is (= (project/show-build-timing? private-project (test-utils/example-plan :free)) false))
    (is (= (project/show-build-timing? private-project (test-utils/example-plan :paid)) true)))
    (is (= (project/show-build-timing? private-project (test-utils/example-plan :osx)) true))
    (is (= (project/show-build-timing? private-project (test-utils/example-plan :trial)) true))
    (is (= (project/show-build-timing? private-project (test-utils/example-plan :expired-trial)) false))
  (with-redefs [config/enterprise? (constantly true)]
    (is (= (project/show-build-timing? private-project (test-utils/example-plan :free)) true))))

(deftest test-show-upsell-works
  (with-redefs [config/enterprise? (constantly false)]
    (is (= (project/show-upsell? oss-project (test-utils/example-plan :free)) false))
    (is (= (project/show-upsell? private-project (test-utils/example-plan :free)) true))
    (is (= (project/show-upsell? private-project (test-utils/example-plan :paid)) false)))
    (is (= (project/show-upsell? private-project (test-utils/example-plan :osx)) false))
    (is (= (project/show-upsell? private-project (test-utils/example-plan :trial)) false))
    (is (= (project/show-upsell? private-project (test-utils/example-plan :expired-trial)) true))
  (with-redefs [config/enterprise? (constantly true)]
    (is (= (project/show-upsell? private-project (test-utils/example-plan :free)) false))))

(deftest test-add-show-insights-works
   (with-redefs [config/enterprise? (constantly false)]
     (is (= (project/show-insights? test-utils/example-user-plans-free oss-project) true))
     (is (= (project/show-insights? test-utils/example-user-plans-free private-project) false))
     (is (= (project/show-insights? test-utils/example-user-plans-paid private-project) true))
     (is (= (project/show-insights? test-utils/example-user-plans-piggieback private-project) true))
     (is (= (project/show-insights? test-utils/example-user-plans-trial private-project) true))
     (is (= (project/show-insights? test-utils/example-user-plans-expired-trial private-project) false)))
  (with-redefs [config/enterprise? (constantly true)]
    (is (= (project/show-insights? test-utils/example-user-plans-free private-project) true))))
