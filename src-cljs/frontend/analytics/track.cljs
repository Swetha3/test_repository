(ns frontend.analytics.track
  (:require [frontend.analytics.core :as analytics]
            [frontend.state :as state]
            [om.core :as om :include-macros true]))

(def trusty-beta-flag :trusty-beta)

(def osx-flag :osx)

(defn image-name [flag value current-state]
  (cond
    (and (= flag trusty-beta-flag) value) "trusty"
    (and (= flag trusty-beta-flag) (not value)) "precise"
    (and (= flag osx-flag) value) "osx"
    ;; This means that the user has turned off osx, so we need to get
    ;; the trusty-beta flag value out of the state
    (and (= flag osx-flag) (not value))
    (image-name trusty-beta-flag
                (get-in current-state
                        (conj state/feature-flags-path trusty-beta-flag)))))

(defn project-image-change [{:keys [previous-state current-state flag value]}]
  (analytics/track {:event-type :change-image-clicked
                    :current-state current-state
                    :properties {:new-image (image-name flag value current-state)
                                 :flag-changed flag
                                 :value-changed-to value
                                 :new-osx-feature-flag (get-in current-state (conj state/feature-flags-path osx-flag))
                                 :new-trusty-beta-feature-flag (get-in current-state (conj state/feature-flags-path trusty-beta-flag))
                                 :previous-osx-feature-flag (get-in previous-state (conj state/feature-flags-path osx-flag))
                                 :previous-trusty-beta-feature-flag (get-in previous-state (conj state/feature-flags-path trusty-beta-flag))}}))

(defn update-plan-clicked [{:keys [new-plan previous-plan plan-type upgrade? owner]}]
  ((om/get-shared owner :track-event) {:event-type :update-plan-clicked
                                       :properties {:plan-type plan-type
                                                    :new-plan new-plan
                                                    :previous-plan previous-plan
                                                    :is-upgrade upgrade?}}))

(defn cancel-plan-modal-dismissed [{:keys [current-state component vcs_type plan-template plan-id plan-type]}]
  (analytics/track {:event-type :cancel-plan-modal-dismissed
                    :current-state current-state
                    :properties {:vcs-type vcs_type
                                 :plan-template plan-template
                                 :plan-id plan-id
                                 :plan-type plan-type
                                 :component component}}))

(defn cancel-plan-clicked [{:keys [current-state vcs_type plan-template plan-id cancel-reasons cancel-notes]}]
  (analytics/track {:event-type :cancel-plan-clicked
                    :current-state current-state
                    :properties {:vcs_type vcs_type
                                 :plan-template plan-template
                                 :plan-id plan-id
                                 :cancel-reasons cancel-reasons
                                 :cancel-notes cancel-notes}}))

(defn rebuild-clicked
  "Handles click events for rebuilds triggered with and without SSH
  Use with any post-control events called to initiate a rebuild, such as:
  rebuild-clicked, ssh-build-clicked, and retry-build-clicked"
  [args]
  (analytics/track {:event-type :rebuild-clicked
                    :current-state (:current-state args)
                    :properties {:vcs-type (:vcs-type args)
                                 :org-name (:org-name args)
                                 :repo-name (:repo-name args)
                                 :component (:component args)
                                 :is-ssh-build (:ssh? args)
                                 :is-build-without-cache (:no-cache? args)}}))

(defn stripe-checkout-closed [args]
  (analytics/track {:event-type :stripe-checkout-closed
                    :current-state (:current-state args)
                    :properties {:action (:action args)}}))

(defn stripe-checkout-succeeded [args]
  (analytics/track {:event-type :stripe-checkout-succeeded
                    :current-state (:current-state args)
                    :properties {:action (:action args)}}))
