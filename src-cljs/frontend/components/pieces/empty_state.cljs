(ns frontend.components.pieces.empty-state
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.icon :as icon]
            [frontend.utils :refer-macros [component html]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.bitbucket :as bb-utils]
            [om.core :as om :include-macros true]))

(defn important
  "An Empty State heading should contain (generally) one important term (often a
  single word), which helps the user understand the context of the Empty State."
  [term]
  (component
    (html [:span term])))

(defn empty-state
  "An Empty State appears in place of a list or other content when that content
  does not exist. For instance, on the Projects page, when there are no projects
  to show, we show an Empty State explaining why and how to change that.

  :icon       - An icon which signals what kind of thing is missing. This may
                be a literal icon, which should appear correctly without
                additional style, or some sort of image, which may require
                additional style. The height of the icon should equal the
                font-size it inherits (that is, 1em).

  :heading    - The first, bigger text shown below the icon. The heading
                typically declares why an Empty State is shown rather than any
                actual content (\"Segmentio has no project building on
                CircleCI\"). It should be a single sentence, in sentence case,
                and should not end with a period.

  :subheading - The secondary text below the heading. The subheading typically
                explains what the user can do to add content and therefore make
                the Empty State go away, along with any other info the user may
                need. It should be a single sentence, and it should end with a
                period.

  :action     - (optional) The action (or actions) a user may wish to take to
                make the Empty State go away. This is typically a single,
                primary button, if present. It may also be multiple buttons."
  [{:keys [icon heading subheading action]}]
  (component
    (html
     [:div
      [:.icon icon]
      [:.heading heading]
      [:.subheading subheading]
      (when action
        [:.action action])])))

(defn avatar-icons
  "Displays up to three avatars, overlapped. Suitable to be used as the :icon of
  an empty-state.

  avatar-urls - A collection of up to three avatar image URLs."
  [avatar-urls]
  {:pre [(>= 3 (count avatar-urls))]}
  (component
    (html
     [:div
      (for [avatar-url avatar-urls]
        [:img {:src avatar-url}])])))

(defn- empty-state-header [{:keys [name icon description]}]
  (component
    (html
      [:div
       (card/basic
         [:.header
          [:.icon-container [:.icon icon]]
          [:h1 [:b name]]
          [:p.description-text description]])])))

(defn- empty-state-demo-card [{:keys [demo-heading demo-description]}]
  (component
    (html
      [:div
       (card/basic
         [:div
          [:h4 [:b demo-heading]]
          [:div demo-description]])])))

(defn- empty-state-footer [_ owner]
  (let [track-auth-button-clicked
        (fn [vcs-type] ((om/get-shared owner :track-event)
                        {:event-type :empty-state-auth-button-clicked
                         :properties {:vcs-type vcs-type}}))]
    (reify
      om/IRender
      (render [_]
        (component
         (html
          [:div
           (card/basic
             [:div
              [:h4 [:b "Authorize CircleCI to Build"]]
              [:p "To automate your software builds and tests with CircleCI, connect to your GitHub or Bitbucket code repository."]
              [:.ctas
               (button/link {:href (gh-utils/auth-url)
                             :kind :secondary
                             :on-click #(track-auth-button-clicked :github)}
                            "Authorize GitHub")
               (button/link {:href (bb-utils/auth-url)
                             :kind :secondary
                             :on-click #(track-auth-button-clicked :bitbucket)}
                            "Authorize Bitbucket")]])]))))))

(defn full-page-empty-state
  [{:keys [content demo-heading demo-description]
    :as page-info}
   owner]
  "A full-page component shown to users who have no code identities (gh, bb).
   It contains a header with the page name, icon and a description, a footer
   with CTAs to add a code identity, some static demo content for the page, and
   an optional demo card to further explain the purpose of the page.

   :content
    The element to display as the main demo content for the page.
    Ideally a card component.

   :name
    The name of the page this empty state is standing in for.

   :icon
    An icon that represents this page.
    Should be the same icon used for this page on the nav bar.

   :description
    A more detailed description of what this page is used for.

   :demo-heading (optional)
    A heading for the optional demo card to be included below the header.
    Use the demo card to further explain the contents of the page.

   :demo-description (optional)
    A description for the optional demo card to be included below the header.
    Use the demo card to further explain the contents of the page."
  (reify
    om/IDidMount
    (did-mount [_]
      ((om/get-shared owner :track-event) {:event-type :empty-state-impression}))
    om/IRender
    (render [_]
      (component
        (html
          [:div
           (card/collection
             [(empty-state-header page-info)
              (when (or demo-heading demo-description)
                (empty-state-demo-card page-info))
              content
              (om/build empty-state-footer {})])])))))

(dc/do
  (defcard empty-state
    (empty-state {:icon (icon/project)
                  :heading (html
                            [:span
                             (important "bakry")
                             " has no projects building on CircleCI"])
                  :subheading "Let's fix that by adding a new project."
                  :action (button/button {:kind :primary} "Add Project")}))

  (defcard empty-state-with-avatars
    ;; Images created with `convert -size 1x1 canvas:red gif:- | base64` (then blue and green).
    (empty-state {:icon (avatar-icons
                         ["data:image/gif;base64,R0lGODlhAQABAPAAAP8AAAAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw=="
                          "data:image/gif;base64,R0lGODlhAQABAPAAAACAAAAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw=="
                          "data:image/gif;base64,R0lGODlhAQABAPAAAAAA/wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw=="])
                  :heading (html
                            [:span
                             "Get started by selecting your "
                             (important "favorite color")])
                  :subheading "Select a color to learn more about it."}))

  (defcard empty-state-header
           (empty-state-header {:name "Insights"
                                :icon (icon/insights)
                                :description "CircleCI insights give performance overviews of your projects and offer ideas about how to increase build speeds. Experience insights by clicking on a test project or creating one of your own."}))

  (defcard empty-state-footer
           (om/build empty-state-footer {})))
