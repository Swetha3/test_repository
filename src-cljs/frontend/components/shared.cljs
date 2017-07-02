(ns frontend.components.shared
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.disposable :as disposable :refer [dispose]]
            [frontend.state :as state]
            [frontend.stefon :refer [data-uri]]
            [frontend.utils.ajax :as ajax]
            [frontend.utils :as utils :refer-macros [html]]
            [frontend.utils.github :as gh-utils]
            [goog.style]
            [om.core :as om :include-macros true])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(defn customers-trust [& {:keys [company-size]
                          :or {company-size "big-company"}}]
  [:div.customers-trust.row
   [:h4 [:span "Trusted By"]]
   [:div {:class company-size}
    [:img {:title "Salesforce" :src (data-uri "/img/logos/salesforce.png")} " "] " "]
   " "
   [:div {:class company-size}
    [:img {:title "Samsung" :src (data-uri "/img/logos/samsung.png")} " "] " "]
   " "
   [:div {:class company-size}
    [:img {:title "Kickstarter" :src (data-uri "/img/logos/kickstarter.png")} " "] " "]
   " "
   [:div {:class company-size}
    [:img {:title "Cisco", :src (data-uri "/img/logos/cisco.png")} " "] " "]
   " "
   [:div {:class company-size}
    [:img {:title "Shopify" :src (data-uri "/img/logos/shopify.png")} " "] " "]
   [:span.stretch]])

(def stories-procedure
  [:div {:dangerouslySetInnerHTML #js {"__html"
                                       "<svg class=\"stories-procedure\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 960 480\" width=\"960\" height=\"480\">
    <path class=\"slot github\" d=\"M240,69.5c-18.2,0-33,14.8-33,33 c0,14.6,9.5,26.9,22.6,31.3c1.6,0.3,2.3-0.7,2.3-1.6c0-0.8,0-3.4,0-6.1c-9.2,2-11.1-3.9-11.1-3.9c-1.5-3.8-3.7-4.8-3.7-4.8 c-3-2,0.2-2,0.2-2c3.3,0.2,5.1,3.4,5.1,3.4c2.9,5,7.7,3.6,9.6,2.7c0.3-2.1,1.2-3.6,2.1-4.4c-7.3-0.8-15-3.7-15-16.3 c0-3.6,1.3-6.5,3.4-8.9c-0.3-0.8-1.5-4.2,0.3-8.7c0,0,2.8-0.9,9.1,3.4c2.6-0.7,5.5-1.1,8.3-1.1c2.8,0,5.6,0.4,8.3,1.1 c6.3-4.3,9.1-3.4,9.1-3.4c1.8,4.5,0.7,7.9,0.3,8.7c2.1,2.3,3.4,5.3,3.4,8.9c0,12.7-7.7,15.5-15.1,16.3c1.2,1,2.2,3,2.2,6.1 c0,4.4,0,8,0,9.1c0,0.9,0.6,1.9,2.3,1.6c13.1-4.4,22.5-16.7,22.5-31.3C273,84.2,258.2,69.5,240,69.5z\"></path>
    <path class=\"slot developer\" d=\"M267.7,395.8c0,0-2.8-9.1-9.9-16.3c-2-2.1-7.3-4.2-10.9-1.1c-6.3,5.3-6.3,17.4-6.3,17.4 H267.7z M237,395.8c0-3.3-2.7-6-6-6c-2.7,0-4.9,1.8-5.7,4.2l-10.6,0c-0.1,0-0.6,0-1-0.4c-0.4-0.3-0.3-0.8-0.3-1.1 c0-0.1,0-0.1,0-0.1l0,0c1.7-6,5.7-15.3,6.2-16.1c0.1-0.2,0.5-0.5,0.8-0.6l0.3-0.1l0,0.2c1.9,8.9,3.8,12,3.8,12.2l0.2,0.3l2.5-0.5 l-5.9-28.3l-2.6,0.5l0,0.4c0,0.2-0.3,4.1,1.4,12.7c0,0,0,0,0,0l0.1,0.5l-0.4,0.2c-0.6,0.3-1.2,0.7-1.7,1.2c-0.5,0.6-4.4,9.6-6.6,17 c0,0.1,0,0.1,0,0.2c-0.2,1,0.1,1.9,0.6,2.6c0.9,1.1,2.3,1.1,2.5,1.1l13,0v0L237,395.8L237,395.8z M245.1,359.1l0.9,4.4 c1.6,0.4,2.8,1.8,2.8,3.5c0,2-1.6,3.6-3.6,3.6c-2,0-3.6-1.6-3.6-3.6c0-1.2,0.6-2.3,1.5-2.9l-0.9-4.8c-3.8,0.9-6.6,4.3-6.6,8.3 c0,4.7,3.8,8.6,8.6,8.6c4.7,0,8.6-3.8,8.6-8.6C252.7,363.3,249.4,359.6,245.1,359.1z M244.1,359.1c0.3,0,0.7,0,1,0.1l-0.6-3.3 l-2.9,0.6l0.6,2.9C242.8,359.2,243.4,359.1,244.1,359.1z\"></path>
    <path class=\"slot users\" d=\"M726.9,374.7c2.8-2,4.6-5.4,4.6-9.1c0-6.2-5-11.2-11.2-11.2s-11.2,5-11.2,11.1 c0,3.7,1.8,7.1,4.6,9.2c-6.3,2.6-10.8,9-10.8,16.3c0,9.6,34.8,9.6,34.8,0C737.7,383.7,733.2,377.2,726.9,374.7z M739.7,374.9 c2-1.5,3.3-3.9,3.3-6.6c0-4.5-3.7-8.2-8.2-8.2c-1,0-2,0.2-2.9,0.5c0.6,1.5,1,3.2,1,4.9c0,3.2-1.2,6.3-3.4,8.7 c5.9,3.3,9.6,9.6,9.6,16.6c0,0.3,0,0.6-0.1,0.9c4.7-0.6,8.5-2.2,8.5-4.8C747.6,381.2,744.4,376.8,739.7,374.9z M701.4,390.8 c0-6.9,3.6-13,9.3-16.4c0.1-0.1,0.2-0.2,0.3-0.2c-2.1-2.3-3.3-5.4-3.3-8.6c0-1.7,0.3-3.3,0.9-4.7c-1-0.5-2.2-0.7-3.4-0.7 c-4.5,0-8.2,3.7-8.2,8.2c0,2.7,1.3,5.2,3.3,6.7c-4.6,1.9-7.9,6.3-7.9,11.9c0,2.7,4.2,4.4,9.1,4.9 C701.4,391.4,701.4,391.1,701.4,390.8z\"></path>
    <path class=\"slot servers\" d=\"M697.5,96.2c0,0,0,7.4,0,9.2c0,3.6,10,6.6,22.4,6.6c12.4,0,22.4-3,22.4-6.6c0-1.8,0-9.2,0-9.2 s-5.9,5.6-22.4,5.6C701.2,101.7,697.5,96.2,697.5,96.2z M720,114.8c-18.7,0-22.4-5.6-22.4-5.6s0,10.7,0,12.5c0,3.6,10,6.6,22.4,6.6 c12.4,0,22.4-3,22.4-6.6c0-1.8,0-12.5,0-12.5S736.5,114.8,720,114.8z M719.7,123.7c-1.3,0-2.4-1.1-2.4-2.4c0-1.3,1.1-2.4,2.4-2.4 c1.3,0,2.4,1.1,2.4,2.4C722.1,122.7,721.1,123.7,719.7,123.7z M719.9,76.4c-12.4,0-22.4,3-22.4,6.6c0,0.4,7.6,7.9,32.4,4.8 c-5.6,2.1-24.7,3.1-32.4-3.1c0,2.4,0,6.1,0,7.5c0,3.6,10,6.6,22.4,6.6c12.4,0,22.4-3,22.4-6.6c0-1.8,0-7.4,0-9.2 C742.4,79.4,732.3,76.4,719.9,76.4z\"></path>
    <path class=\"slot circleci\" d=\"M480,229.1c6.6,0,11.9,5.3,11.9,11.9c0,6.6-5.3,11.9-11.9,11.9c-6.6,0-11.9-5.3-11.9-11.9 C468.1,234.4,473.4,229.1,480,229.1z M480,191c-23.3,0-42.9,15.9-48.4,37.5c0,0.2-0.1,0.4-0.1,0.6c0,1.3,1.1,2.4,2.4,2.4H454 c1,0,1.8-0.6,2.2-1.4c0,0,0-0.1,0-0.1c4.2-9,13.2-15.2,23.8-15.2c14.5,0,26.2,11.7,26.2,26.2c0,14.5-11.7,26.2-26.2,26.2 c-10.5,0-19.6-6.2-23.8-15.2c0,0,0-0.1,0-0.1c-0.4-0.8-1.2-1.4-2.2-1.4h-20.2c-1.3,0-2.4,1.1-2.4,2.4c0,0.2,0,0.4,0.1,0.6 c5.6,21.6,25.1,37.5,48.4,37.5c27.6,0,50-22.4,50-50C530,213.4,507.6,191,480,191z\"></path>
    <g class=\"step dev-to-github\">
      <path class=\"track\" d=\"M194.4,340.4 c-39.5-49.9-46.9-120.8-13.1-179.2c3.3-5.8,7-11.3,10.9-16.5\"></path>
      <polygon class=\"direction\" points=\"196.4,148.7 195.4,141.5 188.2,142.5\"></polygon>
      <path class=\"touch\" d=\"M194.5,365.4c-7.4,0-14.7-3.2-19.6-9.5c-22.7-28.7-36.4-63.2-39.4-99.9 c-3.1-37.5,5.3-74.7,24.2-107.4c3.8-6.6,8-13,12.6-19.1c8.3-11,24-13.2,35-4.9c11,8.3,13.2,24,4.9,35c-3.4,4.4-6.5,9.1-9.2,13.9 c-13.8,23.9-19.9,50.9-17.7,78.3c2.2,26.8,12.1,52,28.7,72.9c8.6,10.8,6.8,26.5-4.1,35.1C205.4,363.6,199.9,365.4,194.5,365.4z\"></path>
      <g class=\"caption\">
        <foreignObject x=\"200\" y=\"140\" width=\"200\" height=\"200\">
          <p style=\"height: 200px\">Developers commit new features to Github.</p>
        </foreignObject>
      </g>
    </g>
    <g class=\"step github-to-circle\">
      <path class=\"track\" d=\"M468.1,180.6 c-2.5-6-5.3-12-8.6-17.8c-32.9-58.9-97.6-88.9-160.6-80.5\"></path>
      <polygon class=\"direction\" points=\"462.4,180.6 469.2,183.4 472,176.6\"></polygon>
      <path class=\"touch\" d=\"M468.2,205.6c-9.9,0-19.2-5.9-23.2-15.6c-2.1-5.2-4.5-10.2-7.2-15.1 c-13.4-24.1-33.6-43.2-58.2-55.2c-24.1-11.8-50.8-16.2-77.3-12.7c-13.7,1.8-26.3-7.8-28.1-21.5s7.8-26.3,21.5-28.1 c36.3-4.8,72.9,1.2,105.9,17.3c33.8,16.6,61.4,42.8,79.8,75.8c3.7,6.6,7,13.6,9.9,20.6c5.2,12.8-0.9,27.4-13.7,32.6 C474.5,205,471.3,205.6,468.2,205.6z\"></path>
      <g class=\"caption\">
        <foreignObject x=\"200\" y=\"140\" width=\"200\" height=\"200\">
          <p style=\"height: 200px\">CircleCI creates a new build to test each commit.</p>
        </foreignObject>
      </g>
    </g>
    <g class=\"step failing\">
      <path class=\"track\" d=\"M300.5,399.8 c6.5,0.8,13.1,1.2,19.8,1.2c67.5,0,125.2-41.8,148.6-101\"></path>
      <polygon class=\"direction\" points=\"301.1,394.6 295.3,399.1 299.8,404.9\"></polygon>
      <g class=\"status\">
        <circle class=\"pin\" cx=\"400.7\" cy=\"379\" r=\"10\"></circle>
        <path class=\"mask\" d=\"M405.4,381c0.4,0.4,0.4,1,0,1.3l-1.3,1.3c-0.4,0.4-1,0.4-1.3,0l-2-2l-2,2 c-0.4,0.4-1,0.4-1.3,0l-1.3-1.3c-0.4-0.4-0.4-1,0-1.3l2-2l-2-2c-0.4-0.4-0.4-1,0-1.3l1.3-1.3c0.4-0.4,1-0.4,1.3,0l2,2l2-2 c0.4-0.4,1-0.4,1.3,0l1.3,1.3c0.4,0.4,0.4,1,0,1.3l-2,2L405.4,381z\"></path>
      </g>
      <path class=\"touch\" d=\"M320.2,426c-7.6,0-15.3-0.5-22.8-1.4c-13.7-1.7-23.4-14.2-21.8-27.9 c1.7-13.7,14.2-23.4,27.9-21.8c5.5,0.7,11.2,1,16.7,1c27.6,0,54-8.3,76.6-23.9c22.1-15.3,38.9-36.5,48.8-61.3 c5.1-12.8,19.6-19.1,32.4-14c12.8,5.1,19.1,19.6,14,32.4c-13.5,34.1-36.6,63.1-66.8,84C394.4,414.7,358,426,320.2,426z\"></path>
      <g class=\"caption\">
        <foreignObject x=\"200\" y=\"140\" width=\"200\" height=\"200\">
          <p style=\"height: 200px\">Failed builds provide feedback to developers.</p>
        </foreignObject>
      </g>
    </g>
    <g class=\"step passing\">
      <path class=\"track\" d=\"M661.8,82.9 c-6.5-0.9-13.1-1.4-19.7-1.4c-67.5-0.8-125.7,40.4-149.7,99.3\"></path>
      <polygon class=\"direction\" points=\"659.1,87.4 664.9,82.9 660.4,77.1\"></polygon>
      <g class=\"status\">
        <circle class=\"pin\" cx=\"560.9\" cy=\"101.9\" r=\"10\"></circle>
        <path class=\"mask\" d=\"M567,99.9l-5.4,5.4l-1.3,1.3c-0.4,0.4-1,0.4-1.3,0l-1.3-1.3l-2.7-2.7c-0.4-0.4-0.4-1,0-1.3 l1.3-1.3c0.4-0.4,1-0.4,1.3,0l2,2l4.7-4.7c0.4-0.4,1-0.4,1.3,0l1.3,1.3C567.4,98.9,567.4,99.5,567,99.9z\"></path>
      </g>
      <path class=\"touch\" d=\"M492.3,205.8c-3.2,0-6.4-0.6-9.5-1.9c-12.8-5.2-18.9-19.8-13.7-32.6 c13.9-33.9,37.3-62.7,67.7-83.3c31.2-21.1,67.7-32,105.4-31.6c7.6,0.1,15.3,0.6,22.8,1.7c13.7,1.8,23.3,14.4,21.4,28.1 c-1.8,13.7-14.4,23.3-28.1,21.4c-5.5-0.7-11.1-1.2-16.7-1.2c-27.6-0.3-54.1,7.6-76.9,23c-22.2,15-39.3,36-49.5,60.8 C511.5,199.9,502.2,205.8,492.3,205.8z\"></path>
      <g class=\"caption right\">
        <foreignObject x=\"567\" y=\"140\" width=\"200\" height=\"200\">
          <p style=\"height: 200px\">New features are deployed once all tests pass.</p>
        </foreignObject>
      </g>
    </g>
    <g class=\"step servers-to-users\">
      <path class=\"track\" d=\"M767.8,337.5 c3.9-5.2,7.6-10.7,10.9-16.5c33.7-58.5,26.2-129.4-13.4-179.2\"></path>
      <polygon class=\"direction\" points=\"772.5,339.7 765.2,340.7 764.2,333.5\"></polygon>
      <path class=\"touch\" d=\"M767.8,362.5c-5.2,0-10.5-1.6-15-5c-11-8.3-13.2-24-4.9-35c3.3-4.4,6.5-9.1,9.2-14 c13.7-23.9,19.8-51,17.5-78.3c-2.2-26.7-12.2-51.9-28.8-72.9c-8.6-10.8-6.8-26.5,4-35.1c10.8-8.6,26.5-6.8,35.1,4 c22.8,28.7,36.5,63.2,39.5,99.8c3.1,37.5-5.2,74.7-24,107.4c-3.8,6.6-8,13-12.6,19.1C782.8,359,775.4,362.5,767.8,362.5z\"></path>
      <g class=\"caption right\">
        <foreignObject x=\"567\" y=\"140\" width=\"200\" height=\"200\">
          <p style=\"height: 200px\">Users constantly have access to latest features.</p>
        </foreignObject>
      </g>
    </g>
  </svg>"}}])

;; Since we can't reliably make an html5 range element look like we
;; want, fake it with divs for the bar, the knob and a highlight div
;;
;; Work in progress. Remaining issues:
;; * still hardcoded state/selected-containers-path to update state
;;   value
;; * doesn't display min and max; good? bad? some other way?
(defn styled-range-slider [data owner]
  (reify

    om/IInitState
    (init-state [_]
      {:drag-id nil
       :node-ref (str "pricing-range-" (Math.random))})

    om/IRenderState
    (render-state [this {:keys [dragging? node-ref]}]
      (let [min-val (get-in data [:min-val])
            max-val (get-in data [:max-val])
            value (or (get-in data state/selected-containers-path)
                      (get-in data [:start-val])
                      min-val)
            increment (/ 100.0 max-val)
            dragging (fn [event]
                       (let [node-ref (om/get-state owner :node-ref)
                             slider (om/get-node owner node-ref)
                             width (.-width (goog.style/getSize slider))
                             slider-left (goog.style/getPageOffsetLeft slider)
                             event-left (.-pageX event)
                             drag-fraction (min 1.0 (max 0.0 (/ (- event-left slider-left) width)))
                             value (Math.round (+ min-val (* drag-fraction (- max-val min-val))))]
                         (utils/edit-input owner state/selected-containers-path event :value value)
                         (.stopPropagation event)
                         (.preventDefault event)))
            drag-done (fn [event]
                        (dragging event)
                        (dispose (om/get-state owner :drag-id))
                        (om/set-state! owner :drag-id nil))]
        (html
         [:div.range-slider {:on-mouse-down #(let [listeners (mapv (fn [[on f :as listener]]
                                                                     (.addEventListener js/document on f)
                                                                     listener)
                                                                   [["mousemove" dragging]
                                                                    ["mouseup" drag-done]])
                                                   dispose (fn [listeners]
                                                             (doseq [[on f] listeners]
                                                               (.removeEventListener js/document on f)))]
                                               (om/set-state! owner :drag-id (disposable/register listeners dispose)))
                             :ref node-ref}
          ;[:span min-val]
          [:figure.range-back]
          [:figure.range-highlight {:style {:width (str (* value increment) "%")}}]
          [:div.range-knob {:style {:left (str (* value increment) "%")}
                            :data-count value}]
          ;[:span max-val]
          ])))

    ))
