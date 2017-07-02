(ns frontend.models.organization
  (:require [clojure.set :as set]
            [frontend.config :as config]
            [frontend.utils :as util]
            [clojure.string :as string])
  (:refer-clojure :exclude [name]))

(defn projects-by-follower
  "Returns a map of users logins to projects they follow."
  [projects]
  (reduce (fn [acc project]
            (let [logins (map :login (:followers project))]
              (reduce (fn [acc login]
                        (update-in acc [login] conj project))
                      acc logins)))
          {} projects))

(defn show-upsell?
  "Given an org, returns whether or not to show that org upsells"
  [org]
  (and (not (config/enterprise?)) (> 4 (:num_paid_containers org))))

(defn uglify-org-id
  "Takes a pretty org id (e.g. \"GitHub/circleci\" and returns an org
  id of the form [:vcs_type :username]"
  [org-id-pretty]
  (let [[vcs_type-pretty username-pretty] (string/split
                                           org-id-pretty
                                           #"/")]
    [(util/keywordize-pretty-vcs_type vcs_type-pretty)
     (keyword username-pretty)]))

(defn prettify-org-id
  "Takes an org id of the form [:vcs_type :username] and returns a
  pretty org id (e.g. \"GitHub/circleci\""
  [[vcs_type username]]
  (str (util/prettify-vcs_type vcs_type)
       "/"
       (clojure.core/name username)))

(def name
  "Takes an org, returns its name.
  Not named 'name' because it conflicts with clojure.core/name"
  (some-fn :login :name :org :org-name))

(def vcs-type
  "Takes an org, returns its vcs type"
  (some-fn :vcs_type :vcs-type))

(defn same?
  "Compares two orgs, returns whether or not they are the same by
  comparing their name and their vcs_type"
  [org-a org-b]
  (and (= (name org-a) (name org-b))
       (= (vcs-type org-a) (vcs-type org-b))))

(defn default
  "Takes a list of orgs and returns a default value"
  [orgs]
  (first orgs))

(def ^:private legacy-org-keys->modern-org-keys
  "These keys are wanted in the Team page and Project page contexts.
   Due to variation in legacy-key context, check usability in
   case-by-case basis."
  {:login :organization/name
   :vcs_type :organization/vcs-type
   :avatar_url :organization/avatar-url
   :admin :organization/current-user-is-admin?})

(defn legacy-org->modern-org
  "Converts an org with legacy keys to the modern equivalent, suitable for
  our Om Next components."
  [org]
  (set/rename-keys org legacy-org-keys->modern-org-keys))

(defn modern-org->legacy-org
  "Inverse of legacy-org->modern-org."
  [org]
  (set/rename-keys org (set/map-invert legacy-org-keys->modern-org-keys)))

(defn get-from-map
  "Returns two-key 'org' map from a larger map.

  Return nil, not an incomplete map, if a complete org cannot be made."
  [map]
  (when (and (name map)
             (vcs-type map))
    {:login (name map)
     :vcs_type (vcs-type map)}))

(defn for-route
  [org]
  {:org (name org)
   :vcs_type (vcs-type org)})

(defn in-orgs?
  "Given an org, and a list of orgs, is the org in the list of orgs"
  [org orgs]
  (some (partial same? org) orgs))
