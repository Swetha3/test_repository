(ns frontend.models.plan
  (:require [frontend.utils :as utils :include-macros true]
            [goog.string :as gstring]
            [frontend.datetime :as datetime]
            [cljs-time.core :as time]
            [cljs-time.format :as time-format]))

;; The template tells how to price the plan
;; TODO: fix this to work from the backend db; how?
(def default-template-properties {:price 0 :container_cost 50 :id
                                  "p18" :max_containers 1000 :free_containers 0})

(def oss-containers 3)

(def osx-plan-type "osx")
(def linux-plan-type "linux")

(def osx-key :osx)
(def linux-key :paid)

(def github-marketplace-id :gh-marketplace)

(def student-trial-template "st2")
(def osx-trial-template "osx-trial")

(defn max-parallelism
  "Maximum parallelism that the plan allows (usually 16x)"
  [plan]
  (:max_parallelism plan))

(defn piggieback? [{{plan-org-name :name
                     plan-vcs-type :vcs_type} :org}
                   org-name
                   vcs-type]
  (or (not= plan-org-name org-name)
      (not= plan-vcs-type vcs-type)))

(defn freemium? [plan]
  (boolean (:free plan)))

(defn linux? [plan]
  (boolean (and
             (:paid plan)
             (pos? (:containers plan)))))

(defn osx? [plan]
  (boolean (:osx plan)))

(defn osx-template [plan]
  (-> plan :osx :template))

(defn linux-template [plan]
  (-> plan :paid :template))

(defn linux-template-id [plan]
  (get-in plan [:paid :template :id]))

(defn osx-plan-id [plan]
  (get-in plan [:osx :template :id]))

(defn linux-trial-plan-id [plan]
  (get-in plan [:trial :template :id]))

(defn github-marketplace-plan? [plan]
  (-> plan
      linux-template-id
      keyword
      (= github-marketplace-id)))

(defn osx-trial-plan? [plan]
  (= osx-trial-template (osx-plan-id plan)))

(defn osx-trial-active? [plan]
  (and (osx? plan)
       (some-> plan :osx_trial_active)))

(defn trial? [plan]
  (boolean (:trial plan)))

(defn trial-over? [plan]
  (when (trial? plan)
    (or (nil? (:trial_end plan))
        (time/after? (time/now) (time-format/parse (:trial_end plan))))))

(defn in-trial? [plan]
  (and (trial? plan) (not (trial-over? plan))))

(defn in-student-trial? [plan]
  (and (in-trial? plan)
       (-> plan
           linux-trial-plan-id
           (= student-trial-template))))

(defn enterprise? [plan]
  (boolean (:enterprise plan)))

(defn suspended? [plan]
  (boolean (some-> plan :paid :suspended)))

(defn freemium-containers [plan]
  (or (get-in plan [:free :template :free_containers]) 0))

(defn has-had-osx-plan?
  "True if this plan has ever had a :osx trial or :osx plan."
  [plan]
  (and
    (:osx_plan_started_on plan)
    (:osx_trial_end_date plan)))

(defmulti trial-eligible?
  "Is this plan eligable for a trial for this plan-type."
  (fn [plan plan-type]
    plan-type))

(defmethod trial-eligible? osx-key
  [plan plan-type]
  (not (has-had-osx-plan? plan)))

(defmethod trial-eligible? linux-key
  [plan plan-type]
  (not (trial? plan)))

(defn paid-linux-containers [plan]
  (if (linux? plan)
    (max (:containers_override plan)
         (:containers plan)
         (get-in plan [:paid :template :free_containers]))
    0))

(defn trial-containers
  "How many containers are provided by this plan's trial? Note that this finds out how many
  containers were provided by expired trials, too. You'll need to guard by `in-trial?` if you
  don't want this behavior."
  [plan]
  (max 0
       (get-in plan [:trial :template :free_containers] 0)))

(defn enterprise-containers [plan]
  (if (:enterprise plan)
    (:containers plan)
    0))

(defn linux-containers
  "Maximum containers that a linux plan has available to it"
  [plan]
  (+ (freemium-containers plan)
     (if (:enterprise plan)
       (enterprise-containers plan)
       (paid-linux-containers plan))
     (trial-containers plan)))

(defn transferrable-or-piggiebackable-plan? [plan]
  (and (not (enterprise? plan))
       (or (linux? plan)
           (osx? plan)
           ;; Trial plans shouldn't really be transferrable
           ;; but they are piggiebackable and the UI mixes the two :(
           (trial? plan))))

;; true  if the plan has an active Stripe discount coupon.
;; false if the plan is nil (not loaded yet) or has no discount applied
(defn has-active-discount? [plan]
  (get-in plan [:discount :coupon :valid]))

(defn days-left-in-trial
  "Returns number of days left in trial, can be negative."
  [plan]
  (let [trial-end (when (:trial_end plan)
                    (time-format/parse (:trial_end plan)))
        now (time/now)]
    (when (not (nil? trial-end))
      (if (time/after? trial-end now)
        ;; count partial days as a full day
        (inc (time/in-days (time/interval now trial-end)))
        (- (time/in-days (time/interval trial-end now)))))))

(defn pretty-trial-time [plan]
  (when-not (nil? (:trial_end plan))
    (let [trial-interval (time/interval (time/now) (time-format/parse (:trial_end plan)))
          hours-left (time/in-hours trial-interval)]
      (cond (< 24 hours-left)
            (str (days-left-in-trial plan) " days")

            (< 1 hours-left)
            (str hours-left " hours")

            :else
            (str (time/in-minutes trial-interval) " minutes")))))

(defn linux-per-container-cost [plan]
  (or (-> plan :paid :template :container_cost)
      (-> plan :enterprise :template :container_cost)
      (:container_cost default-template-properties)))

(defn linux-container-cost [plan containers]
  (let [template-properties (or (-> plan :paid :template)
                                (-> plan :enterprise :template)
                                default-template-properties)
        {:keys [free_containers container_cost]} template-properties]
    (max 0 (* container_cost (- containers free_containers)))))

(defn linux-cost [plan containers & {:keys [include-trial?]}]
  (let [paid-plan-template (or (-> plan :paid :template)
                               (-> plan :enterprise :template)
                               default-template-properties)
        plan-base-price (:price paid-plan-template)
        linux-containers (- containers
                                (freemium-containers plan)
                                (if-not include-trial?
                                  0
                                  (trial-containers plan)))]
    (if (< linux-containers 1)
      0
      (+ plan-base-price
         (linux-container-cost plan linux-containers)))))

(defn stripe-cost
  "Normalizes the Stripe amount on the plan to dollars."
  [plan]
  (/ (:amount plan) 100))

(defn osx-cost
  [plan]
  (or (some-> plan :osx :template :price) 0))

(defn current-linux-cost
  [plan]
  (- (stripe-cost plan) (osx-cost plan)))

(defn grandfathered? [plan]
  (and (linux? plan)
       (< (stripe-cost plan)
          (linux-cost plan (linux-containers plan) :include-trial? true))))

(defn admin?
  "Whether the logged-in user is an admin for this plan."
  [plan]
  (boolean (:admin plan)))

(defn latest-billing-period [plan]
  (->> plan
       :billing_periods
       (map (fn [periods]
              (for [period periods]
                (time-format/parse (time-format/formatters :date-time) period))))
       (sort-by first time/after?)
       (first)))

(defn usage-key->date [usage-key]
  (time-format/parse (time-format/formatter "yyyy_MM_dd") (name usage-key)))

(defn date->usage-key [date]
  (keyword (time-format/unparse (time-format/formatter "yyyy_MM_dd") date)))

(defn current-months-osx-usage-ms [plan]
  (let [[period-start period-end] (latest-billing-period plan)]
    (when (and period-start period-end)
      (if (time/within? (time/interval period-start period-end) (time/now))
        (get-in plan [:usage :os:osx (date->usage-key period-start) :amount])
        0))))

(defn current-months-osx-usage-% [plan]
  (let [usage-ms (current-months-osx-usage-ms plan)
        usage-min (/ usage-ms 1000 60)]
    (if-let [max-min (-> plan :osx :template :max_minutes) ]
      (.round js/Math (* (/ usage-min max-min) 100))
      0)))

(def first-warning-threshold 75)
(def second-warning-threshold 95)
(def third-warning-threshold 100)
(def future-warning-threshold-increment 10)

(defn over-usage-threshold? [plan threshold-percent]
  (>= (current-months-osx-usage-% plan)
      threshold-percent))

(defn over-dismissed-level? [plan dismissed-osx-usage-level]
  (>= (current-months-osx-usage-% plan)
      dismissed-osx-usage-level))

(defn stripe-customer?
  [plan]
  (boolean (:stripe_customer plan)))

(def osx-plans
  {:seed {:plan-id :seed
          :title "SEED"
          :price 39
          :container-count "2x"
          :daily-build-count "1-5"
          :max-minutes "500"
          :support-level "Community support"
          :team-size "1-2"}

   :startup {:plan-id :startup
             :title "STARTUP"
             :price 129
             :container-count "5x"
             :daily-build-count "5-10"
             :max-minutes "1,800"
             :support-level "Engineer support"
             :team-size "unlimited"
             :updated-selection? true}

   :growth {:plan-id :growth
            :title "GROWTH"
            :price 249
            :container-count "7x"
            :daily-build-count "10-30"
            :max-minutes "5,000"
            :support-level "Engineer support"
            :team-size "unlimited"
            :trial-starts-here? true}

   :mobile-focused {:plan-id :mobile-focused
                    :title "MOBILE FOCUSED"
                    :price 449
                    :container-count "12x"
                    :daily-build-count "more than 20"
                    :max-minutes "25,000"
                    :support-level "Priority support & Account manager"
                    :team-size "unlimited"}})

(defn osx-trial-days-left [plan]
  (let [trial-end (some-> plan :osx_trial_end_date)]
    (if (<= (.getTime (time/now)) (.getTime (js/Date. trial-end)))
      (datetime/time-ago (time/in-millis (time/interval (time/now) (js/Date. trial-end))))
      "0 days")))

(defn github-personal-org-plan? [user plan]
  (let [github-identity (get-in user [:identities :github])]
    (and (:user? github-identity)
         (= (:login github-identity) (:org_name plan)))))
