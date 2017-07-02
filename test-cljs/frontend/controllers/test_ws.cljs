(ns frontend.controllers.test-ws
  (:require [frontend.controllers.ws :as ws]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.models.action :as action]
            [cljs.test :refer-macros [deftest is testing]]))

(def dummy-pusher-imp {})

(def ^{:doc "Dummy container data for a build with 2x parallelism, doesn't include output"}
  dummy-containers
  [{:actions [{:infrastructure_fail nil, :index 0, :continue nil, :parallel false,
               :name "Starting the build", :start_time "2014-10-30T22:06:30.065Z",
               :timedout nil, :command "Starting the build", :type "infrastructure",
               :end_time "2014-10-30T22:06:30.119Z", :canceled nil, :messages [],
               :status "success", :run_time_millis 54, :truncated false,
               :has_output false, :bash_command nil, :step 0, :failed nil, :exit_code nil}
              {:infrastructure_fail nil, :index 0, :continue nil, :parallel true,
               :name "Start container", :start_time "2014-10-30T22:06:30.137Z",
               :timedout nil, :command "Start container", :type "infrastructure",
               :source "config", :end_time "2014-10-30T22:06:32.418Z", :canceled nil,
               :messages [], :status "success", :run_time_millis 2281, :truncated false,
               :has_output true, :bash_command nil, :step 1, :failed nil, :exit_code 0}
              {:infrastructure_fail nil, :index 0, :continue nil, :parallel false,
               :name "Restore source cache", :start_time "2014-10-30T22:06:32.717Z",
               :timedout nil, :command "Restore source cache", :type "checkout",
               :source "cache", :end_time "2014-10-30T22:06:35.188Z", :canceled nil,
               :messages [], :status "success", :run_time_millis 2471, :truncated false,
               :has_output true, :bash_command nil, :step 2, :failed nil, :exit_code nil}]
    :index 0}
   {:actions [{:index 0, :step 0, :status "running", :filler-action true}
              {:missing-pusher-output true, :infrastructure_fail nil, :index 1,
               :continue nil, :parallel true, :name "Start container",
               :start_time "2014-10-30T22:06:30.136Z", :timedout nil,
               :command "Start container", :type "infrastructure", :source "config",
               :end_time "2014-10-30T22:06:32.702Z", :canceled nil, :messages [],
               :status "success", :run_time_millis 2566, :truncated false,
               :has_output true, :bash_command nil, :step 1, :failed nil, :exit_code 0}
              {:index 3, :step 2, :status "running", :filler-action true}]
    :index 1}])

(def dummy-container-state (assoc-in state/initial-state state/containers-path dummy-containers))

(deftest append-action-works
  (testing "we create filler steps if output comes out of order"
    (with-redefs [ws/ignore-build-channel? (constantly false)
                  action/format-output (fn [action _] action)]
      (let [new-state (ws/ws-event dummy-pusher-imp
                                   :build/append-action
                                   {:data (clj->js [{:index 0 :step 3}])}
                                   dummy-container-state)]
        (is (= 4 (count (get-in new-state (state/actions-path 0)))))))))
