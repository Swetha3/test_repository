(ns frontend.proxy
  (:require [clojure.string :as string]
            [org.httpkit.server :refer [with-channel send!]]
            [org.httpkit.client :refer [request]]))

(defn query-string-with-om-build-id [req]
  (cond
    (:query-string req) (str "?om-build-id=dev&" (:query-string req))
    (= :get (:request-method req)) "?om-build-id=dev"
    :else nil))

(defn proxy-request [req {:keys [backend-lookup-fn] :as options}]
  (let [backend (backend-lookup-fn req)]
    (assert backend)
    {:url (str (:proto backend) "://"
               (:host backend)
               (or (:uri backend) (:uri req))
               (query-string-with-om-build-id req))
     :timeout 120000 ;ms
     :keepalive -1
     :method (:request-method req)
     :headers (assoc (:headers req)
                     "host" (:host backend)
                     "x-circleci-assets-proto" "https"
                     "x-circleci-assets-host" (get-in req [:headers "host"]))
     :body (:body req)
     :follow-redirects false}))

(defn rewrite-error [{:keys [error] :as response}]
  {:status 503
   :headers {"Content-Type" "text/plain"}
   :body (str "Cannot access backend\n" error)})

(defn strip-secure-cookie [header-val]
  (cond (string? header-val) (string/replace header-val #";(\s)*Secure" "")
        (coll? header-val) (map strip-secure-cookie header-val)))

(defn strip-secure [headers]
  (if (headers "set-cookie")
    (update-in headers ["set-cookie"] strip-secure-cookie)
    headers))

(defn rewrite-success
  "Patches up the proxied response with some ugly hacks. Documented within."
  [{:keys [status headers body] :as response}]
  (let [headers (-> (zipmap (map name (keys headers)) (vals headers))
                    ;; httpkit will decode the body, so hide the
                    ;; fact that the backend was gzipped.
                    (dissoc "content-encoding")
                    ;; avoid setting two Dates!  httpkit here will insert another Date
                    (dissoc "date")
                    ;; The production server insists on secure cookies, but
                    ;; the development proxy does not support SSL.
                    strip-secure
                    ;; Silence pagespeed warnings
                    (assoc "Vary" "Accept-Encoding"))]

    {:status status
     :headers headers
     :body body}))

;; When developing a new page, the URL may not be recognized by the backend yet.
;; That makes it impossible to develop against production. When that happens,
;; add the URL (path) to this set (eg, "/projects"). The proxy will fetch
;; /dashboard from the backend instead, which will load the frontend app.
;; Meanwhile, the browser will see your new URL, so the frontend will dispatch
;; on that.
;;
;; This is for development only. Do not commit code with values in this set.
;; Instead, add the route to the backend. (And while you're there, add it to the
;; nginx config if necessary.)
(def new-urls #{})

(defn- match? [regex-or-string target]
  (condp instance? regex-or-string
    java.util.regex.Pattern (re-find regex-or-string target)
    String (= regex-or-string target)))

(defn with-new-url-mapping [handler]
  (fn [{:keys [uri] :as req}]
    (handler (cond-> req
               (some #(match? % uri) new-urls)
               (assoc :uri "/dashboard")))))

(defn with-proxy [handler options]
  (fn [req]
    (or (when (and (contains? #{:get :head} (:request-method req))
                   (nil? (:body req)))
          ;; local frontend doesn't really handle POSTs and avoid consuming request body
          (let [local-response (handler req)]
            (when (not= 404 (:status local-response))
              local-response)))
        (with-channel req channel
          (request (proxy-request req options)
                   (fn [response]
                     (let [rewrite (if (:error response) rewrite-error rewrite-success)]
                       (send! channel (rewrite response)))))))))

(defn wrap-handler [handler options]
  (-> handler
      (with-proxy options)
      with-new-url-mapping))
