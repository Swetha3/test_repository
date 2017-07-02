(ns frontend.analytics
  (:require [frontend.analytics.core :as analytics]
            [om.next :as om-next]))

(defprotocol Properties
  (properties [this]
    "Returns a map of analytics properties to be used with events tracked within
    this component."))

(extend-type default
  Properties
  (properties [_] {}))


(defn event
  "Builds a complete analytics event for a component. component is the current
  component. evt is an event. The event will be given the properties specified
  by the component and by components up its parent chain, with children's
  properties overriding parents'."
  [component evt]
  (if-not component
    evt
    (recur (om-next/parent component)
           (update evt :properties #(merge (properties component) %)))))

(defn track!
  "Track an analytics event for a component."
  [component evt]
  (analytics/track (event component evt)))
