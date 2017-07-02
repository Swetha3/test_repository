(ns frontend.parser.connection
  (:require [bodhi.core :as bodhi])
  (:refer-clojure :exclude [merge]))

(defn- forgiving-subvec
  "Like subvec, but doesn't throw if you ask for elements beyond the end of the vector.

  (forgiving-subvec [:a :b] 1 5) ;=> [:b]
  (forgiving-subvec [:a :b] 4 5) ;=> []"
  [v offset limit]
  (subvec v
          (min (count v) offset)
          (min (count v) (+ offset limit))))

(defn read [next-read]
  (fn [{:keys [target read-path] {{:keys [connection/offset connection/limit] :as params} :params :keys [key]} :ast :as env}]
    (if (nil? target)
      (if (or offset limit)
        (do
          (assert
           (and offset limit)
           (str ":connection/offset and :connection/limit must be given together."))
          (let [new-env (-> env
                            (update-in [:ast :params] dissoc :connection/offset :connection/limit)
                            (update :state
                                    ;; env contains the state atom, not a
                                    ;; persistent state map. We want to annotate
                                    ;; the state map with the offset and limit to
                                    ;; allow the downstream read mechanism to read
                                    ;; that if it was queried for. To do that, we
                                    ;; have to deref the state atom, modify it,
                                    ;; and re-wrap it in an atom. This
                                    ;; demonstrates that Bodhi should pass the
                                    ;; persistent state map through the read
                                    ;; stack, not the atom.
                                    #(atom (update-in @% read-path assoc
                                                      :connection/offset offset
                                                      :connection/limit limit))))
                result (next-read new-env)]
            (-> (update-in result [:value :connection/edges] forgiving-subvec offset limit))))
        (next-read env))

      (if (#{:connection/offset :connection/limit} key)
        {target nil}
        (next-read env)))))

(defn- pad-or-trim
  "If coll is larger than size, trims it to size. If smaller, pads it with nil.
  Returns a vector."
  [coll size]
  (vec (take size (concat coll (repeat nil)))))

(defn merge [next-merge]
  (fn [{:keys [ast path state novelty] {{:keys [connection/offset connection/limit] :as params} :params :keys [key]} :ast :as env}]
    (let [{:keys [key params]} ast]
      (if (or offset limit)
        (do
          (assert
           (and offset limit)
           (str ":connection/offset and :connection/limit must be given together."))
          (let [total-count (:connection/total-count novelty)
                novel-edges (:connection/edges novelty)
                result-with-total-count {:keys #{}
                                         :next (-> state
                                                   (assoc-in (conj path :connection/total-count) total-count)
                                                   (update-in (conj path :connection/edges) pad-or-trim total-count))}]
            (reduce-kv (fn [result i novel-edge]
                         (bodhi/update-merge-result
                          result
                          (next-merge (-> env
                                          (update :path conj :connection/edges (+ offset i))
                                          (assoc :novelty novel-edge
                                                 :state (:next result)
                                                 :ast (first (filter #(= :connection/edges (:key %)) (:children ast))))))))
                       result-with-total-count novel-edges)))
        (next-merge env)))))
