(ns frontend.analytics.utils)

(defn canonical-plan-type
  "Stop gap until we switch :paid to :linux"
  [plan-type]
  (if (= plan-type :paid)
    :linux
    plan-type))
