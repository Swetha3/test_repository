(ns frontend.components.common
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.config :as config]
            [frontend.datetime :as datetime]
            [frontend.models.feature :as feature]
            [frontend.models.user :as user]
            [frontend.utils :as utils :refer-macros [html]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.github :refer [auth-url]]
            [frontend.timer :as timer]
            [frontend.elevio :as elevio]
            [goog.dom]
            [goog.dom.BrowserFeature]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(defn icon-path [name]
  (utils/cdn-path (str "/img/inner/icons/" name ".svg")))

(defn contact-support-a-info [owner & {:keys [tags]
                                       :or {tags [:support-dialog-raised]}}]
  (if user/support-eligible?
    (if (and (or (elevio/broken?)
                 (not (config/elevio-enabled?)))
             (not (config/zd-widget-enabled?)))
      {:href (str "mailto:" (config/support-email))
       :target "_blank"
       :class "disabled"}
      {:href ""
       :on-click #(raise! owner tags)})
    {:href "https://discuss.circleci.com/t/community-support/1470"
     :target "_blank"}))

(defn contact-us-inner [owner]
  [:a (contact-support-a-info owner)
   "contact us"])

(defn flashes
  "Displays common error messages (poorly named since flashes has another use in the app)."
  [error-message owner]
  (reify
    om/IRender
    (render [_]
      ;; use error messages that have html without passing html around
      (let [display-message (condp = error-message
                              :logged-out [:span "You've been logged out, "
                                                 [:a {:href (gh-utils/auth-url)} "log back in"]
                                                 " to continue."]
                              error-message)]
        (html
         (when error-message
           [:div.flash-error-wrapper.row-fluid
            [:div.offset1.span10
             [:div.alert.alert-block.alert-danger
              [:a.close {:on-click #(raise! owner [:clear-error-message-clicked])} "×"]
              "Error: " display-message
              " If we can help, " (contact-us-inner owner) "."]]]))))))

;; Translated from Google Closure's htmlToDocumentFragment, which is no longer
;; available. Note that it's no longer available because inserting strings from
;; outside the app into the DOM as HTML is inherently unsafe, even if we only do
;; it with data from our own API. We need to stop doing this.
;;
;; goog.dom.htmlToDocumentFragment_ = function(doc, htmlString) {
;;   var tempDiv = doc.createElement('div');
;;   if (goog.dom.BrowserFeature.INNER_HTML_NEEDS_SCOPED_ELEMENT) {
;;     tempDiv.innerHTML = '<br>' + htmlString;
;;     tempDiv.removeChild(tempDiv.firstChild);
;;   } else {
;;     tempDiv.innerHTML = htmlString;
;;   }
;;   if (tempDiv.childNodes.length == 1) {
;;     return /** @type {!Node} */ (tempDiv.removeChild(tempDiv.firstChild));
;;   } else {
;;     var fragment = doc.createDocumentFragment();
;;     while (tempDiv.firstChild) {
;;       fragment.appendChild(tempDiv.firstChild);
;;     }
;;     return fragment;
;;   }
;; };
(defn- html-to-document-fragment [doc html-string]
  (let [temp-div (.createElement doc "div")]
    (if goog.dom.BrowserFeature/INNER_HTML_NEEDS_SCOPED_ELEMENT
      (do
        (set! (.-innerHTML temp-div) (str "<br>" html-string))
        (.removeChild temp-div (.-firstChild temp-div)))
      (set! (.-innerHTML temp-div) html-string))
    (if (= 1 (.. temp-div -childNodes -length))
      (.removeChild temp-div (.-firstChild temp-div))
      (let [fragment (.createDocumentFragment doc)]
        (while (.-firstChild temp-div)
          (.appendChild fragment (.-firstChild temp-div)))
        fragment))))

(defn normalize-html
  "Creates a valid html string given a (possibly) invalid html string."
  [html-string]
  (goog.dom/getOuterHtml (html-to-document-fragment js/document html-string)))

(def messages-default-opts
  {:show-warning-text? true})

(defn message-severity-display-options [message]
  (case (-> message :type keyword)
        :error    ["alert-danger"  "Info-Error"]
        :warning  ["alert-warning" "Info-Warning"]
        :info     ["alert-info"    "Info-Info"]
        ["alert-info" "Info-Info"]))

(defn message [{:keys [type content]}]
  (let [[alert-class alert-icon] 
        (message-severity-display-options {:type type})]
    (html
      [:div {:class ["alert" alert-class "iconified"]}
       [:div.alert-icon
        [:img {:src (icon-path alert-icon)}]]
       content])))

(defn messages
  ([msgs]
   (messages msgs {}))
  ([msgs opts]
   (let [{:keys [show-warning-text?]} (merge messages-default-opts opts)
         content (fn [message] 
                   (html
                     [:div 
                      {:dangerouslySetInnerHTML 
                       #js 
                       {"__html" (normalize-html (:message message))}}]))]
     (when (pos? (count msgs))
       [:div.col-xs-12
        (map (fn [msg]
               [:div.row
                (message {:type (:type msg) 
                          :content (content msg)})])
             msgs)]))))

;; TODO: Why do we have ico and icon?
(def ico-paths
  {:turn "M50,0C26.703,0,7.127,15.936,1.576,37.5c-0.049,0.191-0.084,0.389-0.084,0.595c0,1.315,1.066,2.381,2.381,2.381h20.16c0.96,0,1.783-0.572,2.159-1.391c0,0,0.03-0.058,0.041-0.083C30.391,30.033,39.465,23.809,50,23.809c14.464,0,26.19,11.726,26.19,26.19c0,14.465-11.726,26.19-26.19,26.19c-10.535,0-19.609-6.225-23.767-15.192c-0.011-0.026-0.041-0.082-0.041-0.082c-0.376-0.82-1.199-1.392-2.16-1.392H3.874c-1.315,0-2.381,1.066-2.381,2.38c0,0.206,0.035,0.406,0.084,0.597C7.127,84.063,26.703,100,50,100c27.614,0,50-22.387,50-50C100,22.385,77.614,0,50,0z"
   :slim_turn "M7.5,65c6.2,17.5,22.8,30,42.4,30c24.9,0,45-20.1,45-45c0-24.9-20.1-45-45-45C30.3,5,13.7,17.5,7.5,35"
   :circle "M38.096000000000004,50a11.904,11.904 0 1,0 23.808,0a11.904,11.904 0 1,0 -23.808,0"
   :check "M5.5,10.792 C4.988,10.792 4.476,10.597 4.086,10.207 L0.439,6.561 C-0.146,5.975 -0.146,5.025 0.439,4.439 C1.025,3.853 1.975,3.853 2.561,4.439 L5.5,7.379 L12.439,0.439 C13.025,-0.146 13.975,-0.146 14.561,0.439 C15.146,1.025 15.146,1.975 14.561,2.561 L6.914,10.207 C6.524,10.597 6.012,10.792 5.5,10.792 L5.5,10.792 L5.5,10.792 Z"
   :check_icon "M36.666,85.973 C33.255,85.973 29.837,84.675 27.24,82.074 L2.93,57.764 C-0.977,53.861 -0.977,47.526 2.93,43.623 C6.837,39.716 13.164,39.716 17.071,43.623 L36.666,63.219 L82.929,16.956 C86.836,13.05 93.163,13.05 97.07,16.956 C100.977,20.86 100.977,27.194 97.07,31.097 L46.093,82.074 C43.495,84.675 40.077,85.973 36.666,85.973 L36.666,85.973 Z"
   :times "M61.785,55.051L56.734,50l5.051-5.05c0.93-0.93,0.93-2.438,0-3.368l-3.367-3.367c-0.93-0.929-2.438-0.929-3.367,0L50,43.265l-5.051-5.051c-0.93-0.929-2.437-0.929-3.367,0l-3.367,3.367c-0.93,0.93-0.93,2.438,0,3.368l5.05,5.05l-5.05,5.051c-0.93,0.929-0.93,2.438,0,3.366l3.367,3.367c0.93,0.93,2.438,0.93,3.367,0L50,56.734l5.05,5.05c0.93,0.93,2.438,0.93,3.367,0l3.367-3.367C62.715,57.488,62.715,55.979,61.785,55.051z"
   :slim_circle "M49.5,50a0.5,0.5 0 1,0 1,0a0.5,0.5 0 1,0 -1,0"
   :slim_check "M35,80 L5,50 M95,20L35,80"
   :slim_times "M82.5,82.5l-65-65 M82.5,17.5l-65,65"
   :slim_clock "M7.5,35C13.7,17.5,30.3,5,49.9,5c24.9,0,45,20.1,45,45c0,24.9-20.1,45-45,45C30.3,95,13.7,82.5,7.5,65 M50,20v30 M50,50h20"
   :slim_ban "M95,50 c0,24.9-20.1,45-45,45S5,74.9,5,50S25.1,5,50,5S95,25.1,95,50z M18.2,81.8l63.6-63.6"
   :slim_settings "M94.8,54.3c-0.3,2.1-1.9,3.8-3.9,4c-2.5,0.3-7.7,0.9-7.7,0.9c-2.3,0.5-3.9,2.5-3.9,4.9c0,1,0.3,2,0.8,2.7c0,0.1,3.1,4.1,4.7,6.2 c1.3,1.6,1.2,3.9-0.1,5.5c-1.8,2.3-3.8,4.3-6.1,6.1c-0.8,0.7-1.8,1-2.8,1c-0.9,0-2-0.3-2.7-0.9L67,80.1c-0.7-0.6-1.8-0.8-2.8-0.8 c-2.4,0-4.4,1.8-4.9,4.1l-0.9,7.5c-0.3,2.1-2,3.7-4,3.9C52.9,94.9,51.4,95,50,95c-1.4,0-2.9-0.1-4.3-0.2c-2.1-0.3-3.7-1.9-4-3.9 c0,0-0.9-7.4-0.9-7.5c-0.4-2.3-2.4-4.1-4.9-4.1c-1.1,0-2.2,0.4-3,0.9L27,84.8c-0.7,0.7-1.8,0.9-2.7,0.9c-1,0-2-0.4-2.8-1 c-2.3-1.8-4.3-3.8-6.1-6.1c-1.3-1.6-1.4-3.9-0.1-5.5l4.5-5.9c0.7-0.8,1-1.9,1-3c0-2.5-1.9-4.6-4.3-4.9l-7.3-0.9 c-2.1-0.3-3.7-2-3.9-4c-0.3-2.8-0.3-5.7,0-8.6c0.2-2.1,1.9-3.7,3.9-4l7.3-0.9c2.4-0.4,4.3-2.4,4.3-5c0-1-0.4-2.1-1-2.9 c0,0-3-3.9-4.5-5.9c-1.3-1.6-1.3-3.9,0.1-5.5c1.8-2.3,3.8-4.3,6.1-6.1c1.6-1.3,3.9-1.4,5.5-0.1l5.9,4.6c0.8,0.6,1.9,0.9,3,0.9 c2.4,0,4.5-1.8,4.9-4.1l0.9-7.5c0.3-2.1,2-3.7,4-3.9c2.8-0.3,5.7-0.3,8.6,0c2.1,0.3,3.7,1.9,4,3.9l0.9,7.5c0.5,2.3,2.4,4.1,4.9,4.1 c1,0,2-0.4,2.8-0.8c0,0,4-3.1,6.1-4.7c1.6-1.3,3.9-1.2,5.5,0.1c2.3,1.8,4.3,3.8,6.1,6.1c1.3,1.6,1.4,3.9,0.1,5.5 c0,0-4.7,6.1-4.7,6.2c-0.6,0.7-0.8,1.7-0.8,2.6c0,2.4,1.7,4.4,3.9,5c0,0,5.2,0.7,7.7,0.9c2.1,0.3,3.7,2,3.9,4 C95.1,48.5,95.1,51.4,94.8,54.3z"
   :clock "M59.524,47.619h-7.143V30.952c0-1.315-1.066-2.381-2.381-2.381c-1.315,0-2.381,1.065-2.381,2.381V50c0,1.315,1.066,2.38,2.381,2.38h9.524c1.314,0,2.381-1.065,2.381-2.38S60.839,47.619,59.524,47.619z"
   :chevron_down "M90.4,21.3l-45,45l-45-45"
   :slim_arrow_right "M53.6,17.5L86.1,50L53.6,82.5 M13.9,50h72.2"
   :infinity "M63.2,36.8c7.3-7.3,19.1-7.3,26.4,0 c7.3,7.3,7.3,19.1,0,26.4c-7.3,7.3-19.1,7.3-26.4,0 M63.2,36.8L50,50 M63.2,63.2l-6.6-6.6 M43.4,43.4l-6.6-6.6 M36.8,36.8 c-7.3-7.3-19.1-7.3-26.4,0s-7.3,19.1,0,26.4s19.1,7.3,26.4,0 M50,50L36.8,63.2"
   :github "M50.2,0.2c-27.6,0-50,22.4-50,50c0,22.1,14.3,40.8,34.2,47.4c2.5,0.5,3.4-1.1,3.4-2.4c0-1.2,0-5.1-0.1-9.3 c-13.9,3-16.8-5.9-16.8-5.9c-2.3-5.8-5.6-7.3-5.6-7.3c-4.5-3.1,0.3-3,0.3-3c5,0.4,7.7,5.2,7.7,5.2c4.5,7.6,11.7,5.4,14.6,4.2 c0.4-3.2,1.7-5.4,3.2-6.7C30,71.1,18.3,66.8,18.3,47.6c0-5.5,2-9.9,5.2-13.4C23,33,21.3,27.9,24,21c0,0,4.2-1.3,13.8,5.1 c4-1.1,8.3-1.7,12.5-1.7c4.2,0,8.5,0.6,12.5,1.7c9.5-6.5,13.7-5.1,13.7-5.1c2.7,6.9,1,12,0.5,13.2c3.2,3.5,5.1,8,5.1,13.4 c0,19.2-11.7,23.4-22.8,24.7c1.8,1.6,3.4,4.6,3.4,9.3c0,6.7-0.1,12.1-0.1,13.7c0,1.3,0.9,2.9,3.4,2.4c19.9-6.6,34.2-25.4,34.2-47.4 C100.2,22.6,77.9,0.2,50.2,0.2z"
   :repo "M44.4,27.5h-5.6v5.6h5.6V27.5z M44.4,16.2h-5.6v5.6h5.6V16.2z M78.1,5H21.9c0,0-5.6,0-5.6,5.6 v67.5c0,5.6,5.6,5.6,5.6,5.6h11.2V95l8.4-8.4L50,95V83.8h28.1c0,0,5.6-0.1,5.6-5.6V10.6C83.8,5,78.1,5,78.1,5z M78.1,72.5 c0,5.4-5.6,5.6-5.6,5.6H50v-5.6H33.1v5.6h-5.6c-5.6,0-5.6-5.6-5.6-5.6v-5.6h56.2V72.5z M78.1,61.2h-45V10.6h45.1L78.1,61.2z M44.4,50h-5.6v5.6h5.6V50z M44.4,38.8h-5.6v5.6h5.6V38.8z"})

(def ico-templates
  {:check-icon {:paths [:check_icon]}
   :logo {:paths [:turn :circle]}
   :pass {:paths [:turn :check]}
   :fail {:paths [:turn :times]}
   :queued {:paths [:turn :clock]}
   :logo-light {:paths [:slim_turn :slim_circle]}
   :busy-light {:paths [:slim_turn :slim_circle]}
   :pass-light {:paths [:slim_check]}
   :fail-light {:paths [:slim_times]}
   :hold-light {:paths [:slim_clock]}
   :stop-light {:paths [:slim_ban]}
   :settings-light {:paths [:slim_settings :slim_circle]}
   :none-light {:paths [:slim_circle]}
   :chevron-down {:paths [:chevron_down]}
   :slim-arrow-right {:paths [:slim_arrow_right]}
   :infinity {:paths [:infinity]}
   :github {:paths [:github]}
   :repo {:paths [:repo]}})

(defn ico [ico-name]
  (let [template (get ico-templates ico-name)]
    (html
     [:i {:class "ico"}
      [:svg {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 100 100"
             :class (name ico-name)}
       (for [path (:paths template)]
         (html
          [:path {:key path
                  :class (name path)
                  :d (get ico-paths path)}]))]])))

;; TODO: why do we have ico and icon?
(def icon-shapes
  {:turn {:path "M50,0C26.703,0,7.127,15.936,1.576,37.5c-0.049,0.191-0.084,0.389-0.084,0.595c0,1.315,1.066,2.381,2.381,2.381h20.16c0.96,0,1.783-0.572,2.159-1.391c0,0,0.03-0.058,0.041-0.083C30.391,30.033,39.465,23.809,50,23.809c14.464,0,26.19,11.726,26.19,26.19c0,14.465-11.726,26.19-26.19,26.19c-10.535,0-19.609-6.225-23.767-15.192c-0.011-0.026-0.041-0.082-0.041-0.082c-0.376-0.82-1.199-1.392-2.16-1.392H3.874c-1.315,0-2.381,1.066-2.381,2.38c0,0.206,0.035,0.406,0.084,0.597C7.127,84.063,26.703,100,50,100c27.614,0,50-22.387,50-50C100,22.385,77.614,0,50,0z"}
   :circle {:path "" :cx "50" :cy "50" :r "11.904"}
   :pass {:path "M65.151,44.949L51.684,58.417l-3.367,3.367c-0.93,0.93-2.438,0.93-3.367,0l-3.368-3.367l-6.734-6.733 c-0.93-0.931-0.93-2.438,0-3.368l3.368-3.367c0.929-0.93,2.437-0.93,3.367,0L46.633,50l11.785-11.785 c0.931-0.929,2.438-0.929,3.367,0l3.366,3.367C66.082,42.511,66.082,44.019,65.151,44.949z"}
   :fail {:path "M61.785,55.051L56.734,50l5.051-5.05c0.93-0.93,0.93-2.438,0-3.368l-3.367-3.367c-0.93-0.929-2.438-0.929-3.367,0L50,43.265l-5.051-5.051c-0.93-0.929-2.437-0.929-3.367,0l-3.367,3.367c-0.93,0.93-0.93,2.438,0,3.368l5.05,5.05l-5.05,5.051c-0.93,0.929-0.93,2.438,0,3.366l3.367,3.367c0.93,0.93,2.438,0.93,3.367,0L50,56.734l5.05,5.05c0.93,0.93,2.438,0.93,3.367,0l3.367-3.367C62.715,57.488,62.715,55.979,61.785,55.051z"}
   :clock {:path "M59.524,47.619h-7.143V30.952c0-1.315-1.066-2.381-2.381-2.381c-1.315,0-2.381,1.065-2.381,2.381V50c0,1.315,1.066,2.38,2.381,2.38h9.524c1.314,0,2.381-1.065,2.381-2.38S60.839,47.619,59.524,47.619z"}})

(defn icon [{icon-type :type icon-name :name}]
  [:svg {:class (str "icon-" (name icon-name))
         :xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 100 100"
         :dangerouslySetInnerHTML
         #js {"__html" (apply str (concat
                                   (when (= :status icon-type)
                                     [(str "<path class='" (name icon-name) "' fill='none'"
                                           " d='" (get-in icon-shapes [:turn :path]) "'></path>")])
                                   [(str "<path class='" (name icon-name) "' fill='none'"
                                         " d='" (get-in icon-shapes [icon-name :path]) "'></path>")]))}}])

(defn updating-duration
  "Takes a :start time string and :stop time string. Updates the component every second
   if the stop-time is nil.
   By default, uses datetime/as-duration, but can also take a custom :formatter
   function in opts."
  [{:keys [start stop]} owner opts]
  (reify

    om/IDisplayName
    (display-name [_] "Updating Duration")

    om/IDidMount
    (did-mount [_]
      (timer/set-updating! owner (not stop)))

    om/IDidUpdate
    (did-update [_ _ _]
      (timer/set-updating! owner (not stop)))

    om/IRender
    (render [_]
      (let [formatter (get opts :formatter datetime/as-duration)
            it (if (:formatter-use-start? opts)
                 start
                 (let [end-ms (if stop
                                (.getTime (js/Date. stop))
                                (datetime/now))
                       duration-ms (- end-ms (.getTime (js/Date. start)))]
                   duration-ms))]
        (dom/span nil (formatter it))))))

(defn circle-logo [{:keys [width height]}]
  (html
   [:svg {:xmlns "http://www.w3.org/2000/svg" :x "0px" :y "0px" :viewBox "0 0 393 100" :enableBackground "new 0 0 393 100" :width width :height height}
    [:circle {:cx "48.5" :cy "50" :r "11.9"}]
    [:path {:d "M48.5,39.3c5.9,0,10.7,4.8,10.7,10.7s-4.8,10.7-10.7,10.7S37.8,55.9,37.8,50S42.6,39.3,48.5,39.3z M48.5,5 C27.6,5,9.9,19.3,5,38.8c-0.1,0.2-0.1,0.4-0.1,0.5c0,1.2,1,2.2,2.2,2.2h18.2c0.9,0,1.6-0.5,2-1.3v-0.1C31,32,39.1,26.4,48.6,26.4 c13,0,23.5,10.5,23.5,23.6S61.6,73.6,48.5,73.6c-9.4,0-17.6-5.6-21.4-13.7v-0.1c-0.4-0.7-1.1-1.3-2-1.3H7.1c-1.2,0-2.2,1-2.2,2.2 c0,0.2,0,0.4,0.1,0.5C9.9,80.7,27.6,95,48.5,95c24.8,0,45-20.2,45-45S73.4,5,48.5,5z M173.1,18.6c0,2.5-2,4.5-4.5,4.5 c-2.5,0-4.5-2-4.5-4.5s2-4.5,4.5-4.5C171.1,14.1,173.1,16.1,173.1,18.6z M171.9,71.3V27.6h-6.8v43.7c0,0.6,0.5,1.1,1.1,1.1h4.5 C171.5,72.4,171.9,72,171.9,71.3z M381.4,11.8c-3.7,0-6.8,3.1-6.8,6.8s3.1,6.8,6.8,6.8s6.8-3.1,6.8-6.8 C388.1,14.8,385,11.8,381.4,11.8z M386.9,27.6v43.7c0,0.6-0.5,1.1-1.1,1.1h-9c-0.6,0-1.1-0.5-1.1-1.1V27.6H386.9z M201.2,26.4 c-6.8,0.4-12.2,3.6-15.8,8.6v-6.3c0-0.6-0.5-1.1-1.1-1.1h-4.5c-0.6,0-1.1,0.5-1.1,1.1l0,0v42.7c0,0.6,0.5,1.1,1.1,1.1h4.5 c0.6,0,1.1-0.5,1.1-1.1V50c0-8.9,6.9-16.2,15.8-16.8c0.6,0,1.1-0.5,1.1-1.1v-4.5C202.3,27,201.8,26.5,201.2,26.4z M261.2,12.9h-4.5 c-0.6,0-1.1,0.5-1.1,1.1v57.2c0,0.6,0.5,1.1,1.1,1.1h4.5c0.6,0,1.1-0.5,1.1-1.1V14.1C262.3,13.5,261.8,12.9,261.2,12.9z M157.4,59 h-5.2c-0.4,0-0.7,0.2-0.9,0.5c-3.1,4.5-8.1,7.4-13.9,7.4c-9.3,0-16.8-7.6-16.8-16.8s7.6-16.8,16.8-16.8c5.9,0,10.9,3,13.9,7.4 c0.2,0.3,0.5,0.5,0.9,0.5h5.2c0.6,0,1.1-0.5,1.1-1.1c0-0.2-0.1-0.4-0.1-0.5c-3.9-7.6-11.9-13-21.1-13c-13,0-23.5,10.5-23.5,23.6 s10.5,23.6,23.6,23.6c9.2,0,17.2-5.3,21.1-13c0.1-0.2,0.1-0.4,0.1-0.5C158.5,59.5,158,59,157.4,59z M247.7,59h-5.2 c-0.4,0-0.7,0.2-0.9,0.5c-3.1,4.5-8.1,7.4-14,7.4c-9.3,0-16.8-7.6-16.8-16.8s7.6-16.8,16.8-16.8c5.9,0,10.9,3,14,7.4 c0.2,0.3,0.5,0.5,0.9,0.5h5.2c0.6,0,1.1-0.5,1.1-1.1c0-0.2,0-0.4-0.1-0.5c-3.9-7.6-11.9-13-21.1-13c-13,0-23.6,10.5-23.6,23.6 s10.5,23.6,23.6,23.6c9.2,0,17.2-5.3,21.1-13c0.1-0.2,0.1-0.4,0.1-0.5C248.8,59.5,248.3,59,247.7,59z M368.5,55.8 c-0.2-0.1-0.4-0.2-0.5-0.2l0,0h-9.7l0,0c-0.4,0-0.7,0.2-1,0.5c-2.2,3.7-6.1,6.2-10.7,6.2c-6.8,0-12.3-5.5-12.3-12.3 s5.5-12.3,12.3-12.3c4.6,0,8.6,2.5,10.7,6.2c0.2,0.4,0.5,0.5,1,0.5l0,0h9.7l0,0c0.2,0,0.4-0.1,0.5-0.2c0.5-0.3,0.6-0.8,0.5-1.3 c-3-9.5-12-16.5-22.5-16.5C333.5,26.4,323,37,323,50s10.5,23.6,23.6,23.6c10.5,0,19.4-6.9,22.5-16.5C369.1,56.6,368.9,56,368.5,55.8 z M292.6,26.4C279.6,26.4,269,37,269,50s10.5,23.6,23.6,23.6c9.2,0,17.2-5.3,21.1-13c0.1-0.2,0.1-0.4,0.1-0.5c0-0.6-0.5-1.1-1.1-1.1 h-5.2c-0.4,0-0.7,0.2-0.9,0.5c-3.1,4.5-8.1,7.4-13.9,7.4c-8.6,0-15.6-6.4-16.6-14.6H315c0.6,0,1.1-0.5,1.1-1.1c0-0.4,0-0.8,0-1.2 C316.2,37,305.6,26.4,292.6,26.4z M276.4,45.5c2-7.1,8.5-12.3,16.2-12.3c7.7,0,14.2,5.2,16.2,12.3H276.4z"}]]))

;; Calls to this may be wrapped in (str ...) to appease the CLJS type inferencer.
(defn sign-up-text []
  (if (config/enterprise?)
    "Get Started"
    "Sign Up Free"))

(defn sign-up-cta [{:keys [source]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      ((om/get-shared owner :track-event) {:event-type :signup-impression}))
    om/IRender
    (render [_]
      (let [track-signed-up-clicked #((om/get-shared owner :track-event) {:event-type :signup-clicked})
            link-btn-attrs (fn [login-url]
                             {:href (str login-url
                                         "?return-to="
                                         js/window.location.origin
                                         js/window.location.pathname
                                         "?signup-404=true"
                                         js/window.location.hash)
                              :role "button"
                              :on-mouse-up track-signed-up-clicked})]
        (html
          [:div.container-fluid.signup
           [:div.col-sm-5.col-sm-offset-1
            [:a.btn.btn-cta (link-btn-attrs "/login")
              [:i.fa.fa-github]
              "Authorize GitHub"]]
           [:div.col-sm-5
            [:a.btn.btn-cta (link-btn-attrs "/bitbucket-login")
             [:i.fa.fa-bitbucket]
             "Authorize Bitbucket"]]])))))

(defn feature-icon [name]
  [:img.header-icon {:src (utils/cdn-path (str "/img/outer/feature-icons/feature-" name ".svg"))}])

(defn language [name]
  [:img.background.language {:class name
                             :src (utils/cdn-path (str "/img/outer/languages/language-" name ".svg"))}])

(def language-background
  (map language ["rails-1"
                 "clojure-1"
                 "java-1"
                 "python-1"]))

(def language-background-jumbotron
  (concat language-background
          (map language ["ruby-1"
                         "javascript-1"
                         "node-1"
                         "php-1"])))
