(ns frontend.components.pieces.top-banner
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.utils :refer-macros [component html]]
            [om.core :as om :include-macros true]))

(defn banner
  "Banners create a colored section on the screen with content informing the
  user of an event that has, will, or should take place. Banners should either
  disappear by themselves, after an event, or be dismissable (have a little (X)
  at the top right corner), and are *not* meant to be permanent interfaces.

  :banner-type                      Can be one of :success, :warning, or :danger,
                                    determines the background color of the banner.

  :impression-event-type-event-type (optional) The event banner-type which will
                                    be tracked when this banner displays. If not
                                    given, no event will be tracked.

  :content                          Content to go inside the banner.

  :dismiss-fn                       (optional) A function which will be called
                                    when the banner's dismiss button (X) is
                                    clicked. If not given, no dismiss button
                                    will be shown."

  [{:keys [banner-type content impression-event-type dismiss-fn]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (when (not (nil? impression-event-type))
        ((om/get-shared owner :track-event) {:event-type impression-event-type})))
    om/IRender
    (render [_]
      (component
        (html
          [:div {:class banner-type}
           [:div.text
            content
            (when (not (nil? dismiss-fn))
              [:a.banner-dismiss
               {:on-click dismiss-fn}
               [:i.material-icons "clear"]])]])))))

(dc/do
  (defcard banners
    (html
      [:div
       (om/build banner {:banner-type "success"
                         :content [:span "A success banner."]
                         :impression-event-type nil
                         :dismiss-fn nil
                         :owner nil})
       (om/build banner {:banner-type "warning"
                         :content [:span "A warning banner."]
                         :impression-event-type nil
                         :dismiss-fn nil
                         :owner nil})
       (om/build banner {:banner-type "danger"
                         :content [:span "A dangerous banner!"]
                         :impression-event-type nil
                         :dismiss-fn nil
                         :owner nil})]))
  (defcard green-banner-dismissable
    (om/build banner {:banner-type "success"
                      :content [:span "Some inner content for a banner!"]
                      :impression-event-type nil
                      :dismiss-fn #(.log js/console "Faux dimissal function.")
                      :owner nil})))
