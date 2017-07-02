(ns frontend.models.repo
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [frontend.datetime :as datetime]
            [goog.string :as gstring]
            goog.string.format))

(defn building-on-circle? [repo]
  (or (:following repo)
      (:has_followers repo)))

(defn should-do-first-follower-build? [repo]
  (and (not (building-on-circle? repo))
       (:admin repo)))

(defn requires-invite? [repo]
  (and (not (building-on-circle? repo))
       (not (:admin repo))))

(defn can-follow? [repo]
  (and (not (:following repo))
       (or (:admin repo)
           (building-on-circle? repo))))

(defn likely-osx-repo? [repo]
  ;; GH "Swift" "Objective-C"
  ;; BB "swift" "objective-c"
  (let [osx-languages #{"swift" "objective-c"}]
    (boolean
     (some->> (:language repo)
              str/lower-case
              (contains? osx-languages)))))

(defn id [repo]
  (:vcs_url repo))
