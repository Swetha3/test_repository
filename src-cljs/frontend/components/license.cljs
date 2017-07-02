(ns frontend.components.license
  (:require [om.core :as om :include-macros true]
            [cljs-time.coerce :as t-coerce]
            [cljs-time.core :as t]
            [frontend.utils :refer-macros [html]]))

(defn- days-until-api-time
  "Takes a string representing a time in the format the API delivers it and
  returns the number of days until now."
  [api-date-string]
  (->> api-date-string
       t-coerce/from-string
       (t/interval (t/now))
       t/in-days))

(defn- banner-contents
  "Returns the contents of a license banner.
  * indicator-text - Text to go in the little indicator pill.
  * message - The main message in the banner.
  * stat (optional) - A stat to inform the user of their usage. A map including:
    * :count - The number to show.
    * :nouns - A pair of [singular plural] versions of the noun to follow the count.
    * :description - Further description to follow the noun."
  ([indicator-text message] (banner-contents indicator-text message nil))
  ([indicator-text message stat]
   (list
    [:.indicator indicator-text]
    [:.message message]
    (when-let [{[singular plural] :nouns :keys [count description]} stat]
      [:.stat
       [:span.count count]
       " "
       (if (= 1 count) singular plural)
       (when description
         (list
          " "
          description))])
    [:a.contact-sales.btn
     {:href "mailto:enterprise@circleci.com"}
     "Contact Sales"])))

(defn- banner-for-license
  "Returns the banner for the given license, or nil if no banner applies."
  [license]
  (let [license-type (:type license)
        seat (:seat_status license)
        expiry (:expiry_status license)
        contents
        (cond
          (and (= license-type "trial")
               (= expiry "current"))
          (banner-contents "Trial Account"
                           "Welcome to CircleCI."
                           {:count (days-until-api-time (:expiry_date license))
                            :nouns ["day" "days"]
                            :description "left in trial"})
          (and (= license-type "trial")
               (= expiry "in-violation"))
          (banner-contents "Trial Expired"
                           "Your trial period has expired."
                           {:count (days-until-api-time (:hard_expiry_date license))
                            :nouns ["day" "days"]
                            :description "until suspension"})
          (and (= license-type "trial")
               (= expiry "expired"))
          (banner-contents "Trial Ended"
                           "Your builds have been suspended. Contact Sales to reactivate your builds.")

          (and (= license-type "paid")
               (= expiry "in-violation"))
          (banner-contents "License Expired"
                           "Your license has expired. Contact Sales to renew."
                           {:count (days-until-api-time (:hard_expiry_date license))
                            :nouns ["day" "days"]
                            :description "until suspension"})

          (and (= license-type "paid")
               (= expiry "expired"))
          (banner-contents "License Suspended"
                           "Your builds have been suspended. Contact Sales to reactivate your builds.")

          ;; Seat violation is the least of priorities - if they are expired or trialing,
          ;; warn them about expiry/trial instead
          (and (= license-type "paid")
               (not= seat "current"))
          (banner-contents "License Exceeded"
                           "Please contact Sales to upgrade your license."
                           {:count (str (:seats_used license) "/" (:seats license))
                            :nouns ["user" "users"]})
          :else nil)]
    (when contents
      [:.license-banner {:class [(str "type-" (:type license))
                                 (str "seat-" (:seat_status license))
                                 (str "expiry-" (:expiry_status license))]}
       contents])))

(defn show-banner?
  "Should we show a banner for the given license?"
  [license]
  (boolean (banner-for-license license)))

(defn license-banner [license owner]
  (reify
    om/IDisplayName (display-name [_] "License Banner")
    om/IRender
    (render [_]
      (html
       (banner-for-license license)))))
