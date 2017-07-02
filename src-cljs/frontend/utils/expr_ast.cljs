(ns frontend.utils.expr-ast
  (:refer-clojure :exclude [get get-in]))

(defn get
  ([expr-ast key alternative]
   (if-let [children (:children expr-ast)]
     (or (->> children
              (filter #(= key (:key %)))
              first)
         alternative)
     alternative))
  ([expr-ast key]
   (get expr-ast key nil)))

(defn get-in
  ([expr-ast [first-key & other-keys] alternative]
   (let [child-ast (get expr-ast first-key)]
     (cond (not child-ast) alternative
           (not (seq other-keys)) child-ast
           :else (recur child-ast other-keys alternative))))
  ([expr-ast keypath]
   (get-in expr-ast keypath nil)))

(defn has-children?
  "Takes an ast for an expression and a set of keys. Returns true if
  the set of keys of the expression's children is the same as the
  given set of keys"
  [expr-ast key-set]
  (->> expr-ast
       :children
       (map :key)
       set
       (= key-set)))
