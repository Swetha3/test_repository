(ns frontend.utils.function-query
  "See corresponding ClojureScript namespace for documentation."
  (:refer-clojure :exclude [get])
  (:require [cljs.analyzer.api :as ana]
            [clojure.walk :as walk]))

(defn- fully-qualify-symbol
  "Fully qualifies symbol according to ns."
  [ns sym]
  (let [ns-map (ana/find-ns ns)
        env (assoc (ana/empty-env)
                   :ns ns-map)]
    (let [var-map (ana/resolve env sym)]
      (if-not var-map
        sym                  ; If the symbol is not a var, leave it alone.
        (:name var-map)))))  ; :name is the fully qualified name.

(defn- quotation? [form]
  (and (seq? form)
       (= 'quote (first form))))

(defn- fully-qualify-form
  "Fully qualifies all symbols in form according to ns."
  [ns form]
  (cond
    (quotation? form) form
    (symbol? form) (fully-qualify-symbol ns form)
    :else (walk/walk (partial fully-qualify-form ns) identity form)))


(defmacro get
  "Get the query named k from a var named var-sym.

  This recursively expands any nested get calls in the query. The expanded form
  will then be evaluated, in case it includes any data manipulation (most
  likely, merge calls)."
  [var-sym k]
  {:pre [(symbol? var-sym)
         (keyword? k)]}
  (let [var-map (ana/resolve &env var-sym)]
    (if-not var-map
      ;; If no var is found, return the symbol. ClojureScript will generate a
      ;; nice "Use of undeclared Var" warning for us.
      var-sym
      (do
        (assert (contains? var-map ::queries)
                (str var-sym " metadata has no " ::queries " key. "))
        (let [queries (::queries var-map)]
          (assert (contains? queries k)
                  (str var-sym " has no " k " query."))
          (fully-qualify-form (:ns var-map) (clojure.core/get queries k)))))))
