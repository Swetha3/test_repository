(ns frontend.analytics.test-core
  (:require [cljs.test :refer-macros [is deftest testing use-fixtures]]
            [schema.test]
            [frontend.state :as state]
            [frontend.utils.seq :refer [submap?]]
            [frontend.utils.build :as build-util]
            [frontend.test-utils :as test-utils]
            [frontend.models.user :as user]
            [frontend.analytics.amplitude :as amplitude]
            [frontend.analytics.core :as analytics]
            [frontend.analytics.common :as common-analytics]
            [frontend.analytics.segment :as segment]))

(defn- stub-set-amplitude-session-cookie [test]
  (with-redefs [amplitude/set-session-id-cookie! (constantly nil)]
    (test)))

(use-fixtures :once schema.test/validate-schemas
                    stub-set-amplitude-session-cookie)

(defn stub-segment-track-event
  "Given a function, call it and return the args segment/track-event
  was passed."
  [f]
  (let [calls (atom [])
        stub-fn (fn [event & [properties]]
                  (swap! calls conj {:args (list event properties)}))]
    (with-redefs [segment/track-event stub-fn
                  segment/track-external-click stub-fn]
      (f)
      @calls)))

(def data {:view :a-view
           :primary-email "foobar@email.com"
           :user "foobar-user"
           :repo "foobar-repo"
           :org "foobar-org"})

(def properties {:view :new-view
                 :primary-email "props@email.com"
                 :user "props-user"
                 :repo "props-repo"
                 :org "props-org"})

(def current-state (test-utils/state data))

(deftest track-default-works
  (let [click-event (first analytics/supported-events)]
    (testing "track :default works with a current-state"
      (let [calls (stub-segment-track-event #(analytics/track {:event-type click-event
                                                               :current-state current-state}))]
        (is (= 1 (count calls)))
        (is (= click-event (-> calls first :args first)))
        (is (submap? data (-> calls first :args second)))))

    (testing "you need an event-type"
      (test-utils/fails-schema-validation #(analytics/track {:current-state current-state})))

    (testing "track :default is checking for valid event-data"
      (test-utils/fails-schema-validation #(analytics/track {:event-type click-event
                                                             :current-state current-state
                                                             :shibbity-doo-bot "heyooooo"})))

    (testing "track :default is not allowing non-valid events"
      (test-utils/fails-schema-validation #(analytics/track {:event-type :shibbity-ibbity-ima-fake-event
                                                             :current-state current-state})))))

(deftest track-external-click-works
  (let [event-type :external-click
        event (first analytics/supported-events)]
    (testing "a valid external-click event is fired"
      (let [calls (stub-segment-track-event #(analytics/track {:event-type event-type
                                                               :event event
                                                               :current-state current-state}))]
        (is (= 1 (count calls)))
        (is (= event (-> calls first :args first)))
        (is (submap? data (-> calls first :args second)))))

  (testing "track :external-click is checking for valid event-data"
    (test-utils/fails-schema-validation #(analytics/track {:event-type event-type
                                                           :current-state current-state
                                                           :shibbity-doo-bot "heyooooo"})))

  (testing "track :external-click is not allowing non-valid events"
    (test-utils/fails-schema-validation #(analytics/track {:event-type event-type
                                                           :event :shibbity-ibbity-ima-fake-event
                                                           :current-state current-state})))))

(deftest track-view-build-works
  (let [event-type :view-build
        build-num 4
        repo "repo"
        org "org"
        oss false
        outcome nil
        build {:start_time "nowish"
               :build_num build-num
               :oss oss
               :vcs_url (str "https://github.com/"org"/"repo)
               :outcome outcome}
        calls (stub-segment-track-event #(analytics/track {:event-type event-type
                                                           :build build
                                                           :current-state current-state}))]

    (testing "track :view-build adds the correct properties"
      (is (= 1 (count calls)))
      (is (= event-type (-> calls first :args first)))
      (is (submap? (merge data
                          {:running true
                           :build-num build-num
                           :repo repo
                           :org org
                           :oss oss
                           :outcome outcome}) (-> calls first :args second))))

    (testing "track :view-build requires a build"
      (test-utils/fails-schema-validation #(analytics/track {:event-type event-type
                                                             :current-state current-state})))

    (testing "track :view-build adds the correct :tab in the correct situations"
      ;; first assure that :tab and :subpage are not set in state
      ;; note: subpage should never be set when viewing a build (since the build vier does not have a subpage)
      (is (nil? (get-in current-state state/navigation-subpage-path)))
      (is (nil? (get-in current-state state/navigation-tab-path)))

      (let [default-tab :i-am-the-default-tab]
        (with-redefs [build-util/default-tab (constantly default-tab)]
          (testing "track :view-build makes :tab the build/default-tab when one is not specified in state"
            (let [calls (stub-segment-track-event #(analytics/track {:event-type event-type
                                                                     :build build
                                                                     :current-state current-state}))]
              (is (= default-tab (-> calls first :args second :tab)))))

          (testing "track :view-build makes :tab the navigation-tab-path when one is specified in state"
            (let [tab :non-default-tab
                  calls (stub-segment-track-event #(analytics/track {:event-type event-type
                                                                     :build build
                                                                     :current-state (assoc-in current-state state/navigation-tab-path tab)}))]
              (is (= tab (-> calls first :args second :tab))))))))))

(deftest track-init-user-works
  (testing "track :init-user adds the correct properties and calls segment/identify"
    (let [user-email "hi-im-a-users-email@foobarbaz.com"
          ab-treatments {:foo :bar}
          num-projects 12
          stub-identify (fn [properties]
                          (let [calls (atom [])]
                            (with-redefs [segment/identify (fn [event-data]
                                                             (swap! calls conj {:args (list event-data)}))
                                          user/primary-email (constantly user-email)
                                          user/num-projects-followed (constantly num-projects)
                                          analytics/ab-test-treatments (constantly ab-treatments)]
                              (analytics/track {:event-type :init-user
                                                :current-state current-state}))
                            @calls))]
       (let [calls (stub-identify {})
             expected-data {:id (get-in current-state state/user-analytics-id-path)
                            :user-properties (merge
                                               {:primary-email user-email
                                                :ab-test-treatments ab-treatments
                                                :num-projects-followed num-projects}
                                               (select-keys
                                                 (get-in current-state state/user-path)
                                                 (keys common-analytics/UserProperties)))}]
         (is (= 1 (-> calls count)))
         (is (submap? expected-data (-> calls first :args first)))))))

(deftest track-pageview-works
  (let [event-type :pageview
        nav-point :some-view-some-place
        stub-track-pageview (fn [properties & [state]]
                              (let [current-state (or state current-state)
                                    calls (atom [])]
                                (with-redefs [segment/track-pageview (fn [nav-point subpage & [event-data]]
                                                                     (swap! calls conj {:args (list nav-point subpage event-data)}))]
                                (analytics/track {:event-type event-type
                                                  :navigation-point nav-point
                                                  :properties properties
                                                  :current-state current-state}))
                                @calls))]

    (testing "track :pageview adds the correct properties to segment/track-pageview"
      (let [calls (stub-track-pageview {})]
        (is (= 1 (-> calls count)))
        (is (= nav-point (-> calls first :args first)))
        (is (submap? data (-> calls first :args (#(nth % 2)))))))

    (testing "track :pageview :properties overwrite default values from current-state"
      (let [calls (stub-track-pageview properties)]
        (is (submap? properties (-> calls first :args (#(nth % 2)))))))

    (testing "track :pageview requires a :navigation-point"
      (test-utils/fails-schema-validation #(analytics/track {:event-type event-type
                                                             :current-state current-state})))

    (testing "track :pageview automatically add a :default subpage when there isn't one in state"
        (let [calls (stub-track-pageview {})]
          (is (= :default (-> calls first :args second)))))

    (testing "track :pageview adds a subpage if one is present in state/navigation-subpage-path"
        (let [subpage :a-subpage
              calls (stub-track-pageview {} (assoc-in current-state state/navigation-subpage-path subpage))]
          (is (= subpage (-> calls first :args second)))))

    (testing "track :pageview adds a subpage if one is present in state/navigation-tab-path"
        (let [tab :a-tab
              calls (stub-track-pageview {} (assoc-in current-state state/navigation-tab-path tab))]
          (is (= tab (-> calls first :args second)))))

    (testing "state/navigation-subpage-path takes precedent over state/navigation-tab-path for the subpage"
        (let [tab :a-tab
              subpage :a-subpage
              calls (stub-track-pageview {} (-> current-state
                                                (assoc-in state/navigation-tab-path tab)
                                                (assoc-in state/navigation-subpage-path subpage)))]
          (is (= subpage (-> calls first :args second)))))))

(deftest properties-overwrite-default-state
  (testing "for each type of tracking that calls segment/track-event, ensure that :properties overwrite the default values from :current-state"
    (let [click-event (first analytics/supported-events)
          ensure-overwrite (fn [event-type & extra-args]
                             (let [calls (stub-segment-track-event #(analytics/track
                                                                      (merge {:event-type event-type
                                                                              :properties properties
                                                                              :current-state current-state}
                                                                             (apply hash-map extra-args))))]
                               (is (submap? properties (-> calls first :args second)))))]
      (ensure-overwrite click-event)
      (ensure-overwrite :external-click :event click-event)
      (ensure-overwrite :view-build :build {}))))
