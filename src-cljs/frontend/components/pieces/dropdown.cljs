(ns frontend.components.pieces.dropdown
  (:require [devcards.core :as dc :refer-macros [defcard-om]]
            [frontend.utils :refer-macros [html]]
            [frontend.utils.seq :refer [find-index]]
            [om.core :as om :include-macros true]))

(defn dropdown
  "A standard dropdown select control.

  :on-change    - A function called with the new value when the selection changes.
                  Unlike the DOM node's onclick handler, this function is *not*
                  called with an event object. It receives the value itself.
  :value        - The currently selected value.
  :options      - A sequence of pairs, [value label]. The label is the text shown
                  for each option. The value can be any object, and will be passed
                  to the :on-click handler when that option is selected.
  :default-text - (optional). If default-text has value, the dropdown won't make a
                  default selection but instead display the default text"
  [{:keys [on-change value options default-text]}]
  (let [values (map first options)
        labels (map second options)]
    (html
     [:select {:data-component `dropdown
               :on-change #(on-change (nth values (-> % .-target .-value int)))
               :value (find-index #(= value %) values)}
      (when default-text
        [:option {:disabled true :selected true}
         default-text])
      (for [[index label] (map-indexed vector labels)]
        [:option {:value index} label])])))


(dc/do
  (defcard-om dropdown
    (fn [{:keys [selected-value] :as data} owner]
      (om/component
        (html
         [:div
          [:div
           (dropdown
            {:on-change #(om/update! data :selected-value %)
             :value selected-value
             :options [["value" "String Value"]
                       [:value "Keyword Value"]
                       [{:map "value"} "Map Value"]]})]
          [:div
           "Selected: " (if selected-value
                          (pr-str selected-value)
                          "(Nothing)")]])))
    {:selected-value nil})

  (defcard-om default-text
    (fn [{:keys [selected-value] :as data} owner]
      (om/component
        (html
          [:div
           [:div
            (dropdown
              {:on-change #(om/update! data :selected-value %)
               :value nil
               :default-text "Select an option"
               :options [["value" "String Value"]
                         [:value "Keyword Value"]
                         [{:map "value"} "Map Value"]]})]
           [:div
            "Selected: " (if selected-value
                           (pr-str selected-value)
                           "(Nothing)")]])))
    {:selected-value nil}))
