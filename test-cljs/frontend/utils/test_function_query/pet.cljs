(ns frontend.utils.test-function-query.pet
  ;; Aliasing as fq- to prove that the alias can be different in different
  ;; namespaces.
  (:require [frontend.utils.function-query :as fq- :include-macros true]))

(defn cute?
  {::fq-/queries {:pet [:pet/cuteness]}}
  [pet]
  (< 100 (:pet/cuteness pet)))

(defn fuzzy?
  {::fq-/queries {:pet [:pet/fuzz]}}
  [pet]
  (< 100 (:pet/fuzz pet)))

(defn satisfactory?
  {::fq-/queries {:pet (fq-/merge (fq-/get cute? :pet)
                                  (fq-/get fuzzy? :pet))}}
  [pet]
  (and (cute? pet)
       (fuzzy? pet)))
