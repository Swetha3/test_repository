(ns frontend.components.build-timings
  (:require [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.datetime :as datetime]
            [frontend.disposable :as disposable]
            [frontend.models.build :as build-model]
            [frontend.models.project :as project-model]
            [frontend.routes :as routes]
            [frontend.utils :refer-macros [html]]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.events :as gevents]
            [goog.string :as gstring]
            [om.core :as om :include-macros true]))

(def required-build-keys
  "Keys which are required from the build model to appropriately render a
  build timings component."
  [:parallel
   :steps
   :start_time
   :stop_time
   :vcs_url
   :username
   :reponame
   :build_num])

(def padding-right 20)

(def top-axis-height 20)
(def left-axis-width 40)

(def bar-height 20)
(def bar-gap 10)
(def container-bar-height (- bar-height bar-gap))
(def step-start-line-extension 1)

(def min-container-rows 4)

(defn timings-width []  (-> (.querySelector js/document ".build-timings")
                            (.-offsetWidth)
                            (- padding-right)
                            (- left-axis-width)))

(defn timings-height [number-of-containers]
  (let [number-of-containers (if (< number-of-containers min-container-rows)
                               min-container-rows
                               number-of-containers)]
  (* (inc number-of-containers) bar-height)))

;;; Helpers
(defn create-x-scale [start-time stop-time]
  (let [start-time (js/Date. start-time)
        stop-time  (js/Date. stop-time)]
    (-> (js/d3.time.scale)
        (.domain #js [start-time stop-time])
        (.range  #js [0 (timings-width)]))))

(defn create-root-svg [dom-root number-of-containers]
  (let [root (.select js/d3 dom-root)]
    (-> root
        (.attr "width" (+ (timings-width)
                          left-axis-width
                          padding-right))
        (.attr "height" (+ (timings-height number-of-containers)
                           top-axis-height)))
    (-> root
        (.select "g")
        (.remove))

    (-> root
        (.append "g")
        (.attr "transform" (gstring/format "translate(%d,%d)" left-axis-width top-axis-height)))))

(defn create-y-axis [number-of-containers]
  (let [range-start (+ bar-height (/ container-bar-height 2))
        range-end   (+ (timings-height number-of-containers) (/ container-bar-height 2))
        axis-scale  (-> (.linear js/d3.scale)
                          (.domain #js [0 number-of-containers])
                          (.range  #js [range-start range-end]))]
  (-> (js/d3.svg.axis)
        (.tickValues (clj->js (range 0 number-of-containers)))
        (.scale axis-scale)
        (.tickFormat #(js/Math.floor %))
        (.orient "left"))))

(defn create-x-axis [build-duration]
  (let [axis-scale (-> (.linear js/d3.scale)
                         (.domain #js [0 build-duration])
                         (.range  #js [0 (timings-width)]))]
  (-> (.axis js/d3.svg)
        (.tickValues (clj->js (range 0 (inc build-duration) (/ build-duration 4))))
        (.scale axis-scale)
        (.tickFormat #(datetime/as-duration %))
        (.orient "top"))))

(defn duration [start-time stop-time]
  (- (.getTime (js/Date. stop-time))
     (.getTime (js/Date. start-time))))

(defn container-position [step]
  (* bar-height (inc (aget step "index"))))

(defn scaled-time [x-scale step time-key]
  (x-scale (js/Date. (aget step time-key))))

(defn wrap-status [status]
  {:status  status
   :outcome status})


;;; Elements of the visualization
(defn highlight-selected-container! [step-data]
  (let [fade-value #(if (= (.-textContent %1) (str (aget %2 "index"))) 1 0.5)]
    (-> (.select js/d3 ".y-axis")
        (.selectAll ".tick")
        (.selectAll "text")
        (.transition)
        (.duration 200)
        (.attr "fill-opacity" #(this-as element (fade-value element step-data))))))

(defn highlight-selected-step! [step selected]
  (-> step
      (.selectAll "rect")
      (.transition)
      (.duration 200)
      (.attr "fill-opacity" #(this-as element (if (= element selected) 1 0.5)))))

(defn reset-selected! [step]
  ;reset all the steps
  (-> step
      (.selectAll "rect")
      (.transition)
      (.duration 500)
      (.attr "fill-opacity" 1))

  ;reset all container labels
  (-> (.select js/d3 ".y-axis")
      (.selectAll ".tick")
      (.selectAll "text")
      (.transition)
      (.duration 200)
      (.attr "fill-opacity" 1)))

(defn step-href
  [{:keys [vcs_url username reponame build_num] :as build} step]
  ; Due to how our frontend.history/setup-link-dispatcher! intercepts links,
  ; we cannot use fragments to navigate from an SVG. Using the fully qualified
  ; path gets around this issue.
  (routes/v1-build-path (vcs-url/vcs-type vcs_url)
                        username
                        reponame
                        nil
                        build_num
                        :build-timing
                        (aget step "index")
                        (aget step "step")))

(defn foreground-actions
  "filter out background processes whose timing isn't to be represented in timings"
  [step]
  (let [actions (aget step "actions")]
    ;; actions is a js array so use js filter to avoid converting back and forth
    (.filter actions #(not (aget % "background")))))

(defn draw-containers! [build x-scale step]
  (let [step-start-pos #(scaled-time x-scale % "start_time")
        step-length    #(- (scaled-time x-scale % "end_time")
                           (step-start-pos %))
        step-duration  #(datetime/as-duration (duration (aget % "start_time") (aget % "end_time")))]
    (-> step
        (.selectAll "rect")
          (.data foreground-actions)
        (.enter)
          (.append "a")
            (.attr "href" (partial step-href build))
          (.append "rect")
            (.attr "class"     #(str "container-step-"
                                     (-> (aget % "status")
                                         wrap-status
                                         build-model/build-status
                                         build-model/status-class
                                         name)))
            (.attr "width"     step-length)
            (.attr "height"    container-bar-height)
            (.attr "y"         container-position)
            (.attr "x"         step-start-pos)
            (.on   "mouseover" #(this-as selected
                                         (highlight-selected-step! step selected)
                                         (highlight-selected-container! %)))
            (.on   "mouseout"  #(reset-selected! step))
          (.append "title")
            (.text #(gstring/format "%s (%s:%s) - %s"
                                    (aget % "name")
                                    (aget % "index")
                                    (aget % "step")
                                    (step-duration %))))))

(defn draw-step-start-line! [x-scale step]
  (let [step-start-position #(scaled-time x-scale % "start_time")]
  (-> step
      (.selectAll "line")
        (.data foreground-actions)
      (.enter)
        (.append "line")
        (.attr "class" "container-step-start-line")
        (.attr "x1"    step-start-position)
        (.attr "x2"    step-start-position)
        (.attr "y1"    #(- (container-position %)
                           step-start-line-extension))
        (.attr "y2"    #(+ (container-position %)
                           container-bar-height
                           step-start-line-extension)))))

(defn draw-steps! [{:keys [steps] :as build} x-scale chart]
  (let [steps-group       (-> chart
                              (.append "g"))

        step              (-> steps-group
                              (.selectAll "g")
                                (.data (clj->js steps))
                              (.enter)
                                (.append "g"))]
    (draw-step-start-line! x-scale step)
    (draw-containers! build x-scale step)))

(defn draw-label! [chart number-of-containers]
  (let [[x-trans y-trans] [-30 (+ (/ (timings-height number-of-containers) 2) 40)]
        rotation          -90]
  (-> chart
      (.append "text")
        (.attr "class" "y-axis-label")
        (.attr "transform" (gstring/format "translate(%d,%d) rotate(%d)" x-trans y-trans rotation))
        (.text "CONTAINERS"))))

(defn draw-axis! [chart axis class-name]
  (-> chart
      (.append "g")
        (.attr "class" class-name)
        (.call axis)))

(defn draw-chart! [root {:keys [parallel start_time stop_time] :as build}]
  (let [x-scale (create-x-scale start_time stop_time)
        chart   (create-root-svg root parallel)
        x-axis  (create-x-axis (duration start_time stop_time))
        y-axis  (create-y-axis parallel)]
    (draw-axis!  chart x-axis "x-axis")
    (draw-axis!  chart y-axis "y-axis")
    (draw-label! chart parallel)
    (draw-steps! build x-scale chart)))

(defn build-timings-chart [build owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (when build
        (draw-chart! (om/get-node owner "build-timings-svg") build))
      (om/set-state! owner [:resize-key]
                     (disposable/register
                      (gevents/listen js/window "resize"
                                      #(draw-chart!
                                        (om/get-node owner "build-timings-svg")
                                        (om/get-props owner)))
                      gevents/unlistenByKey)))

    om/IWillUnmount
    (will-unmount [_]
      (disposable/dispose (om/get-state owner [:resize-key])))

    om/IDidUpdate
    (did-update [_ _ _]
      (when build
        (draw-chart! (om/get-node owner "build-timings-svg") build)))

    om/IRenderState
    (render-state [_ _]
      (html
       [:div
        (when-not build (spinner))
        [:svg {:ref "build-timings-svg"}]]))))

(defn upsell-message [plan owner]
  (reify
    om/IDidMount
    (did-mount [_]
      ((om/get-shared owner :track-event) {:event-type :build-timing-upsell-impression}))

    om/IRenderState
    (render-state [_ _]
      (let [{{plan-org-name :name
              plan-vcs-type :vcs_type} :org} plan]
        (html
         [:span.message "This release of Build Timing is only available for projects belonging to paid plans. Please "
          [:a.upgrade-link
           {:href (routes/v1-org-settings-path {:org plan-org-name
                                                :vcs_type plan-vcs-type})
            :on-click #((om/get-shared owner :track-event) {:event-type :build-timing-upsell-click})}
           "upgrade here."]])))))

;;;; Main component
(defn build-timings [{:keys [build project plan]} owner]
  (reify
    om/IRenderState
    (render-state [_ _]
      (html
       [:div.build-timings
        (if (project-model/show-build-timing? project plan)
          (om/build build-timings-chart build)
          (om/build upsell-message plan))]))))
