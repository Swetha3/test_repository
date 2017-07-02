(ns frontend.utils.ajax
  (:require [ajax.core :as clj-ajax]
            [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [cljs-time.core :as time]
            [clojure.string :as str]
            [cognitect.transit :as transit]
            [frontend.async :refer [put!]]
            [frontend.utils :as utils :include-macros true])
  (:import [goog Uri]))

(defn- js->clj*
  "This is a copy of the native js->clj, but it removes the clojure protocol
   checks. With advanced compilation turned on, the ISeq protocol will be
   considered satisfied for any js object with the key 'v'. David Nolen
   suggested this workaround.

   https://dev.clojure.org/jira/browse/CLJS-2050"
  ([x] (js->clj* x :keywordize-keys false))
  ([x & opts]
    (let [{:keys [keywordize-keys]} opts
          keyfn (if keywordize-keys keyword str)
          f (fn thisfn [x]
              (cond
                (array? x)
                (vec (map thisfn x))

                (identical? (type x) js/Object)
                (into {} (for [k (js-keys x)]
                           [(keyfn k) (thisfn (aget x k))]))

                :else x)
              )]
      (f x))))

(defn- response-headers [xhrio]
  (->> xhrio
       .getResponseHeaders
       js->clj*
       (into {} (map (fn [[k v]] [(str/lower-case k) v])))))

;; https://github.com/JulianBirch/cljs-ajax/blob/master/src/ajax/core.cljs
;; copy of the default json formatter, but returns a map with json body
;; in :resp and extra request metadata: :response-headers, :url, :method, and :request-time
(defn json-response-format
  "Returns a JSON response format.  Options include
   :keywords? Returns the keys as keywords
   :prefix A prefix that needs to be stripped off.  This is to
   combat JSON hijacking.  If you're using JSON with GET request,
   you should use this.
   http://stackoverflow.com/questions/2669690/why-does-google-prepend-while1-to-their-json-responses
   http://haacked.com/archive/2009/06/24/json-hijacking.aspx"
  ([{:keys [prefix keywords? url method start-time]
     :or {start-time (time/now)}}]
     {:read (fn read-json [xhrio]
              (let [json (js/JSON.parse (.getResponseText xhrio))
                    headers (response-headers xhrio)
                    request-time (try
                                   (time/in-millis (time/interval start-time (time/now)))
                                   (catch :default e
                                     (utils/merror e)
                                     0))]
                {:resp (js->clj* json :keywordize-keys keywords?)
                 :response-headers headers
                 :url url
                 :method method
                 :request-time request-time}))
      :description (str "JSON"
                        (if prefix (str " prefix '" prefix "'"))
                        (if keywords? " keywordize"))}))

(defn xml-request-format []
  {:content-type "application/xml"
   :write identity})

(defn xml-response-format []
  {:read (fn read-xml [xhrio]
           {:resp (.getResponseXml xhrio)})
   :description "XML"})

(defn raw-response-format []
  {:read (fn read-text [xhrio]
           {:resp (.getResponseText xhrio)})})

(defn transit-response-format
  [{:keys [url method start-time]
    :or {start-time (time/now)}}]
  {:read (fn read-transit [xhrio]
           (let [reader (transit/reader :json)
                 headers (response-headers xhrio)
                 request-time (try
                                (time/in-millis (time/interval start-time (time/now)))
                                (catch :default e
                                  (utils/merror e)
                                  0))]
             {:resp (transit/read reader (.getResponseText xhrio))
              :response-headers headers
              :url url
              :method method
              :request-time request-time}))})

(defn scopes-from-response [api-resp]
  (if-let [scope-str (get-in api-resp [:response-headers "x-circleci-scopes"])]
    (->> (str/split scope-str #"[,\s]+")
         (map #(str/replace % #"^:" ""))
         (map keyword)
         (set))))

(defn normalize-error-response [default-response props]
  (-> default-response
      (merge props)
      (assoc :status-code (:status default-response))
      (assoc :resp (get-in default-response [:response :resp]))
      (assoc :status :failed)))

(defn ajax-opts [{:keys [keywords? context headers format csrf-token uri method]
                  :or {keywords? true format :json csrf-token true}
                  :as opts}]
  (let [csrf-header (when (and csrf-token (re-find #"^/" uri))
                      {:X-CSRFToken (utils/csrf-token)})
        format-opts (case format
                      :json {:format (clj-ajax/json-request-format)
                             :response-format (json-response-format {:keywords? keywords? :url uri :method method})
                             :keywords? keywords?
                             :headers (merge {:Accept "application/json"}
                                             csrf-header
                                             headers)}
                      :transit {:format (clj-ajax/transit-request-format)
                                :response-format (transit-response-format {:url uri :method method})
                                :headers (merge {:Accept "application/transit+json"}
                                               csrf-header
                                               headers)}
                      :xml {:format (xml-request-format)
                            :response-format (xml-response-format)
                            :headers (merge {:Accept "application/xml"}
                                            csrf-header
                                            headers)}
                      :raw {:format :raw
                            :response-format (raw-response-format)
                            :headers headers})]
    (-> opts
        (merge format-opts)
        clj-ajax/transform-opts)))

;; TODO prefixes not implemented
(defn ajax [method url message channel & {:keys [context]
                                          :as opts}]
  (let [uuid frontend.async/*uuid*
        base-opts {:method method
                   :uri url
                   :handler #(binding [frontend.async/*uuid* uuid]
                               (put! channel [message :success (assoc % :context context :scopes (scopes-from-response %))]))
                   :error-handler #(binding [frontend.async/*uuid* uuid]
                                     (put! channel [message :failed (normalize-error-response % {:url url :context context})]))
                   :finally #(binding [frontend.async/*uuid* uuid]
                               (put! channel [message :finished context]))}]
    (put! channel [message :started context])
    (-> base-opts
        (merge opts)
        ajax-opts
        clj-ajax/ajax-request)))

(defn managed-ajax [method url & {:as opts}]
  (let [channel (chan)
        base-opts {:method method
                   :uri url
                   :handler #(put! channel (assoc % :status :success :scopes (scopes-from-response %)))
                   :error-handler #(put! channel (normalize-error-response % {:url url}))
                   :finally #(close! channel)}]
    (-> base-opts
        (merge opts)
        ajax-opts
        clj-ajax/ajax-request)
    channel))

(defn- same-origin-as-document?
  "True iff the given URL has the same origin as the current document. Accepts
  a relative URL (which will be resolved relative to the document's URL, and
  should therefore have the same origin)."
  [url]
  (let [document-uri (Uri. (.-href js/document.location))
        other-uri (Uri. url)
        resolved-other-uri (.resolve document-uri other-uri)]
    (.hasSameDomainAs resolved-other-uri document-uri)))

;; TODO this should be possible to do with the normal ajax function, but punting for now
(defn managed-form-post [url & {:keys [params headers keywords?]
                                :or {keywords? true}}]
  (let [channel (chan)]
    (-> {:format (clj-ajax/url-request-format)
         :response-format (json-response-format {:keywords? keywords? :url url :method :post})
         :params params
         :headers (merge {:Accept "application/json"}
                         (when (same-origin-as-document? url)
                           {:X-CSRFToken (utils/csrf-token)})
                         headers)
         :handler #(put! channel (assoc % :status :success))
         :error-handler #(put! channel %)
         :finally #(close! channel)}
        clj-ajax/transform-opts
        (assoc :uri url :method :post)
        clj-ajax/ajax-request)
    channel))
