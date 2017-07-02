(ns frontend.support
  (:require [frontend.config :as config]
            [frontend.utils :as utils :include-macros true]
            [frontend.intercom :as intercom]
            [frontend.elevio :as elevio]
            [frontend.zendesk :as zendesk]
            [goog.dom :as gdom]))

(defn support-widget []
  (cond (config/zd-widget-enabled?) :zd
        (config/elevio-enabled?) :elevio
        :else :intercom))

(defn raise-dialog [ch]
  (try
    (case (support-widget)
      :zd (zendesk/show!)
      :elevio (elevio/show-support!)
      :intercom (js/Intercom "show"))
    (catch :default e
      (utils/notify-error ch "Uh-oh, our Help system isn't available. Please email us instead, at sayhi@circleci.com")
      (utils/merror e))))

(defn enable-one!
  "Enables zendesk widget, elevio, or Intercom, depending on LD flags"
  [initial-user-data]
  (case (support-widget)
    :zd (elevio/disable!)
    :elevio (do
              (intercom/enable!)
              (elevio/enable! initial-user-data))
    ;; default
    :intercom (do
                (elevio/disable!)
                (intercom/enable!))))
