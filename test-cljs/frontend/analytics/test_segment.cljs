(ns frontend.analytics.test-segment
  (:require [cljs.test :refer-macros [is deftest testing use-fixtures]]
            [schema.test]
            [frontend.utils :refer [clj-keys-with-dashes->js-keys-with-underscores]]
            [frontend.models.user :as user]
            [frontend.test-utils :as test-utils]
            [frontend.analytics.segment :as segment]))

(use-fixtures :once schema.test/validate-schemas)

(aset js/window "analytics" (js-obj))
(let [stub-fn (fn [fn-name arg1 arg2 & [arg3]]
                (let [
                      calls (atom [])]
                  (swap! calls conj {:fn fn-name
                                     :args (list arg1 arg2 arg3)})
                  @calls))]
  (aset js/analytics "page" (fn [nav-point subpage props]
                              (stub-fn "page" nav-point subpage props)))
  (aset js/analytics "track" (fn [nav-point props]
                              (stub-fn "track" nav-point props)))
  (aset js/analytics "identify" (fn [nav-point props]
                                  (stub-fn "identify" nav-point props))))

(def mock-user-properties
  {:all_emails ["email"]
   :basic_email_prefs "email"
   :bitbucket_authorized true
   :created_at "2015"
   :in_beta_program true
   :login "user"
   :name "User"
   :selected_email "email"
   :sign_in_count 42
   :ab-test-treatments {:foo :bar}
   :num-projects-followed 12}) 

(def outbound-user-props
  (clj-keys-with-dashes->js-keys-with-underscores mock-user-properties))

(def mock-segment-props
  {:user "test-user"
   :view :test-view
   :org  "test-org"
   :repo "test-repo"
   :ab-test-treatments {:foo :bar}})

(def outbound-segment-props
  (clj-keys-with-dashes->js-keys-with-underscores mock-segment-props))

(deftest track-pageview-works
  (testing "track-pageview required a a nav-point :- Keyword, subpage :- Keyword, event-data :- SegmentProperties"
    (test-utils/fails-schema-validation #(segment/track-pageview :nav-point :subpage {}))
    (test-utils/fails-schema-validation #(segment/track-pageview :nav-point "subpage" {}))
    (test-utils/fails-schema-validation #(segment/track-pageview "nav-point" :subpage outbound-segment-props)))

  (testing "track-pageview does the correct data manipulation before sending data to segment"
    (let [nav-point :nav-point
          subpage :subpage
          calls (segment/track-pageview nav-point subpage mock-segment-props)]
      (is (= 1 (count calls)))
      (is (= "page" (-> calls first :fn)))
      (is (= (name nav-point) (-> calls first :args first)))  
      (is (= (name subpage) (-> calls first :args second)))
      (is (= (js->clj outbound-segment-props) (-> calls first :args (#(nth % 2)) js->clj))))))

(deftest track-event-works
  (testing "track-event requires a keyword and SegmentProperties"
    (test-utils/fails-schema-validation #(segment/track-event :event {}))
    (test-utils/fails-schema-validation #(segment/track-event "event" outbound-segment-props)))
  (testing "track-event does the correct data manipulation before sending data to segment"
    (let [event :event
          calls (segment/track-event event mock-segment-props)]
      (is (= 1 (count calls)))
      (is (= "track" (-> calls first :fn)))
      (is (= (name event) (-> calls first :args first)))
      (is (= (js->clj outbound-segment-props) (-> calls first :args second js->clj))))))

(deftest identify-works
  ;; segment/identify takes a :primary-email, so add that to the mock-user-properties and outbound-user-props
  (let [mock-user-properties (merge mock-user-properties {:primary-email "email"})
        outbound-user-props (clj-keys-with-dashes->js-keys-with-underscores mock-user-properties)]
    (testing "identify requires event-data consisting of a id and user-properties"
      (test-utils/fails-schema-validation #(segment/identify "id"))
      (test-utils/fails-schema-validation #(segment/identify {:id "id"
                                                              :user-properties {}}))
      (test-utils/fails-schema-validation #(segment/identify {:id 1
                                                              :user-properties mock-user-properties})))
    (testing "identify does the correct data manipulation before sending data to segment"
      (let [id "id"
            calls (segment/identify {:id id
                                     :user-properties mock-user-properties})]
        (is (= 1 (count calls)))
        (is (= "identify" (-> calls first :fn)))
        (is (= id (-> calls first :args first)))
        (is (= (js->clj outbound-user-props) (-> calls first :args second js->clj)))))))
