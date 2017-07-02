(ns frontend.utils.map)

(defn map-vals
  "Calls f, a fn of one arg on each value in m. Returns a new map"
  [f m]
  (->> m
       (map (fn [pair]
              [(key pair) (f (val pair))]))
       (into {})))

(defn coll-to-map [k coll]
  "Given a key k, and a collection coll, return a map where the key
  is (k item-of-coll) and the value is item-of-coll."
  (->> coll
       (map (fn [item]
              [(get item k) item]))
       (into {})))
