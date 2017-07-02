(ns frontend.components.pieces.flash-notification
  (:require [cljs.core.async :refer [<! chan close! put! timeout]]
            [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.pieces.button :as button]
            [frontend.utils.seq :refer [dedupe-by]]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros
   [cljs.core.async.macros :refer [alt! go]]
   [frontend.utils :refer [component element html while-let]]))

(defui
  ^{:doc
    "A single flash notification. It's not the thing that animates, it's the
    thing that's animated."}

  Notification

  Object
  (render [this]
    (component
      (html
       [:div (om-next/children this)]))))

(def flash-notification (om-next/factory Notification))


(def ^:const presenter-animation-duration 300)

(defui
  ^{:doc
    "Presents notifications by animating them onto the screen, one at a time.

    Props:
    :notification    - The current notification to display. Notifications are

                       distinguished by their React key. To display a new
                       notification, set this to an element with a different key
                       from the previous notification.

                       Note: setting this to an element with the same key as the
                       previous notification but different content could
                       reasonably be expected to change the content of the
                       current notification without animating it out. It does
                       not. Instead, nothing happens. Changing notifications in
                       place turned out to be a lot of effort and unlikely to
                       ever be useful. However, if it becomes useful, it can be
                       implemented.

                       Also: ideally this would be passed as a child rather than
                       an explicit prop, but Om's implementation of
                       componentWillReceiveProps (as of alpha41) has no way to
                       access new children.
                       https://github.com/omcljs/om/issues/748

    :display-timeout - Maximum duration to display a notification."}

  Presenter

  Object
  (componentWillMount [this]
    ;; The notification channel dedupes by React key. This means that an update
    ;; to the notification which changes the key is treated as a new
    ;; notification to be displayed, while one which doesn't change the key is
    ;; ignored.
    (set! (.-notificationsChan this) (chan 1 (dedupe-by #(.-key %))))
    (set! (.-clickEventChan this) (chan))

    ;; Put the first notification on the channel.
    (when-let [n (:notification (om-next/props this))]
      (put! (.-notificationsChan this) n))

    ;; Watch the channel. When we want to show a notification, put it in the
    ;; component's state.
    (go

      ;; We'd like to "peek" values on the notification channel, but we can't
      ;; actually do that. Instead, we'll take a value off the notification
      ;; channel and put it onto peek-chan. Then, later, we'll take from
      ;; peek-chan instead of the notification channel if we've put something
      ;; there. It's almost like we peeked the value without taking it.
      (let [peek-chan (chan 1)]

        ;; Take the next notification: from the peek-chan if we "peeked" a
        ;; value, or from the actual notification channel if we haven't.
        ;;
        ;; alts! returns a pair of [val port]; first gives us just the value. If
        ;; that value is nil, that means the channel has closed, and our go
        ;; block should terminate
        (while-let [notification (first (alts! [peek-chan (.-notificationsChan this)]
                                               :priority true))]

          ;; Show the notification we just took off the channel.
          (om-next/update-state! this assoc :notification notification)

          ;; Wait for the notification to animate on.
          (<! (timeout presenter-animation-duration))

          ;; Park here until another notification arrives, the notification is
          ;; clicked, or we time out.
          (alt!
            ;; If we found a new notification, put it onto peek-chan so we'll
            ;; pick it up on the next loop (since we can't put it back on the
            ;; front of the actual notification channel).
            (.-notificationsChan this) ([n] (when n (>! peek-chan n)))

            ;; If the notification is clicked, do nothing, just move on.
            (.-clickEventChan this) nil

            ;; If we time out, do nothing, just move on.
            (timeout (:display-timeout (om-next/props this))) nil)

          ;; Clear the notification, animating it off.
          (om-next/update-state! this assoc :notification nil)

          ;; Wait for the notification to animate off before picking up a new one.
          (<! (timeout presenter-animation-duration))))))

  (componentWillUnmount [this]
    (close! (.-notificationsChan this)))

  (componentWillReceiveProps [this next-props]
    (when-not (= (:notification (om-next/props this))
                 (:notification next-props))
      (put! (.-notificationsChan this) (:notification next-props))))

  (render [this]
    (component
      (js/React.createElement
       js/React.addons.CSSTransitionGroup
       #js {:transitionName "notification"
            :transitionAppear true
            :transitionAppearTimeout presenter-animation-duration
            :transitionEnterTimeout presenter-animation-duration
            :transitionLeaveTimeout presenter-animation-duration}
       (when-let [notification (:notification (om-next/get-state this))]
         (element :notification
           (html
            ;; Wrap the notification in a div we can style (to animate it),
            ;; but "inherit" the notification's key.
            [:div {:key (.-key notification)
                   :on-click #(put! (.-clickEventChan this) %)}
             notification])))))))

(def presenter (om-next/factory Presenter))


(dc/do
  (defcard flash-notification
    (flash-notification {} "Flash notification copy"))

  (defcard presenter
    (fn [state]
      (let [{:keys [counter]} @state]
        (html
         [:div
          [:div {:style {:margin "10px 0"}}
           (button/button {:kind :primary
                           :on-click #(swap! state update :counter inc)}
                          "Show Notification")]
          [:div {:style {:overflow "hidden"}}
           (when-not (zero? counter)
             (presenter {:display-timeout 2000
                         :notification
                         (flash-notification {:react-key counter}
                                             (str "Flash notification " counter))}))]])))
    {:counter 0}))
