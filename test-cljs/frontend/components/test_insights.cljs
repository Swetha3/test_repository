(ns frontend.components.test-insights
  (:require [cljs.test :refer-macros [are deftest testing]]
            [frontend.components.insights :as insights]
            [frontend.models.test-project :as test-project]
            [frontend.test-utils :as test-utils]
            [frontend.timer :as timer]
            [goog.dom :as gdom]
            [om.core :as om :include-macros true]))

(def insights-plot-info
  {:top 30
   :right 10
   :bottom 10
   :left 30
   :max-bars 100
   :positive-y% 0.6
   :left-legend-items [{:classname "success"
                        :text "Passed"}
                       {:classname "failed"
                        :text "Failed"}
                       {:classname "canceled"
                        :text "Canceled"}]
   :right-legend-items [{:classname "queue"
                         :text "Queue time"}]
   :legend-info {:top 22
                 :square-size 10
                 :item-width 80
                 :item-height 14   ; assume font is 14px
                 :spacing 4}})

(def test-projects-data
  [{:reponame "test repo"
    :username "foo user"
    :default_branch "master"
    :show-insights? true
    :recent-builds {"master" [
                              {
                               :committer_name "Mr. M",
                               :usage_queued_at "2015-09-14T04:25:05.652Z",
                               :branch "master",
                               :outcome "failed",
                               :body "Enterprise admins",
                               :start_time "2015-09-14T04:24:59.767Z",
                               :lifecycle "finished",
                               :stop_time "2015-09-14T04:33:58.898Z",
                               :subject "Merge pull request #5052 from circleci/enterprise-odds-and-ends",
                               :vcs_url "https://github.com/circleci/circle",
                               :vcs_tag nil,
                               :build_num 129611,
                               :author_date "2015-09-14T00:24:57-04:00",
                               :status "failed",
                               :build_time_millis 539131,
                               :queued_at "2015-09-14T04:24:59.507Z",
                               :vcs_revision "3bb775e8006351380fe3a025ab87c2cc556f4ff5",
                               :committer_date "2015-09-14T00:24:57-04:00",
                               :reponame "circle",
                               :build_url "https://circleci.com/gh/circleci/circle/129611",
                               :username "circleci",
                               :dont_build nil
                               },
                              {
                               :vcs_url "https://github.com/circleci/circle",
                               :vcs_tag nil,
                               :author_date "2015-09-12T23:27:08-04:00",
                               :build_num 129596,
                               :build_time_millis 702991,
                               :status "success",
                               :queued_at "2015-09-13T03:28:13.057Z",
                               :committer_date "2015-09-12T23:27:08-04:00",
                               :vcs_revision "f18aa5183b93cd0c8756cf00c51d40f51402b5b0",
                               :reponame "circle",
                               :build_url "https://circleci.com/gh/circleci/circle/129596",
                               :dont_build nil,
                               :username "circleci",
                               :usage_queued_at "2015-09-13T03:28:16.801Z",
                               :branch "master",
                               :outcome "success",
                               :body "Have an up-check for monitoring purposes",
                               :stop_time "2015-09-13T03:39:56.305Z",
                               :start_time "2015-09-13T03:28:13.314Z",
                               :lifecycle "finished",
                               :subject "Merge pull request #5051 from circleci/nginx-upcheck"}]}}])

(deftest can-render-insights-cards
  (testing "Simple render of cards."
    (let [test-node (gdom/createDom "div")]
      (om/root insights/cards
               {:projects [(insights/decorate-project insights-plot-info test-utils/example-user-plans-paid test-project/private-project)]
                :selected-filter :all
                :selected-sorting :alphabetical}
               {:target test-node
                :shared {:timer-atom (timer/initialize)}}))))

(deftest median
  (are [m xs] (= m (insights/median xs))
       nil []
       1 [1]
       1.5 [1 2]
       2 [1 2 3]
       1.5 [1 1 2 3]
       1 [1 1 1 2 3]))
