(ns frontend.components.pieces.button
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.forms :as forms]
            [frontend.components.pieces.icon :as icon]
            [frontend.utils :refer-macros [component html]]))

(defn button
  "A standard button.

  :on-click  - A function called when the button is clicked.
  :kind      - The kind of button. One of #{:primary :secondary :danger :flat}.
               (default: :secondary)
  :disabled? - If true, the button is disabled. (default: false)
  :label     - (optional) This will be used as tooltip text on the button.
  :size      - The size of the button. One of #{:full :small}.
               (default: :full)
  :fixed?    - If true, the button is fixed width. (default: false)"
  [{:keys [on-click kind disabled? size fixed? label]
    :or {kind :secondary size :full}}
   content]
  (component
    (html
     [:button {:class (remove nil? [(name kind)
                                    (when fixed? "fixed")
                                    (case size
                                      :full nil
                                      :small "small")])
               :title label
               :disabled disabled?
               :on-click on-click}
      content])))

(defn icon
  "An icon button. content should be an icon.

  :label     - An imperative verb which describes the button. This will be used
               as the ARIA label text and as tooltip text.
  :on-click  - A function called when the button is clicked.
  :disabled? - If true, the button is disabled. (default: false)
  :bordered? - (optional) Adds a border to the button."
  [{:keys [label on-click disabled? bordered?]} content]
  (assert label "For usability, an icon button must provide a textual label (as :label).")
  (component
    (html
      [:button {:title label
                :aria-label label
                :class (when bordered? "with-border")
                :disabled disabled?
                :on-click on-click}
       content])))

(defn link
  "A link styled as a button.

  :href          - The link href
  :on-click      - A function called when the link is clicked.
  :kind          - The kind of button. One of #{:primary :secondary :danger :flat}.
                   (default: :secondary)
  :size          - The size of the button. One of #{:full :small}.
                   (default: :full)
  :fixed?        - If true, the button is fixed width. (default: false)
  :target        - Specifies where to display the linked URL.
  :bordered? - (optional) Adds a border to the button."
  [{:keys [href on-click kind size fixed? target bordered?]
    :or {kind :secondary size :full}}
   content]
  (component
    (html
     [:a.exception
      {:class (remove nil? [(name kind)
                            (when fixed? "fixed")
                            (case size
                              :full nil
                              :small "small")
                            (when bordered? "with-border")])
       :href href
       :target target
       :on-click on-click}
      content])))

(defn icon-link
  "A link styled as an icon button.

  :href          - The link href
  :label         - An imperative verb which describes the button. This will be used
                   as the ARIA label text and as tooltip text.
  :on-click      - A function called when the link is clicked.
  :target        - Specifies where to display the linked URL.
  :bordered?      - (optional) Adds a border to the button."
  [{:keys [href label on-click target bordered?]}
   content]
  (assert label "For usability, an icon button must provide a textual label (as :label).")
  (component
    (html
      [:a.exception
       {:title label
        :aria-label label
        :href href
        :class (when bordered? "with-border")
        :target target
        :on-click on-click}
       content])))

(defn managed-button
  "A managed button.

  :on-click     - A function called when the button is clicked.
  :kind         - The kind of button. One of #{:primary :secondary :danger :flat}.
                  (default: :secondary)
  :disabled?    - If true, the button is disabled. (default: false)
  :size         - The size of the button. One of #{:full :small}.
                  (default: :full)
  :fixed?       - If true, the button is fixed width. (default: false)
  :loading-text - Text to display indicating that the button action is in
                  progress. (default: \"...\")
  :success-text - Text to display indicating that the button action was
                  successful. (default: \"Saved\")
  :failed-text  - Text to display indicating that the button action failed.
                  (default: \"Failed\")
  :bordered?    - (optional) Adds a border to the button."
  [{:keys [kind disabled? size fixed? failed-text success-text loading-text on-click bordered?]
    :or {kind :secondary size :full disabled? false}}
   content]
  (forms/managed-button
   ;; Normally, manually adding :data-component is not recommended. We
   ;; make an exception here because `forms/managed-button` takes
   ;; hiccup as an argument instead of an element.
   [:button {:data-component `managed-button
             :data-failed-text failed-text
             :data-success-text success-text
             :data-loading-text loading-text
             :disabled disabled?
             :on-click on-click
             :class (remove nil? [(name kind)
                                  (when fixed? "fixed")
                                  (case size
                                    :full nil
                                    :small "small")
                                  (when bordered? "with-border")])}
    content]))

(dc/do
  (defn button-display [columns]
    (html
     [:div {:style {:display "flex" :justify-content "space-between"}}
      (for [column columns]
        [:div {:style {:margin-right "2em"}}
         (for [button column]
           [:div {:style {:margin-bottom "1em"}}
            button])])]))

  (defcard buttons
    "A **button** represents an action a user can take. A button's label should
    be an action—that is, an imperative verb. Clicking the button initiates that
    action.


    ## Kinds

    A **Primary** button is the main action in a given context. Submit actions,
    save actions, and enable actions would all use a Primary button.

    The exception is the **Danger** button, which is used for destructive
    actions.

    The **Secondary** button is used for other actions.

    The **Flat** button is reserved for non-action actions, mainly Cancel
    buttons. A Flat button is appropriate for backing out of a workflow.


    ## Sizes

    The **Full** size button is the default. A **Small** size is available to
    use in table rows, card headers, and anywhere vertical space is at a
    premium.


    ## Widths

    Button widths vary based on button text length and can have a `fixed?`
    attribute which if set to `true` will make the button fixed width for
    cases where we want buttons to be consistent."

    (button-display
     [;; Primary buttons
      [(button {:kind :primary
                :label "This is a primary button"
                :on-click #(js/alert "Clicked!")}
               "Primary")
       (button {:disabled? true
                :kind :primary
                :label "This is a primary disabled button"
                :on-click #(js/alert "Clicked!")}
               "Primary Disabled")
       (button {:fixed? true
                :kind :primary
                :on-click #(js/alert "Clicked!")}
               "Primary Fixed")
       (button {:kind :primary
                :size :small
                :on-click #(js/alert "Clicked!")}
               "Primary Small")]

      ;; Secondary buttons
      [(button {:on-click #(js/alert "Clicked!")}
               "Secondary")
       (button {:disabled? true
                :on-click #(js/alert "Clicked!")}
               "Secondary Disabled")
       (button {:fixed? true
                :on-click #(js/alert "Clicked!")}
               "Secondary Fixed")
       (button {:size :small
                :on-click #(js/alert "Clicked!")}
               "Secondary Small")]

      ;; Danger buttons
      [(button {:kind :danger
                :on-click #(js/alert "Clicked!")}
               "Danger")
       (button {:disabled? true
                :kind :danger
                :on-click #(js/alert "Clicked!")}
               "Danger Disabled")
       (button {:fixed? true
                :kind :danger
                :on-click #(js/alert "Clicked!")}
               "Danger Fixed")
       (button {:kind :danger
                :size :small
                :on-click #(js/alert "Clicked!")}
               "Danger Small")]

      ;; Flat buttons
      [(button {:kind :flat
                :on-click #(js/alert "Clicked!")}
               "Flat")
       (button {:disabled? true
                :kind :flat
                :on-click #(js/alert "Clicked!")}
               "Flat Disabled")
       (button {:fixed? true
                :kind :flat
                :on-click #(js/alert "Clicked!")}
               "Flat Fixed")
       (button {:kind :flat
                :size :small
                :on-click #(js/alert "Clicked!")}
               "Flat Small")]]))

  (defcard icon-buttons
    "When horizontal space is at a premium, use an **icon button**. These square
    buttons display only a single icon. Like a normal button, an icon button
    requires an imperative verb as its label, but the label is only displayed as
    tooltip text and given to screen readers as the ARIA label.

    Icon buttons are only styled as secondary buttons, and should only be used
    where a secondary button would be appropriate. They come in a single size,
    which matches the Small size of text buttons."

    (button-display
     [;; Icon buttons
      [(icon {:label "Icon"
              :on-click #(js/alert "Clicked!")}
             [:i.octicon.octicon-repo-forked])
       (icon {:label "Icon Disabled"
              :disabled? true
              :on-click #(js/alert "Clicked!")}
             [:i.octicon.octicon-repo-forked])]]))

  (defcard link-buttons
    "A **link-button** looks like a button, but is actually a link.

    A link-button's label, like an ordinary button's, should be an action. Like
    a normal button, clicking it initiates that action. Clicking a link-button
    in particular \"initiates\" the action by navigating to a place in the app
    where the user can continue the action. For instance, \"Add Projects\" is a
    link, because it navigates to the Add Projects page, but it is a link-button
    in particular because it takes the user there to perform the \"Add
    Projects\" action.

    Viewing more information is not an action. \"Build #5\" would not be an
    appropriate label for a link-button; neither would \"View Build #5\".
    Instead, \"Build #5\" should be a normal link."

    (button-display
     [;; Primary link buttons
      [(link {:kind :primary
              :href "#"}
             "Primary Link")
       (link {:fixed? true
              :kind :primary
              :href "#"}
             "Primary Link Fixed")
       (link {:kind :primary
              :href "#"
              :size :small}
             "Small Primary Link")]

      ;; Secondary link buttons
      [(link {:href "#"}
             "Secondary Link")
       (link {:fixed? true
              :href "#"}
             "Secondary Link Fixed")
       (link {:href "#"
              :size :small}
             "Small Secondary Link")]

      ;; Danger link buttons
      [(link {:kind :danger
              :href "#"}
             "Danger Link")
       (link {:fixed? true
              :kind :danger
              :href "#"}
             "Danger Link Fixed")
       (link {:kind :danger
              :href "#"
              :size :small}
             "Small Danger Link")]

      ;; Flat buttons
      [(link {:kind :flat
              :href "#"}
             "Flat Link")
       (link {:fixed? true
              :kind :flat
              :href "#"}
             "Flat Link Fixed")
       (link {:kind :flat
              :href "#"
              :size :small}
             "Small Flat Link")]]))

  (defcard icon-link-button
    "A **icon link-button** is a link-button which displays a single icon, like an
    icon button."
    (button-display
     [[(icon-link {:href "#"
                   :label "Labels are provided for accessibility"}
                  (icon/settings))]]))

  (defcard bordered-buttons
    "Buttons with borders are used in the page header. This is design debt
    [documented in Jira](https://circleci.atlassian.net/browse/CIRCLE-3972)
    and will be addressed when we revisit the page headers."
    (button-display
     [[(managed-button {:bordered? true
                        :size :small}
                       "Bordered Button")
       (managed-button {:fixed? true
                        :bordered? true
                        :size :small}
                       "Bordered Button Fixed")
       (icon {:label "Bordered GitHub icon button"
              :bordered? true}
             (icon/github))
       (icon-link {:href "#"
                   :label "Bordered Bitbucket icon link-button"
                   :bordered? true}
                  (icon/bitbucket))]])))
