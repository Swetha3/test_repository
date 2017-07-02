(ns frontend.config
  (:require [frontend.models.feature :as feature]))

(defn env
  "The name of the server configuration environment.
  For humans only: Do not gate features with this setting."
  []
  (aget js/window "renderContext" "env"))

(goog-define DEV true)
;; Type-hinting this as a boolean means that code inside (when client-dev? ...)
;; is elided in production builds.
(def ^boolean client-dev?
  "Was the client compiled in dev-mode?"
  DEV)

(defn enterprise?
  "True if this is an enterprise (as opposed to main public web) deployment."
  []
  (or (boolean (aget js/window "renderContext" "enterprise"))
      (feature/enabled? :enterprise-ui)))

(defn pusher
  "Options to be passed to the Pusher client library."
  []
  ;;TODO Delete this if-let when https://github.com/circleci/circle/pull/3972 is fully deployed.
  (if-let [app-key (aget js/window "renderContext" "pusherAppKey")]
    {:key app-key}
    (js->clj (aget js/window "renderContext" "pusher") :keywordize-keys true)))

(defn logging-enabled?
  []
  "If true, log statements print to the browswer's JavaScript console."
  (boolean (aget js/window "renderContext" "logging_enabled")))

(defn assets-root
  "Path to root of CDN assets."
  []
  (aget js/window "renderContext" "assetsRoot"))

(defn github-endpoint
  "Full HTTP URL of GitHub API."
  []
  (aget js/window "renderContext" "githubHttpEndpoint"))

(defn github-client-id
  "The GitHub application client id for the app we're executing as."
  []
  (aget js/window "renderContext" "githubClientId"))

(defn stripe-key
  "Publishable key to identify our account with Stripe.
  See: https://stripe.com/docs/tutorials/dashboard#api-keys"
  []
  (aget js/window "renderContext" "stripePublishableKey"))

(defn analytics-enabled?
  []
  "If true, collect user analytics"
  ;; The value should be supplied by backend
  ;; till that happens, use enterprise? check instead
  (let [v (aget js/window "renderContext" "analytics_enabled")]
    (if-not (nil? v)
      (boolean v)
      ;; TODO: Kill this after backend populate the context value
      (not (enterprise?)))))

(defn intercom-enabled?
  []
  (let [v (aget js/window "renderContext" "intercom_enabled")]
    (if-not (nil? v)
      (boolean v)
      ;; TODO: Kill this after backend populate the context value
      (analytics-enabled?))))

(defn elevio-enabled?
  []
  (boolean (aget js/window "elevSettings")))

(defn zd-widget-enabled?
  []
  (boolean (aget js/window "zdUser")))

(defn statuspage-header-enabled?
  "If true, we should show statuspage alerts with incidents activated"
  []
  (not (enterprise?)))

(defn footer-enabled?
  "Whether we should show the site-wide footer."
  []
  (not (enterprise?)))

(defn show-marketing-pages?
  "Whether we should show links to marketing pages in the site header."
  []
  (not (enterprise?)))

(defn support-email
  []
  (or (aget js/window "renderContext" "support_email")
      "sayhi@circleci.com"))

(defn jira-connect-enabled? []
  (not (enterprise?)))
