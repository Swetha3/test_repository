(ns frontend.components.pieces.tabs
  (:require [devcards.core :as dc :refer-macros [defcard-om]]
            [frontend.config :as config]
            [frontend.utils :refer-macros [defrender html]]
            [om.core :as om :include-macros true]))

(defn tab-row
  "A row of tabs, suitable for the top of a card.

  :tabs              - A sequence of tabs, in display order. Each tab is a map:
                       :name  - A unique identifier for the tab, not displayed to the
                                user.
                       :icon  - An icon which appears next to the tab label, often an <i>
                                element of some sort.
                       :label - The text which labels the tab. This may also be a
                                component or a list of components.
  :selected-tab-name - The name of the selected tab.
  :on-tab-click      - (optional) A handler called when a tab is clicked. The handler will
                       receive the name of the clicked tab. (Either :on-tab-click or :href
                       should be given.)
  :href              - (optional) A function of a tab name which returns the URL that tab
                       should link to. If :href is not given, the tabs will not be links.
                       (Either :on-tab-click or :href should be given.)"
  [{:keys [tabs selected-tab-name on-tab-click href] :as data} owner]
  (reify
    om/IDisplayName (display-name [_] "Tab Row")

    om/IRender
    (render [_]
      (html
       [:ul {:data-component `tab-row}
        (for [{:keys [name icon label]} tabs]
          [:li (merge
                {:key name}
                (cond
                  (= selected-tab-name name) {:class "active"}
                  on-tab-click {:on-click #(on-tab-click name)}))
           (let [content [:.content
                          (when icon
                            [:span.tab-icon icon])
                          [:span.tab-label label]]]
             (if (and href (not= selected-tab-name name))
               [:a.exception {:href (href name)}
                content]
               content))])]))))

(dc/do
  (defcard-om tab-row
    "Here, a parent renders a `tab-row`. Note that the `tab-row` itself does not
    track which tab is selected as state. Instead, the parent tells the tab row
    which tab is selected. It's the parent's responsibility to listen to the
    `:on-tab-click` event and track which tab should be selected, by holding
    it in its own component state, storing it in the app state (as demonstrated
    here), or some other means. (Often, in our app, we accomplish this by
    navigating to a different URL, which specifies the tab which should be
    selected.)"
    (fn [{:keys [selected-tab-name] :as data} owner]
      (om/component
        (html
         [:div
          (om/build tab-row {:tabs [{:name :tab-one
                                     :label "Tab One"}
                                    {:name :tab-two
                                     :label "Tab Two"}]
                             :selected-tab-name selected-tab-name
                             :on-tab-click #(om/update! data :selected-tab-name %)})
          "Selected: " (str selected-tab-name)])))
    {:selected-tab-name :tab-one})

  (defcard-om tab-row-with-icon
    "This `tab-row` features icons on the tab labels."
    (fn [{:keys [selected-tab-name] :as data} owner]
      (om/component
        (html
         [:div
          (om/build tab-row {:tabs [{:name :tab-one
                                     :icon (html [:i.fa.fa-linux.fa-lg])
                                     :label "Tab One"}
                                    {:name :tab-two
                                     :icon (html [:i.fa.fa-apple.fa-lg])
                                     :label "Tab Two"}]
                             :selected-tab-name selected-tab-name
                             :on-tab-click #(om/update! data :selected-tab-name %)})
          "Selected: " (str selected-tab-name)])))
    {:selected-tab-name :tab-one})

  (defcard-om tab-row-with-hrefs
    "A `tab-row` can also be implemented as links to URLs. This is useful when
    the state which drives the tab selection is derived from the URL. Rather
    than provide an `:on-tab-click`, provide an `:href`, which is a function
    which takes a tab name and returns the href it should point to."
    (fn [{:keys [selected-tab-name] :as data} owner]
      (om/component
        (html
         [:div
          (om/build tab-row {:tabs [{:name :tab-one
                                     :label "Tab One"}
                                    {:name :tab-two
                                     :label "Tab Two"}]
                             :selected-tab-name selected-tab-name
                             :href #(str "#" %)})
          "Selected: " (str selected-tab-name)])))
    {:selected-tab-name :tab-one}))
