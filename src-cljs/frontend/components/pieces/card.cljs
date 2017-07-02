(ns frontend.components.pieces.card
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [om.core :as om :include-macros true]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.tabs :as tabs]
            [frontend.utils :refer-macros [component html]]))

(defn full-bleed
  "A card with no padding. Content will touch the edge of the card."
  [content]
  (component
    (html
     [:div
      content])))

(defn- padding [content]
  (component
    (html
     [:div
      content])))

(defn- header [title action]
  (component
    (html
     [:div
      [:.title title]
      (when action
        [:.action action])])))

(defn basic
  "The most basic of cards. The given content appears on a card, with normal
  padding."
  [& children]
  (-> children padding full-bleed))

(defn titled
  "A card with a title.

  :title - The card title.
  :action - (optional) An action to place on the right of the header."
  ([{:keys [title action]} & children]
   (full-bleed
    (list
     (header title action)
     (padding children)))))

(defn tabbed
  "A card with a tab row.

  :tab-row - The tab row to attach to the card."
  ([{:keys [tab-row]} content]
   (full-bleed
    (list
     tab-row
     (padding content)))))

(defn collection
  "A set of cards to layout together"
  ([cards]
   (component
    (html
     [:div
      (for [card cards]
        ;; Reuse the card's key. Thus, if each card is built with a unique key,
        ;; each .item will be built with a unique key.
        [:.item (when-let [react-key (and card (.-key card))]
                  {:key react-key})
         card])]))))

(dc/do
  (defcard basic-card
    (basic "Some content.")
    {}
    {:classname "background-gray"})

  (defcard full-bleed-card
    (full-bleed "Some content.")
    {}
    {:classname "background-gray"})

  (defcard titled-card
    (titled {:title "Card Title (Generally in Title Case)"}
            "Some content.")
    {}
    {:classname "background-gray"})

  (defcard titled-card-with-action
    (titled
     {:title "Card Title (Generally in Title Case)"
      :action (button/button {:kind :primary
                              :size :small}
                             "Action")}
     "Some content.")
    {}
    {:classname "background-gray"})

  (defcard tabbed-card
    (fn [state]
      (tabbed
       {:tab-row (om/build tabs/tab-row {:tabs [{:name :tab-one
                                                 :label "Tab One"}
                                                {:name :tab-two
                                                 :label "Tab Two"}]
                                         :selected-tab-name (:selected-tab-name @state)
                                         :on-tab-click #(swap! state assoc :selected-tab-name %)})}
       "Some content."))
    {:selected-tab-name :tab-one}
    {:classname "background-gray"})

  (defcard card-collection
    (collection
     [(titled {:title "Card A"}
              "A bunch of words")
      (titled {:title "Card B"}
              "A whole bunch of words")])
    {}
    {:classname "background-gray"}))
