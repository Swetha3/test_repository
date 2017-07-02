(ns frontend.utils.vcs-url
  (:require [clojure.string :as string]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh]
            [frontend.utils.bitbucket :as bb]
            [frontend.utils.vcs :as vcs]))

(def url-project-re #"^https?://([^/]+)/(.*)")

(defn project-name [vcs-url]
  (some->> vcs-url
           (re-matches url-project-re)
           (last)))

(defn vcs-type [vcs-url]
  (or ({"github.com" "github"
        "bitbucket.org" "bitbucket"} (second (re-matches url-project-re vcs-url)))
      "github"))

(defmulti profile-url
  (fn [user]
    (if (= "none" (:login user))
      :none
      (keyword (:vcs_type user)))))

(defmethod profile-url :github [user]
  (str (gh/http-endpoint) "/" (:login user)))

(defmethod profile-url :bitbucket [user]
  (str (bb/http-endpoint) "/" (:login user)))

(defmethod profile-url :none [user] "")

;; slashes aren't allowed in github org/user names or project names
(defn org-name [vcs-url]
  (first (string/split (project-name vcs-url) #"/")))

(defn repo-name [vcs-url]
  (second (string/split (project-name vcs-url) #"/")))

(defn display-name [vcs-url]
  (.replace (project-name vcs-url) "/" \u200b))

(defn project-path [vcs-url]
  (str "/" (vcs/->short-vcs (vcs-type vcs-url)) "/" (project-name vcs-url)))

(defn vcs-url [vcs-type org repo]
  (let [protocol "https://"
        host (case (name vcs-type)
               "github" "github.com"
               "bitbucket" "bitbucket.org")]
    (string/join "/" [(str protocol host) org repo])))
