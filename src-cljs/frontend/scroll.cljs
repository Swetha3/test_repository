(ns frontend.scroll
  (:require [frontend.disposable :as disposable]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]
            [goog.events]))

(defn register
  ([owner cb scrollable-container]
   (om/set-state! owner ::scroll-id
                  (disposable/register
                   (goog.events/listen
                    scrollable-container
                    "scroll"
                    (fn [event]
                      ;; FIXME: Dispose doesn't actually remove the event listeners
                      ;; so we end up leaking event handlers that survive their components
                      ;; and throw interesting errors when events are triggered against unmounted components

                      ;; see: https://github.com/omcljs/om/issues/332
                      (when (om/mounted? owner)
                        (utils/swallow-errors (cb event)))))
                   (fn dispose [event-key]
                     (goog.events/unlistenByKey event-key)))))
  ([owner cb]
   (register owner cb js/window)))

(defn dispose [owner]
  (disposable/dispose (om/get-state owner ::scroll-id)))
