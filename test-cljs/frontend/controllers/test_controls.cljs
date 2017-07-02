(ns frontend.controllers.test-controls
  (:require [cljs.core.async :refer [chan <! >!]]
            [cljs.test :as test]
            [frontend.state :as state]
            [frontend.utils.ajax :as ajax]
            [frontend.analytics.core :as analytics]
            [frontend.api.path :as api-path]
            [frontend.controllers.controls :as controls]
            [bond.james :as bond :include-macros true]
            [goog.string :as gstring])
  (:require-macros [cljs.test :refer [is deftest testing async]]
                   [cljs.core.async.macros :refer [go]]))

(deftest extract-from-works
  (is (= nil (controls/extract-from nil nil)))
  (is (= nil (controls/extract-from nil [])))
  (is (= nil (controls/extract-from nil [:a])))
  (is (= nil (controls/extract-from {} [:a])))
  (is (= {:a 1} (controls/extract-from {:a 1} [:a])))
  (is (= {:a {:b {:c 1}}} (controls/extract-from {:a {:b {:c 1}}, :d 2} [:a :b]))))

(deftest merge-settings-works
  (testing "nil paths always return settings"
    (let [settings {:a 1, :b {:c 3}}]
      (is (= settings (controls/merge-settings nil nil settings)))
      (is (= settings (controls/merge-settings nil {} settings)))
      (is (= settings (controls/merge-settings nil {:a 4} settings)))))

  (testing "empty paths always return settings"
    (let [settings {:a 1, :b {:c 3}}]
      (is (= settings (controls/merge-settings [] nil settings)))
      (is (= settings (controls/merge-settings [] {} settings)))
      (is (= settings (controls/merge-settings [] {:a 4} settings)))))

  (testing "nil settings returns project values"
    (let [project {:a 1, :b {:c 3}}]
      (is (= {:b {:c 3}} (controls/merge-settings [[:b :c]], project, nil)))
      (is (= {:b {:c 3}} (controls/merge-settings [[:b] [:b :c]], project, nil)))
      (is (= {} (controls/merge-settings [[:a]], {}, nil)))))

  (testing "nil project always return settings"
    (is (= {:a 1} (controls/merge-settings [[:b]], nil, {:a 1})))
    (is (= {:a 1, :b {:c 4}} (controls/merge-settings [[:b :c]], nil, {:a 1, :b {:c 4}}))))

  (testing "empty project always return settings"
    (is (= {:a 1} (controls/merge-settings [[:b]], {}, {:a 1})))
    (is (= {:a 1, :b {:c 4}} (controls/merge-settings [[:b :c]], {}, {:a 1, :b {:c 4}}))))

  (testing "empty settings use pathed portions of project data"
    (is (= {} (controls/merge-settings [[:b]], {}, {})))
    (is (= {} (controls/merge-settings [[:a :b :c]], {:d 1}, {})))
    (is (= {:b {:c 4}} (controls/merge-settings [[:b :c]], {:a 1, :b {:c 4}}, {})))
    (is (= {:b {:c 4}, :d 5} (controls/merge-settings [[:b :c], [:d]], {:a 1, :b {:c 4}, :d 5}, {}))))

  (testing "top-level settings merge correctly"
    (is (= {:a 1, :b 2, :c 3} (controls/merge-settings [[:a], [:b]], {}, {:a 1, :b 2, :c 3})))
    (is (= {:a 10, :b 3, :c 2} (controls/merge-settings [[:a], [:b]], {:a 1, :b 3}, {:a 10, :c 2})))
    (is (= {:a 10, :b {:c 2}} (controls/merge-settings [[:a], [:b :c]], {:a 1, :b 3}, {:a 10, :b {:c 2}}))))

  (testing "nested settings merge correctly"
    (is (= {:e 2} (controls/merge-settings [[:a :b :c]], {:d 1}, {:e 2})))
    (is (= {:a {:b {:c 2}}} (controls/merge-settings [[:a :b]], {:a {:x 10}}, {:a {:b {:c 2}}})))
    (is (= {:a {:b {:c 2}}} (controls/merge-settings [[:a]], {:a {:b {:c 1}}}, {:a {:b {:c 2}}})))
    (is (= {:a {:b {:c 2}}} (controls/merge-settings [[:a :b]], {:a {:b {:c 1}}}, {:a {:b {:c 2}}})))
    (is (= {:a {:b {:c 2}}} (controls/merge-settings [[:a :b :c]], {:a {:b {:c 1}}}, {:a {:b {:c 2}}})))

    (is (= {:a {:b {:c 1, :d 2}}} (controls/merge-settings [[:a :b :c]], {:a {:b {:c 1}}}, {:a {:b {:d 2}}})))))

(defn success-event [] "success")

(defn failed-event [] "failed")

(defn check-button-ajax [{:keys [status success-count failed-count]}]
  (async done
         (go
           (bond/with-spy [success-event failed-event]
             (bond/with-stub [[ajax/ajax (fn [_ _ _ c _] (go (>! c  ["" status ""])))]]
               (let [channel (chan)]
                 (controls/button-ajax "a" "b" "c" channel
                                       :fake "data"
                                       :events {:success success-event
                                                :failed failed-event})
                 (<! channel)
                 (is (= success-count (-> success-event bond/calls count)))
                 (is (= failed-count (-> failed-event bond/calls count)))
                 (done)))))))

(deftest button-ajax-failed-event-works
  (testing "button-ajax correctly sends a failed event on failed"
    (check-button-ajax {:status :success
                        :success-count 1
                        :failed-count 0})))

(deftest button-ajax-success-event-works
  (testing "button-ajax correctly sends a success event on success"
    (check-button-ajax {:status :failed
                        :success-count 0
                        :failed-count 1})))

(deftest button-ajax-random-status-no-event-fires
  (testing "a non success or failed event fire no events"
    (check-button-ajax {:status :foobar
                        :success-count 0
                        :failed-count 0})))

;; TODO: make this a macro in a test.analytics.utils ns
(defn analytics-track-call-args [func]
  (let [calls (atom [])]
    (with-redefs [analytics/track (fn [event-data]
                                    (swap! calls conj {:args (list event-data)}))]
      (func)
      @calls)))

(deftest post-control-event-activate-plan-trial-works
  (with-redefs [ajax/ajax (constantly nil)]
    (let [org-name "foo"
          vcs-type "github"
          plan-type :paid
          template :t3
          controller-data {:plan-type plan-type
                           :template template
                           :org {:name org-name :vcs_type vcs-type}}
          current-state {:zippity "doo-da"}]
      (testing "the post-control-event activate-plan-trial sends a :start-trial-clicked event with the correct properties"
        (let [calls (analytics-track-call-args #(controls/post-control-event! {} :activate-plan-trial controller-data {} current-state {:api (chan)
                                                                                                                                        :nav (chan)}))]
          (is (= (count calls) 1))
          (let [args (-> calls first :args first)]
            (is (= (:event-type args) :start-trial-clicked))
            (is (= (:current-state args) current-state))
            (is (= (:properties args) {:org org-name
                                       :vcs-type vcs-type
                                       :plan-type :linux
                                       :template template}))))))))

(deftest post-control-event-dismiss-trial-offer-banner-works
  (let [org-name "bar"
        vcs-type "bitbucket"
        org {:name org-name :vcs_type vcs-type}
        plan-type :paid
        template :t3
        controller-data {:plan-type plan-type
                         :template template
                         :org {:name org-name :vcs_type vcs-type}}
        current-state {:zippity "doo-da"}]
    (testing "the post-control-event dismiss-trial-offer-banner sends a :dismiss-trial-offer-banner-clicked event with the correct properties"
      (let [calls (analytics-track-call-args #(controls/post-control-event! {} :dismiss-trial-offer-banner controller-data {} current-state))]
        (is (= (count calls) 1))
        (let [args (-> calls first :args first)]
          (is (= (:event-type args) :dismiss-trial-offer-banner-clicked))
          (is (= (:current-state args) current-state))
          (is (= (:properties args) {:org org-name
                                     :vcs-type vcs-type
                                     :plan-type :linux
                                     :template template})))))))

(deftest post-control-event-selected-project-parallelism-works
  (let [org-name ""
        vcs-type "bitbucket"
        org {:name org-name :vcs_type vcs-type}
        previous-parallelism 1
        previous-state (assoc-in {} state/project-parallelism-path previous-parallelism)
        new-parallelism 10
        current-state (assoc-in {} state/project-parallelism-path new-parallelism)
        controller-data {:project-id "https://github.com/circleci/circle"}]
    (with-redefs [ajax/ajax (fn [& args] nil)]
      (testing "the post-control-event dismiss-trial-offer-banner sends a :selected-project-parallelism event with the correct properties"
      (let [calls (analytics-track-call-args #(controls/post-control-event! {} :selected-project-parallelism controller-data previous-state current-state {:api (chan)}))]
        (is (= (count calls) 1))
        (let [args (-> calls first :args first)]
          (is (= (:event-type args) :update-parallelism-clicked))
          (is (= (:current-state args) current-state))
          (is (= (:properties args) {:plan-type :linux
                                     :previous-parallelism previous-parallelism
                                     :new-parallelism new-parallelism
                                     :vcs-type "github"}))))))))

(deftest post-control-event-dismiss-invite-form-works
  (let [current-state {:zippity "doo-da"}]
    (testing "the post-control-event dismiss-invite-form sends a :invite-teammates-dismissed event"
      (let [calls (analytics-track-call-args #(controls/post-control-event! {} :dismiss-invite-form {} {} current-state))]
        (is (= (count calls) 1))
        (let [args (-> calls first :args first)]
          (is (= (:event-type args) :invite-teammates-dismissed))
          (is (= (:current-state args) current-state)))))))

(deftest post-control-event-invite-selected-all-works
  (let [users ["user1" "user2"]
        current-state (assoc-in {} state/build-invite-members-path users)]
    (testing "the post-control-event invite-selected-all sends a :invite-teammates-select-all-clicked event"
      (let [calls (analytics-track-call-args #(controls/post-control-event! {} :invite-selected-all {} {} current-state))]
        (is (= (count calls) 1))
        (let [args (-> calls first :args first)]
          (is (= (:event-type args) :invite-teammates-select-all-clicked))
          (is (= (:current-state args) current-state))
          (is (= (:properties args) {:teammate-count (count users)})))))))

(deftest post-control-event-invite-selected-none-works
  (let [users ["user1" "user2"]
        current-state (assoc-in {} state/build-invite-members-path users)]
    (testing "the post-control-event invite-selected-none sends a :invite-teammates-select-none-clicked event"
      (let [calls (analytics-track-call-args #(controls/post-control-event! {} :invite-selected-none {} {} current-state))]
        (is (= (count calls) 1))
        (let [args (-> calls first :args first)]
          (is (= (:event-type args) :invite-teammates-select-none-clicked))
          (is (= (:current-state args) current-state))
          (is (= (:properties args) {:teammate-count (count users)})))))))

(deftest post-control-event-invited-team-members-works
  (let [calls (atom [])
        project-name "proj"
        vcs-type "github"
        invitees [{:handle "me"
                   :email "me@foo.com"
                   :external_id 666
                   :name "Me"}
                  {:handle "you"
                   :email "you@bar.co"
                   :external_id 667
                   :name "You"}]
        control-data {:project-name project-name
                      :org-name "org"
                      :invitees invitees}
        api-ch "api-ch"
        comms {:api api-ch}]
    (testing "the post-control-event invite-github-users calls button-ajax with the correct parameters"
      (with-redefs [controls/button-ajax (fn [method url message channel & opts]
                                           (swap! calls conj {:args (list method url message channel opts)}))]
        (controls/post-control-event! {} :invited-team-members control-data {} {} comms)
        (let [calls @calls
              args (-> calls first :args)]
          (is (= (count calls) 1))
          (is (= (nth args 0) :post))
          (is (= (nth args 1) (api-path/project-users-invite vcs-type project-name)))
          (is (= (nth args 2) :invite-team-members))
          (is (= (nth args 3) api-ch))
          (is (= (->> (nth args 4) (apply hash-map) :context) {:project project-name
                                                               :first_green_build true}))
          (is (= (->> (nth args 4) (apply hash-map) :params) invitees)))))))

(deftest post-control-event-invited-team-members-invites-users-for-bb-projects
  (let [calls (atom [])
        project-name "proj"
        vcs-type "bitbucket"
        invitees [{:handle "me"
                   :email "me@foo.com"
                   :external_id 666
                   :name "Me"}
                  {:handle "you"
                   :email "you@bar.co"
                   :external_id 667
                   :name "You"}]
        control-data {:project-name project-name
                      :vcs-type vcs-type
                      :org-name "org"
                      :invitees invitees}
        api-ch "api-ch"
        comms {:api api-ch}]
    (testing "the post-control-event invite-github-users does nothing for bitbucket projects"
      (with-redefs [controls/button-ajax (fn [method url message channel & opts]
                                           (swap! calls conj {:args (list method url message channel opts)}))]
        (controls/post-control-event! {} :invited-team-members control-data {} {} comms)
        (let [calls @calls
              args (-> calls first :args)]
          (is (= (count calls) 1))
          (is (= (->> (nth args 4) (apply hash-map) :params) invitees)))))))
