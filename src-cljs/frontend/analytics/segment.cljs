(ns frontend.analytics.segment
  (:require [cljs.core.async :as async :refer  [chan close!]]
            [frontend.async :refer  [put!]]
            [frontend.utils :as utils
             :include-macros true
             :refer [clj-keys-with-dashes->js-keys-with-underscores]]
            [frontend.analytics.common :as common-analytics]
            [schema.core :as s]))

(def SegmentProperties
  {:user (s/maybe s/Str)
   :view s/Keyword
   :org  (s/maybe s/Str)
   :repo (s/maybe s/Str)
   :ab-test-treatments {s/Keyword s/Keyword} ; Different from current user ab-test-treatment on identify
   s/Keyword s/Any})

(def LoggedOutEvent
  (merge
    SegmentProperties
    {:user (s/maybe s/Str)}))

(def UserEvent
  {:id s/Str
   :user-properties common-analytics/UserProperties})

(def IdentifyEvent
  (merge-with merge
              UserEvent
              {:user-properties {:primary-email (s/maybe s/Str)
                                 :ab-test-treatments {s/Keyword s/Keyword}
                                 ; Only updated on identify call - not follow / unfollow events
                                 ;   there will be a lag to update the correct number of projects
                                 ;   that a user follows, but after conversations with our
                                 ;   data analyst (Kyle) this was considered acceptable
                                 :num-projects-followed s/Int}}))

(s/defn track-pageview [navigation-point :- s/Keyword subpage :- s/Keyword & [properties :- SegmentProperties]]
  (utils/swallow-errors
    (js/analytics.page (name navigation-point)
                       (when subpage (name subpage))
                       (clj-keys-with-dashes->js-keys-with-underscores properties))))

(s/defn track-event [event :- s/Keyword & [properties :- SegmentProperties]]
  (utils/swallow-errors
    (js/analytics.track (name event)
                        (clj-keys-with-dashes->js-keys-with-underscores properties))))

(s/defn identify [event-data :- IdentifyEvent]
  (utils/swallow-errors
    (js/analytics.identify (:id event-data) (-> event-data
                                                :user-properties
                                                clj-keys-with-dashes->js-keys-with-underscores))))

(s/defn track-external-click [event :- s/Keyword & [properties :- LoggedOutEvent]]
  (let [ch (chan)]
    (js/analytics.track (name event)
                        (clj-keys-with-dashes->js-keys-with-underscores properties)
                        #(do (put! ch %) (close! ch)))
    ch))
