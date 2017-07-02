(ns frontend.api.path
  (:require [goog.string :as gstring]))

(defn- base-project-url-path [vcs-type]
  (gstring/format
   "/api/v1.1/project/%s"
   vcs-type))

(defn- base-organization-url-path [vcs-type org-name]
  (gstring/format "/api/v1.1/organization/%s/%s"
                  (or vcs-type "github")
                  org-name))

(defn base-project-jira-path [vcs-type project-name]
  (gstring/format
    "%s/%s/jira"
    (base-project-url-path vcs-type)
    project-name))

(defn branch-path [vcs-type org-name repo-name branch]
  (gstring/format
    "%s/%s/%s/tree/%s"
    (base-project-url-path vcs-type) org-name repo-name (gstring/urlEncode branch)))

(defn project-settings [vcs-type org-name repo-name]
  (gstring/format
    "%s/%s/%s/settings"
    (base-project-url-path vcs-type) org-name repo-name))

(defn project-info
  [vcs-type org-name repo-name]
  (gstring/format
    "%s/%s/%s/info"
    (base-project-url-path vcs-type) org-name repo-name))

(defn project-checkout-keys [vcs-type repo-name]
  (gstring/format
   "%s/%s/checkout-key"
   (base-project-url-path vcs-type)
   repo-name))

(defn project-checkout-key  [vcs-type repo-name fingerprint]
  (gstring/format
   "%s/%s/checkout-key/%s"
   (base-project-url-path vcs-type) repo-name fingerprint))

(defn project-ssh-key  [vcs-type repo-name]
  (gstring/format
   "%s/%s/ssh-key"
   (base-project-url-path vcs-type) repo-name))

(defn project-plan [vcs-type org-name repo-name]
  (gstring/format
   "%s/%s/%s/plan"
   (base-project-url-path vcs-type) org-name repo-name))

(defn project-tokens  [vcs-type project-name]
  (gstring/format
   "%s/%s/token"
   (base-project-url-path vcs-type) project-name))

(defn project-token [vcs-type project-name token]
  (gstring/format
   "%s/%s/token/%s"
   (base-project-url-path vcs-type) project-name token))

(defn project-follow [vcs-type project]
  (gstring/format
   "%s/%s/follow"
   (base-project-url-path vcs-type) project))

(defn project-unfollow [vcs-type project]
  (gstring/format
   "%s/%s/unfollow"
   (base-project-url-path vcs-type) project))

(defn project-enable [vcs-type project]
  (gstring/format
   "%s/%s/enable"
   (base-project-url-path vcs-type) project))

(defn project-users [vcs-type project]
  (gstring/format "%s/%s/users"
                  (base-project-url-path vcs-type)
                  project))

(defn project-users-invite [vcs-type project]
  (gstring/format "%s/%s/users/invite"
                  (base-project-url-path vcs-type)
                  project))

(defn project-cache [vcs-type project-name cache-type]
  (gstring/format "%s/%s/%s-cache"
                  (base-project-url-path vcs-type)
                  project-name
                  cache-type))

(defn organization-invite [vcs-type org-name]
  (gstring/format "%s/invite"
                  (base-organization-url-path vcs-type org-name)))

(defn project-hook-test [vcs-type project-name service-name]
  (gstring/format
   "%s/%s/hooks/%s/test"
   (base-project-url-path vcs-type)
    project-name
    service-name))

(defn build-retry [vcs-type org-name repo-name build-num ssh?]
  (gstring/format
   "%s/%s/%s/%s/%s"
   (base-project-url-path vcs-type) org-name repo-name build-num
   (if ssh?
     "ssh"
     "retry")))

(defn build-cancel [vcs-type org-name repo-name build-num]
  (gstring/format
   "%s/%s/%s/%s/cancel"
   (base-project-url-path vcs-type) org-name repo-name build-num))

(defn heroku-deploy-user [vcs-type repo-name]
  (gstring/format
   "%s/%s/heroku-deploy-user"
   (base-project-url-path vcs-type) repo-name))

(defn action-output [vcs-type project-name build-num step index max-chars]
  (gstring/format "%s/%s/%s/output/%s/%s?truncate=%s"
                  (base-project-url-path vcs-type)
                  project-name
                  build-num
                  step
                  index
                  max-chars))

(defn action-output-file [vcs-type project-name build-num step index]
  (gstring/format "%s/%s/%s/output/%s/%s?file=true"
                  (base-project-url-path vcs-type)
                  project-name
                  build-num
                  step
                  index))

(defn artifacts [vcs-type project-name build-num]
  (gstring/format "%s/%s/%s/artifacts"
                  (base-project-url-path vcs-type)
                  project-name
                  build-num))

(defn org-members [vcs-type org-name]
  (gstring/format
    "%s/members"
    (base-organization-url-path vcs-type org-name)))

(defn jira-projects [vcs-type project-name]
  (gstring/format
    "%s/projects"
    (base-project-jira-path vcs-type project-name)))

(defn jira-issue-types [vcs-type project-name]
  (gstring/format
    "%s/issue-types"
    (base-project-jira-path vcs-type project-name)))

(defn jira-issue [vcs-type project-name]
  (gstring/format
    "%s/issue"
    (base-project-jira-path vcs-type project-name)))
