(ns frontend.utils.docs
  (:require [clojure.string :as string]
            [frontend.stefon :as stefon]
            [frontend.utils.html :refer [hiccup->html-str]]
            [frontend.utils :as utils :include-macros true :refer-macros [defrender]]
            [goog.string :as gstring]
            goog.string.format
            [frontend.state :as state]
            [om.core :as om :include-macros true]))


(defn api-curl [endpoint]
  (let [curl-args (->> [(when-not (= (:method endpoint) "GET")
                         (str "-X " (:method endpoint)))

                        (when (:body endpoint)
                          (str "--header \"Content-Type: application/json\""))

                       (when-let [body (:body endpoint)]
                         (str "-d '"
                              (string/replace body "'" "\\'")
                              "'"))]

                       (filter identity)
                       (string/join " "))
        curl-args-padded (if (empty? curl-args) ""
                             (str curl-args " "))
        curl-params (let [params (:params endpoint)]
                      (if (and params (= (:method endpoint) "GET"))
                        (->> params
                             (map #(str (:name %) "=" (:example %)))
                             (string/join "&")
                             (str "&"))
                        ""))]
    (gstring/format "curl %shttps://circleci.com%s?circle-token=:token%s"
                    curl-args-padded (:url endpoint) curl-params)))

(defn api-endpoint-filter [endpoint]
  (hiccup->html-str
   [:div
    [:p (:description endpoint)]
    [:h4 "Method"]
    [:p (:method endpoint)]
    (when-let [params (:params endpoint)]
      [:div [:h4 "Optional parameters"]
       [:table.table
        [:thead
         [:tr
          [:th "Parameter"] [:th "Description"]]]
        [:tbody
         (for [param params]
           [:tr
            [:td (:name param)]
            [:td (:description param)]])]]])
    [:h4 "Example call"]
    [:pre [:code (api-curl endpoint)]]
    [:h4 "Example response"]
    [:pre [:code (:response endpoint)]]
    (when (:try_it endpoint)
      [:p [:a {:href (str "https://circleci.com" (:url endpoint)) :target "_blank"}
           "Try it in your browser"]])]))

(defn code-list-filter [versions]
  (hiccup->html-str
   [:ul (for [version versions]
          [:li [:code (if-let [name (:name version)] name version)]])]))

(defn replace-variables [html]
  (string/replace html #"\{\{\s*([^\s]*)\s*(?:\|\s*([\w-]*)\s*)?\}\}"
                  (fn [[match var-name-and-path filter-name]]
                    (let [[name & path] (string/split var-name-and-path #"\.")
                          var (case name
                                "versions" (aget js/window "CI" "Versions")
                                "api_data" (aget js/window "circle_api_data")
                                nil)
                          val (when var (apply aget var path))
                          filter (case filter-name
                                   "code-list" code-list-filter
                                   "api-endpoint" api-endpoint-filter
                                   identity)]
                      ;; Fall back to the matched string if it we don't have a value for it.
                      (if-not val
                        match
                        (filter (utils/js->clj-kw val)))))))

(defn replace-asset-paths [html]
  (string/replace html #"\(asset:/(/.*?)\)"
                  (fn [[_ asset]]
                    (str "(" (stefon/asset-path asset) ")"))))

(defn article-info [doc-kw doc]
  (let [doc-name (name doc-kw)
        children (map keyword (or (:children doc) []))
        title (:title doc)
        short-title (or (:short_title doc) title)]
    {:url (str "/docs/" doc-name)
     :slug doc-name
     :title title
     :sort_title short-title
     :children children
     :lastUpdated (:last_updated doc)
     :category (:category doc)}))

(defn update-child-counts [{:keys [children title ] short-title :sort_title :as info}]
  (let [child-count (count children)
        has-children (seq children)
        title-with-count (if has-children (gstring/format "%s (%d)" title child-count) title)
        short-title-with-count (if has-children (gstring/format "%s (%d)" short-title child-count) short-title)]
    (assoc info :title_with_child_count title-with-count :short_title_with_child_count short-title-with-count)))

(defn update-children [docs]
  (reduce (fn [acc [template-name article-info]]
            (if (seq (:children article-info))
              (update-in acc [template-name :children] (fn [children]
                                                         ((apply juxt children) docs)))
              acc))
          docs docs))

(defn format-doc-manifest [manifest]
  (update-children (reduce (fn [acc [k v]]
                             (assoc acc k (update-child-counts (article-info k v)))) {} manifest)))


(defn maybe-rewrite-token [token]
  (case token
    "common-problems#intro" ""
    "common-problems#file-ordering" "file-ordering"
    "common-problems#missing-log-dir" "missing-log-dir"
    "common-problems#missing-file" "missing-file"
    "common-problems#time-day" "time-day"
    "common-problems#time-seconds" "time-seconds"
    "common-problems#requires-admin" "requires-admin"
    "common-problems#oom" "oom"
    "common-problems#wrong-ruby-version" "unrecognized-ruby-version"
    "common-problems#dont-run" "dont-run"
    "common-problems#git-bundle-install" "git-bundle-install"
    "common-problems#git-pip-install" "git-pip-install"
    "common-problems#wrong-commands" "not-specified-ruby-commands"
    "common-problems#bundler-latest" "bundler-latest"
    "common-problems#capybara-timeout" "capybara-timeout"
    "common-problems#clojure-12" "clojure-12"
    "common-problems" "troubleshooting"

    "faq" ""
    "faq#permissions" "permissions"
    "faq#what-happens" "what-happens"
    "faq#look-at-code" "look-at_code"
    "faq#parallelism" "parallelism"
    "faq#versions" "environment"
    "faq#external-resources" "external-resources"
    "faq#cant-follow" "cant-follow"

    "wrong-commands" "not-specified-ruby-commands"
    "wrong-ruby-version" "unrecognized-ruby-version"
    "not-recognized-ruby-version" "unrecognized-ruby-version"
    "rspec-wrong-exit-code" "rspec-exit-codes"

    "configure-php" "language-php"
    "reference-api" "api"
    "reference-api#build" "api#build"

    "ios" "ios-builds-on-os-x"
    token))
