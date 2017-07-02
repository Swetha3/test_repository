(ns frontend.analytics.test-track
  (:require [cljs.core.async :as async :refer [chan]]
            [cljs.test :refer-macros [is deftest testing]]
            [bond.james :as bond :include-macros true]
            [frontend.state :as state]
            [frontend.controllers.controls :as controls]
            [frontend.analytics.core :as analytics]
            [frontend.analytics.track :as track]
            [frontend.analytics.segment :as segment]
            [frontend.test-utils :as test-utils]))

(deftest project-image-change-works
  (let [state (test-utils/state {})
        event :change-image-clicked
        test-image-logging (fn [flag value image-name & [trusty-state-value]]
                             (let [call-args (atom [])]
                               (with-redefs [analytics/track (fn [& args]
                                                               (swap! call-args conj args))]
                                 (let [data {:project-id "some-fake-org/some-fake-project"
                                             :flag flag
                                             :value value}
                                       previous-state state
                                       current-state (if (nil? trusty-state-value)
                                                       state
                                                       (assoc-in state
                                                                 (conj state/feature-flags-path :trusty-beta)
                                                                 trusty-state-value))]
                                   (controls/post-control-event! "" :project-feature-flag-checked data previous-state current-state {:api (chan)
                                                                                                                                     :errors (chan)})
                                   (is (= 1 (count @call-args)))
                                   (is (= (-> @call-args first :event-type event)))
                                   (is (= (-> @call-args first :properties
                                              {:new-image image-name
                                               :flag-changed flag
                                               :value-changed-to value
                                               :new-osx-feature-flag (get-in current-state (conj state/feature-flags-path track/osx-flag))
                                               :new-trusty-beta-feature-flag (get-in current-state (conj state/feature-flags-path track/trusty-beta-flag))
                                               :previous-osx-feature-flag (get-in previous-state (conj state/feature-flags-path track/osx-flag))
                                               :previous-trusty-beta-feature-flag (get-in previous-state (conj state/feature-flags-path track/trusty-beta-flag))})))))))]

    (testing "switching between precise and trusty with osx off sends the correct event"
      (test-image-logging :trusty-beta true "trusty")
      (test-image-logging :trusty-beta false "precise"))

    (testing "switching on osx sets the image to be osx"
      (test-image-logging :osx true "osx"))

    (testing "switching off osx sets it to precise or trusty depending on what is enabled"
      (test-image-logging :osx false "trusty" true)
      (test-image-logging :osx false "precise" false))

    (testing "if there is nothing set in the state and the user turns osx off the image is precise"
      (test-image-logging :osx false "precise"))))
