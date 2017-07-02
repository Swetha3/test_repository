(ns frontend.utils.vcs
  (:require [frontend.config :as config]))

(def bitbucket-possible? (complement config/enterprise?))

(def short-to-long-vcs
  {"gh" "github"
   "bb" "bitbucket"
   ;; If the key is already in long form, that's fine
   "github" "github"
   "bitbucket" "bitbucket"})

(def long-to-short-vcs
  ;; If key is already in short form, that's fine
  {"gh" "gh"
   "bb" "bb"
   "github" "gh"
   "bitbucket" "bb"})

(defn adjust-vcs
  [vcs-map val]
  (let [kw (cond
             (map? val) (-> val :vcs_type vcs-map)
             (string? val) (-> val vcs-map)
             (keyword? val) (-> val name vcs-map)
             :default nil)]
    (if (map? val)
      (assoc val :vcs_type kw)
      kw)))

(def ->lengthen-vcs
  (partial adjust-vcs short-to-long-vcs))

(def ->short-vcs
  (partial adjust-vcs long-to-short-vcs))
