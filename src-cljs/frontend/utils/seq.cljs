(ns frontend.utils.seq)

(defn find-index
  "Finds index of first item in coll that returns truthy for filter-fn"
  [filter-fn coll]
  (first (keep-indexed (fn [i x] (when (filter-fn x) i)) coll)))

(def sentinel (js-obj))

(defn select-in
  "Returns a map containing only those entries in map whose keypath is in keypaths
   (select-in {:a {:b 1 :c 2}} [[:a :b]]) => {:a {:b 1}} "
  [map keypathseq]
    (loop [ret (empty map) keypaths (seq keypathseq)]
      (if keypaths
        (let [entry (get-in map (first keypaths) sentinel)]
          (recur
           (if (identical? entry sentinel)
             ret
             (assoc-in ret (first keypaths) entry))
           (next keypaths)))
        (with-meta ret (meta map)))))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn submap?
  "True if every key and value in m1 is contained in m2"
  [m1 m2]
  (let [k (keys m1)]
    (= (select-keys m2 k)
       m1)))

(defn dedupe-by
  "Like clojure.core/dedupe, but removes values with duplicate values for
  (keyfn value)."
  ([keyfn]
   (fn [rf]
     (let [pk (volatile! ::none)]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result input]
          (let [prior-key @pk
                input-key (keyfn input)]
            (vreset! pk input-key)
            (if (= prior-key input-key)
              result
              (rf result input))))))))
  ([keyfn coll] (sequence (dedupe-by keyfn) coll)))

(defn average-of-fn
  "Takes a seq `s` and a function `f`.
  Returns the average result of applying the function `f` to each item `x` of
  the seq `s`."
  [f s]
  (let [sum (fn [sum x]
              (+ (f x) sum))]
    (/ (reduce sum 0 s) (count s))))
