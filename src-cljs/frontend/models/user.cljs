(ns frontend.models.user
  (:require [clojure.set :as set]
            [frontend.models.feature :as feature]
            goog.string.format))

(defn current-scopes [user]
  (let [scopes (or (:user/github-oauth-scopes user)
                   (:github_oauth_scopes user))]
    (set scopes)))

(defn missing-public-scopes [user]
  (let [current-scopes (current-scopes user)]
    (set/union
      (when (empty? (set/intersection current-scopes #{"user" "user:email"}))
        #{"user:email"})
      (when (empty? (set/intersection current-scopes #{"repo" "public_repo"}))
        #{"public_repo"})
      (when (empty? (set/intersection current-scopes #{"repo" "user" "read:org"}))
        #{"read:org"}))))

(defn missing-private-scopes [user]
  (let [current-scopes (current-scopes user)]
    (set/union (when (empty? (set/intersection current-scopes #{"user" "user:email"}))
                 #{"user:email"})
               (when-not (contains? current-scopes "repo")
                 #{"repo"}))))

(defn has-public-scopes? [user]
  (empty? (missing-public-scopes user)))

(defn has-private-scopes? [user]
  (empty? (missing-private-scopes user)))

(defn missing-scopes [user]
  (if (or (has-public-scopes? user)
          (has-private-scopes? user))
    ;; if just missing private scopes, that can be added with add-private-repos
    #{}
    (missing-public-scopes user)))

(defn public-key-scope? [user]
  (-> user current-scopes (get "admin:public_key") boolean))

(defn unkeyword
  "Converts a keyword in to a string without the leading colon. See server-side function of the same name."
  [kw]
  (.substr (str kw) 1))

(defn project-preferences [user]
  (into {} (for [[vcs-url prefs] (:projects user)]
             [(unkeyword vcs-url) prefs])))

(def free? (boolean (some-> js/window
                            (aget "ldUser")
                            (aget "custom")
                            (aget "free"))))

(def support-eligible? (boolean (some-> js/window
                                        (aget "elevSettings")
                                        (aget "support_enabled"))))
(defn has-org? [{:keys [organizations]} org-name vcs-type]
  (some #(and (= org-name (:login %))
              (= vcs-type (:vcs_type %)))
        organizations))

(defn github-authorized? [user]
  (-> user current-scopes empty? not))

(defn bitbucket-authorized? [user]
  (let [check-fn (some-fn :user/bitbucket-authorized? :bitbucket_authorized)]
    (-> user check-fn boolean)))

(defn has-code-identity? [user]
  (if (feature/enabled? "google-login-empty-state")
    (or (github-authorized? user)
        (bitbucket-authorized? user))
    true))

(defn deauthorize-github [user]
  (-> user
      (assoc :github_oauth_scopes nil)))

(defn deauthorize-bitbucket [user]
  (-> user
      (assoc :bitbucket_authorized nil)))

(defn primary-email [user]
  ;; We shove the primary email into :selected_email in the api layer, even
  ;; though we have a :selected-email key in the collection which is different.
  ;; Encapsulating this logic in case we ever want to make the :selected_email on
  ;; the FE == the :selected-email on the BE.
  (:selected_email user))

(defn num-projects-followed [user-data]
  (:num_projects_followed user-data))

(defn installation-admin?
  "Indicates whether or not a user is an admin of this installation of CircleCI"
  [user]
  (:admin user))

(defn- org-admin?
  "Indicates whether or not user is an admin for this installation"
  [org]
  (or (:admin org)
      (:admin? org)))

(defn org-admin-authorized?
  "Does the current user have the permissions to view this org"
  [user org]
  (or (org-admin? org) (installation-admin? user)))
