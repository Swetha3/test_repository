(ns frontend.components.test-org-settings
  (:require [cljs.core.async :as async]
            [frontend.test-utils :as test-utils :refer [example-plan is-re]]
            [frontend.components.org-settings :as org-settings]
            [frontend.utils.docs :as doc-utils]
            [frontend.utils.html :refer [hiccup->html-str]]
            [frontend.stefon :as stefon]
            [goog.dom]
            [om.core :as om :include-macros true]
            [frontend.routes :as routes]
            [om.dom :refer (render-to-str)]
            [cljs.test :as test :refer-macros [deftest is testing]]))

(deftest test-discount-rendering
  (let [fifty-percent {:discount { :coupon {
                                     :id                 "qJayHMis"
                                     :percent_off        1
                                     :amount_off         nil
                                     :duration           "forever"
                                     :duration_in_months nil
                                     :valid              true }}}
        sweety-high {:discount {:coupon {
                                     :max_redemptions 1,
                                     :valid false,
                                     :amount_off nil,
                                     :duration_in_months 3,
                                     :created 1390517901,
                                     :duration "repeating",
                                     :redeem_by nil,
                                     :currency nil,
                                     :percent_off 15,
                                     :id "sweety-high-discount",
                                     :times_redeemed 1,
                                     :livemode true,
                                     :metadata {},
                                     :object "coupon"}}}
        blog-post {:discount {:coupon {
                                     :max_redemptions nil,
                                     :valid true,
                                     :amount_off 10000,
                                     :duration_in_months 1,
                                     :created 1406670715,
                                     :duration "repeating",
                                     :redeem_by nil,
                                     :currency "usd",
                                     :percent_off nil,
                                     :id "blog-post",
                                     :times_redeemed 1,
                                     :livemode true,
                                     :metadata {},
                                     :object "coupon"}}}]

    (is (=  [:p "Your plan includes 1% off forever from coupon code " [:strong "qJayHMis"]]
            (org-settings/format-discount fifty-percent)))
    (is (=  [:p "Your plan includes $100.00 off for 1 month from coupon code " [:strong "blog-post"]]
            (org-settings/format-discount blog-post)))
    (is (=  [:p "Your plan includes 15% off for 3 months from coupon code " [:strong "sweety-high-discount"]]
            (org-settings/format-discount sweety-high)))))
