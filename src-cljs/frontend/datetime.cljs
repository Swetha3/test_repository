(ns frontend.datetime
  (:require [cljs-time.coerce :as time-coerce :refer [from-long]]
            [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            [clojure.string :as str]
            [frontend.utils :as utils :include-macros true]
            [goog.date.DateTime]
            [goog.i18n.DateTimeFormat.Format :as date-formats]
            [goog.string :as g-string]
            goog.string.format))

(defn now []
  (.getTime (js/Date.)))

(defn unix-timestamp []
  (int (/ (now) 1000)))

(def full-date-format
  (goog.i18n.DateTimeFormat. date-formats/FULL_DATE))

(def full-datetime-format
  (goog.i18n.DateTimeFormat. date-formats/FULL_DATETIME))

(def full-time-format
  (goog.i18n.DateTimeFormat. date-formats/FULL_TIME))

(def long-date-format
  (goog.i18n.DateTimeFormat. date-formats/LONG_DATE))

(def long-datetime-format
  (goog.i18n.DateTimeFormat. date-formats/LONG_DATETIME))

(def long-time-format
  (goog.i18n.DateTimeFormat. date-formats/LONG_TIME))

(def medium-date-format
  (goog.i18n.DateTimeFormat. date-formats/MEDIUM_DATE))

(def medium-datetime-format
  (goog.i18n.DateTimeFormat. date-formats/MEDIUM_DATETIME))

(def medium-time-format
  (goog.i18n.DateTimeFormat. date-formats/MEDIUM_TIME))

(def short-date-format
  (goog.i18n.DateTimeFormat. date-formats/SHORT_DATE))

(def short-datetime-format
  (goog.i18n.DateTimeFormat. date-formats/SHORT_DATETIME))

(def short-time-format
  (goog.i18n.DateTimeFormat. date-formats/SHORT_TIME))

(defn format-date [date-format date]
  (.format date-format (js/Date. date)))

(def full-date
  (partial format-date full-date-format))

(def full-datetime
  (partial format-date full-datetime-format))

(def full-time
  (partial format-date full-time-format))

(def long-date
  (partial format-date long-date-format))

(def long-datetime
  (partial format-date long-datetime-format))

(def long-time
  (partial format-date long-time-format))

(def medium-date
  (partial format-date medium-date-format))

(def medium-datetime
  (partial format-date medium-datetime-format))

(def medium-time
  (partial format-date medium-time-format))

(def short-date
  (partial format-date short-date-format))

(def short-datetime
  (partial format-date short-datetime-format))

(def short-time
  (partial format-date short-time-format))

(def medium-consistent-date-format
  (goog.i18n.DateTimeFormat. "MMM dd, yyyy"))

(def medium-consistent-date
  (partial format-date medium-consistent-date-format))

(def calendar-date-format
  (goog.i18n.DateTimeFormat. "EEE, MMM dd, yyyy 'at' hh:mma"))

(def calendar-date
  (partial format-date calendar-date-format))

(def year-month-day-date-format
  (goog.i18n.DateTimeFormat. "yyyy/MM/dd"))

(def year-month-day-date
  (partial format-date year-month-day-date-format))

(def month-name-day-date-format
  (goog.i18n.DateTimeFormat. "MMM dd"))

(def month-name-day-date
  (partial format-date month-name-day-date-format))

(defn date->month-name [date]
  (time-format/unparse (time-format/formatter "MMMM") date))

(defn date-in-ms [date]
  (let [[y m d] (map js/parseInt (.split (name date) #"-"))]
    (.getTime (js/Date. (js/Date.UTC y (dec m) (dec d) 0 0 0)))))

(def day-in-ms
  (* 1000 3600 24))

; Units of time in seconds
(def minute
  60)

(def hour
  (* minute 60))

(def day
  (* hour 24))

(def month
  (* day 30))

(def year
  (* month 12))


(def full-time-units
  {:seconds ["second" "seconds"]
   :minutes ["minute" "minutes"]
   :hours ["hour" "hours"]
   :days ["day" "days"]
   :months ["month" "months"]
   :years ["year" "years"]})

(def abbreviated-time-units
  {:seconds ["sec" "sec"]
   :minutes ["min" "min"]
   :hours ["hr" "hr"]
   :days ["day" "days"]
   :months ["month" "months"]
   :years ["year" "years"]})

(defn format-duration [duration-ms units]
  (let [ago (max (.floor js/Math (/ duration-ms 1000)) 0)
        [divisor unit] (cond
                         (< ago minute) [1 :seconds]
                         (< ago hour) [minute :minutes]
                         (< ago day) [hour :hours]
                         (< ago month) [day :days]
                         (< ago year) [month :months]
                         :else [year :years])
        [singular plural] (units unit)
        time-count (.round js/Math (/ ago divisor))]
    (str time-count " "  (if (= 1 time-count) singular plural))))

(defn time-ago [duration-ms]
  (format-duration duration-ms full-time-units))

(defn time-ago-abbreviated [duration-ms]
  (format-duration duration-ms abbreviated-time-units))

(defn as-duration [duration]
  (if (neg? duration)
    (do (utils/mwarn "got negative duration" duration "returning 00:00")
        "00:00")
    (let [seconds (js/Math.floor (/ duration 1000))
          minutes (js/Math.floor (/ seconds 60))
          hours (js/Math.floor (/ minutes 60))
          display-seconds (g-string/format "%02d" (mod seconds 60))
          display-minutes (g-string/format "%02d" (mod minutes 60))]
      (if (pos? hours)
        (g-string/format "%s:%s:%s" hours display-minutes display-seconds)
        (g-string/format "%s:%s" display-minutes display-seconds)))))

(defn as-time-since [date-string]
  (let [time-stamp (.getTime (js/Date. date-string))
        zone-time (time/to-default-time-zone (from-long time-stamp))
        now (now)
        ago (js/Math.floor (/ (- now time-stamp) 1000))]
    (cond (< ago minute) "just now"
          (< ago hour) (str (int (/ ago minute)) " min ago")
          (< ago day) (time-format/unparse (time-format/formatter "h:mm a") zone-time)
          (< ago (* 2 day)) "yesterday"
          (< ago year) (time-format/unparse (time-format/formatter "MMM d") zone-time)
          :else (time-format/unparse (time-format/formatter "MMM yyyy") zone-time))))

(def millis-factors [{:unit :hours
                      :display "h"
                      :divisor (* 60 1000 60)}
                     {:unit :minutes
                      :display "m"
                      :divisor (* 1000 60)}
                     {:unit :seconds
                      :display "s"
                      :divisor 1000}
                     {:unit :milliseconds
                      :display "ms"
                      :divisor 1}])

(defn millis-to-float-duration
  "Return vector of [nice-string unit-information] e.g.
[\"12.4ms\" {:unit :milliseconds
             :display \"ms\"
             :divisor 1}]"
  ([millis]
   (millis-to-float-duration millis {}))
  ([millis {requested-unit :unit decimals :decimals :or {decimals 1}}]
   (let [{:keys [display divisor unit] :as unit-info}
         (or (some #(when (= requested-unit (:unit %))
                      %)
                   millis-factors)
             (some #(when (>= (/ millis (:divisor %)) 1)
                      %)
                   millis-factors)
             (last millis-factors))
         result (float (/ millis divisor))]
     (vector (if (= 0 result)
               "0"
               (g-string/format (str "%." decimals "f%s")
                                result
                                display))
             unit-info))))

(defn millis-to-nice-duration
  "Formats millis as a string that looks like \"10h 10m 10s\""
  [millis]
  (if (< millis 1000)
    "0"
    (let [result (loop [;; floor millis to seconds
                        millis (* 1000 (js/Math.floor (/ millis 1000)))
                        [{:keys [divisor display] :as current} & remaining-factors] millis-factors
                        acc []]
                        (if divisor
                          (let [whole-result (quot millis divisor)
                                remainder (rem millis divisor)
                                ;; don't add leading 0's to result
                                next-acc (if (and (empty? acc) (zero? whole-result))
                                           acc
                                           (conj acc [whole-result display]))]
                            (if (> remainder 0)
                              (recur remainder remaining-factors next-acc)
                              next-acc))
                          acc))]
           (str/join " " (map (fn [[num unit]]
                                (str num unit)) result)))))

(defn nice-floor-duration
  "Returns millis floored to a nice value for printing."
  [millis]
  (let [[_ {:keys [divisor]}] (millis-to-float-duration millis)]
    (* (js/Math.floor (/ millis divisor))
       divisor)))

(defn iso-to-unix
  "Takes an ISO 8601 timestamp and returns the equivalent as seconds after the Unix epoch."
  [iso]
  (time-coerce/to-epoch (time-format/parse (time-format/formatters :date-time) iso)))

(defn iso-comparator
  "Compares two ISO 8601 timestamps"
  [t1 t2]
  (> (iso-to-unix t1) (iso-to-unix t2)))
