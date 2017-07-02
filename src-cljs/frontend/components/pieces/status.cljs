(ns frontend.components.pieces.status
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.pieces.icon :as icon]
            [frontend.models.build :as build-model]
            [frontend.utils :refer-macros [component html]]
            [frontend.utils.devcards :as devcard-utils]))

(defn- status-icon [status-class]
  (case status-class
    :status-class/failed (icon/status-failed)
    :status-class/stopped (icon/status-canceled)
    :status-class/succeeded (icon/status-passed)
    :status-class/running (icon/status-running)
    :status-class/waiting (icon/status-queued)
    :status-class/on-hold (icon/status-on-hold)))

(defn icon
  "A status icon corresponding to the given status class."
  [status-class]
  (component
    (html
     [:div {:class (name status-class)}
      (status-icon status-class)])))

(defn badge
  "A status badge corresponding to the given status class, with a text label."
  [status-class label]
  (component
    (html
     [:div {:class (name status-class)
            :title label}
      [:.status-icon (status-icon status-class)]
      [:.badge-label label]])))

(defn build-icon
  "A status icon corresponding to the given build status."
  [build-status]
  (icon (build-model/status-class build-status)))

(defn build-badge
  "A status badge corresponding to the given build status."
  [build-status]
  (badge (build-model/status-class build-status)
         (case build-status
           :build-status/infrastructure-fail "Circle Bug"
           :build-status/timed-out "Timed Out"
           :build-status/no-tests "No Tests"
           :build-status/not-run "Not Run"
           :build-status/skipped "Skipped"
           :build-status/not-paid "Not Paid"
           :build-status/not-running "Not Running"
           :build-status/failed "Failed"
           :build-status/fixed "Fixed"
           :build-status/killed "Killed"
           :build-status/queued "Queued"
           :build-status/retried "Retried"
           :build-status/running "Running"
           :build-status/scheduled "Scheduled"
           :build-status/success "Success"
           :build-status/canceled "Canceled")))

(dc/do
  (def ^:private status-classes
    [:status-class/waiting
     :status-class/running
     :status-class/on-hold
     :status-class/succeeded
     :status-class/failed
     :status-class/stopped])

  (def ^:private build-statuses
    [:build-status/failed
     :build-status/fixed
     :build-status/infrastructure-fail
     :build-status/killed
     :build-status/no-tests
     :build-status/not-paid
     :build-status/not-run
     :build-status/not-running
     :build-status/queued
     :build-status/retried
     :build-status/running
     :build-status/scheduled
     :build-status/skipped
     :build-status/success
     :build-status/timed-out
     :build-status/canceled])

  (defcard status-classes
    "There are five classes of status in CircleCI. Each has a color and an icon."
    (html
     [:table
      [:thead
       [:th {:style {:padding "5px"}} "badge"]
       [:th {:style {:padding "5px"}} "icon"]
       [:th {:style {:padding "5px"}} "status class"]]
      [:tbody
       (for [status-class status-classes]
         [:tr
          [:td {:style {:text-align "center"}}
           [:div {:style {:display "flex"
                          :justify-content "center"}}
            (badge status-class "label")]]
          [:td
           [:div {:style {:display "flex"
                          :justify-content "center"}}
            (icon status-class)]]
          [:td {:style {:padding "5px"}}
           (devcard-utils/display-data status-class)]])]]))

  (defcard build-statuses
    "There are many build statuses. Each belongs to a status class, and each has its own badge label."
    (html
     [:table
      [:thead
       [:th {:style {:padding "5px"}} "badge"]
       [:th {:style {:padding "5px"}} "icon"]
       [:th {:style {:padding "5px"}} "build status"]]
      [:tbody
       (let [status-class-order (reduce-kv #(assoc %1 %3 %2) {} status-classes)]
         (for [build-status (sort-by (comp status-class-order build-model/status-class) build-statuses)]
           [:tr
            [:td
             [:div {:style {:display "flex"
                            :justify-content "center"}}
              (build-badge build-status)]]
            [:td
             [:div {:style {:display "flex"
                            :justify-content "center"}}
              (build-icon build-status)]]
            [:td {:style {:padding "5px"}}
             (devcard-utils/display-data build-status)]]))]])))
