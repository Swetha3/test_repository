(ns frontend.components.pages.build.head.trigger
  "Responsible for deciding how to describe what triggered a build. See
  `description`."
  (:require [frontend.models.build :as build-model]
            [frontend.routes :as routes]
            [frontend.utils :refer-macros [html]]
            [frontend.utils.vcs-url :as vcs-url]))

(defn- link-to-retry-source [build]
  (html
   (when-let [retry-id (:retry_of build)]
     [:a {:href (routes/v1-build-path
                 (vcs-url/vcs-type (:vcs_url build))
                 (:username build)
                 (:reponame build)
                 nil
                 retry-id)}
      retry-id])))

(defn- link-to-user [build]
  (html
   (when-let [user (:user build)]
     (if (= "none" (:login user))
       [:em "Unknown"]
       [:a {:href (vcs-url/profile-url user)}
        (build-model/ui-user build)]))))

(defn- link-to-commit [build]
  (html
   [:a {:href (:compare build)}
    (take 7 (:vcs_revision build))]))

(defn description
  "Renders a description of what triggered the given build."
  [build]
  (let [user-link (link-to-user build)
        commit-link (link-to-commit build)
        retry-link (link-to-retry-source build)
        cache? (build-model/dependency-cache? build)]
    (case (:why build)
      ("github" "bitbucket") (list user-link " (pushed " commit-link ")")
      "edit" (list user-link " (updated project settings)")
      "first-build" (list user-link " (first build)")
      "retry" (list user-link " (retried " retry-link
                    (when-not cache? " without cache") ")")
      "ssh" (list user-link " (retried " retry-link " with SSH)")
      "auto-retry" (list "CircleCI (auto-retry of " retry-link ")")
      "trigger" (if (:user build)
                  (list user-link " on CircleCI.com")
                  (list "CircleCI.com"))
      "api" "API"
      (or
       (:job_name build)
       "unknown"))))
