(ns frontend.core
  (:require [cheshire.core :as json]
            [compojure.core :refer (defroutes GET ANY)]
            [compojure.handler :refer (site)]
            [compojure.route]
            [frontend.less :as less]
            [frontend.util.docs :as doc-utils]
            [frontend.proxy :as proxy]
            [frontend.stefon]
            [ring.util.response :as response]
            [stefon.core :as stefon]
            [stefon.path :as path]
            [stefon.settings]
            [org.httpkit.server :as httpkit]))

(def stefon-options
  {:asset-roots frontend.stefon/asset-roots
   :mode :development})

(defroutes routes
  (GET "/js/om-dev.js" []
    (response/redirect (stefon/link-to-asset "js/om-dev.js.stefon" stefon-options)))
  (GET "/devcards" []
    (response/resource-response "devcards.html"))
  (compojure.route/resources "/" {:root "public"
                                  :mime-types {:svg "image/svg"}})
  (compojure.route/resources "/vendor/font-awesome" {:root "components/font-awesome"
                                                     :mime-types {:svg "image/svg"}})
  (compojure.route/resources "/vendor/material-design-icons" {:root "components/material-design-icons"
                                                              :mime-types {:svg "image/svg"}})
  (compojure.route/resources "/vendor/octicons" {:root "components/octicons"
                                                 :mime-types {:svg "image/svg"}})
  (GET "/docs/manifest-dev.json" []
       (-> (doc-utils/read-doc-manifest "resources/assets/docs")
           (json/encode {:pretty true})
           (response/response)
           (response/content-type "application/json")))
  (ANY "*" [] {:status 404 :body nil}))

(defn cross-origin-everything
  "This lets us use local assets without the browser complaining. Safe because
   we're only serving assets in dev-mode from here."
  [handler]
  (fn [req]
    (-> (handler req)
        (response/header "Access-Control-Allow-Origin" "*")
        (response/header "Vary" "Accept-Encoding"))))

(defn less-override
  "Serve LESS files instead of letting Stefon handle them. (Needed for sourcemap support)"
  [handler]
  (fn [req]
    (let [path (:uri req)
          file (or
                 (if (= "/app.css.map" path) path)
                 (last (re-matches #"^/((?:(?:assets/css)|components)/.*\.less)$" path)))]
      (if file
        (-> file
            (response/resource-response)
            (response/header "Content-Type" "text/plain; charset=utf-8"))
        (handler req)))))

(defn wrap-hosted-scripts
  "Redirects to the canonical url for a hosted script if we're using stefon in development mode."
  [handler stefon-options hosted-scripts]
  (if (not= (:mode stefon-options) :development)
    handler
    (stefon.settings/with-options stefon-options
      (let [paths (set (map :path hosted-scripts))]
        (fn [req]
          (if (or (-> req :uri path/asset-uri? not)
                  (->> req :uri path/uri->adrf (contains? paths) not))
            (handler req)
            (response/redirect (:url (first (filter #(= (:path %)
                                                        (path/uri->adrf (:uri req)))
                                                    hosted-scripts))))))))))

(defonce stopf (atom nil))

(defn stop-server []
  (when-let [f @stopf]
    (println "stopping server")
    (f :timeout 3000)
    (reset! stopf nil)))

(def port 3000)

(defn backend-lookup
  "helper for finding appropriate backend, assumes that the `*.circlehost` prefix
  matches the DNS for env on circleci.com"
  [req]
  (let [server-name (:server-name req)
        env (System/getenv "CIRCLE_FRONTEND_ENV")]
    (cond
      (or (= env "dev")
          (= server-name "dev.circlehost"))
      (if (= (:uri req) "/query-api")
        {:proto "http" :host (if (= env "docker")
                               "api-service:3000"
                               "localhost:3009") :uri "/query"}
        {:proto "http" :host "dev.circlehost:8080"})

      (or (= env "prod")
          (= server-name "prod.circlehost"))
      {:proto "https" :host "circleci.com"}

      (or (= env "enterprise")
          (= server-name "enterprise.circlehost"))
      {:proto "https" :host "enterprise-staging.sphereci.com"}

      (= env "docker")
      {:proto "http" :host (System/getenv "CIRCLE_BACKEND_HOST")}

      (re-matches #"(.*).circlehost" server-name)
      (when-let [env (some->> req :server-name (re-matches #"(.*).circlehost") second)]
        {:proto "https" :host (format "%s.circleci.com" env)})

      :else {:proto "http" :host "dev.circlehost:8080"})))

(def proxy-config
  {:backend-lookup-fn backend-lookup})

(defn start-server []
  (stop-server)
  (println "starting server on port" port)
  (reset! stopf
          (httpkit/run-server (-> (site #'routes)
                                  (stefon/asset-pipeline stefon-options)
                                  (wrap-hosted-scripts stefon-options frontend.stefon/hosted-scripts)
                                  (less-override)
                                  (proxy/wrap-handler proxy-config)
                                  (cross-origin-everything))
                              {:port port}))
  nil)

(defn -main
  "Starts the server that will serve the assets when visiting circle with ?use-local-assets=true"
  []
  (println "Starting less compiler.")
  (less/init)
  (start-server))
