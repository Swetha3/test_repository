(ns frontend.components.inspector
  (:require [ankha.core :as ankha]
            [frontend.utils.legacy :refer [build-legacy]]
            [om.next :as om-next :refer-macros [defui]]
            [sablono.core :as html :refer-macros [html]]))

(defn- sort-map-by-pr [m]
  (into (sorted-map-by #(compare (pr-str %1) (pr-str %2))) m))

;; By default, Ankha sorts maps by key, using sorted-map. That fails when the
;; map's keys are themselves maps, because maps are not Comparable. (It also
;; fails for a few other cases similarly.) Instead, here we sort maps by the
;; pr-str representation of their keys. This works for almost all values, keeps
;; the sort order stable, and even orders things the way you'd probably expect
;; to see them.
(extend-protocol ankha/IInspect
  PersistentArrayMap
  (-inspect [this]
    (ankha/coll-view this "{" "}" "map persistent-array-map" sort-map-by-pr))

  PersistentHashMap
  (-inspect [this]
    (ankha/coll-view this "{" "}" "map persistent-hash-map" sort-map-by-pr))

  Subvec
  (-inspect [this] (ankha/-inspect (vec this))))

(defui ^:once Inspector
  Object
  (render [this]
    (html
     [:code (build-legacy ankha/inspector @(om-next/app-state (om-next/get-reconciler this)))])))

(def inspector (om-next/factory Inspector))
