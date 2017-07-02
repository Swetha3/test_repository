(ns frontend.components.build-steps
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.datetime :as datetime]
            [frontend.models.action :as action-model]
            [frontend.models.container :as container-model]
            [frontend.models.build :as build-model]
            [frontend.components.common :as common]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.state :as state]
            [frontend.disposable :as disposable :refer [dispose]]
            [frontend.utils :as utils :refer-macros [html]]
            [frontend.utils.html :as html-utils]
            [om.core :as om :include-macros true]
            [goog.events]
            [goog.string :as gstring]
            goog.dom
            goog.style
            goog.string.format
            goog.fx.dom.Scroll
            goog.fx.easing))

(defn source-type [source]
  (condp = source
    "db" "UI"
    "template" "standard"
    source))

(defn source-title [source]
  (condp = source
    "template" "CircleCI generated this command automatically"
    "cache" "CircleCI caches some subdirectories to significantly speed up your tests"
    "config" "You specified this command in your circle.yml file"
    "inference" "CircleCI inferred this command from your source code and directory layout"
    "db" "You specified this command on the project settings page"
    "Unknown source"))

(defn mount-browser-resize
  "Handles scrolling the container on the build page to the correct position when
  the size of the browser window chagnes. Has to add an event listener at the top level."
  [owner]
  (om/set-state! owner [:browser-resize-key]
                 (disposable/register
                   (goog.events/listen
                     js/window
                     "resize"
                     #(raise! owner
                            ;; This is pretty hacky, it would be nice if we had a better way to do this
                            [:container-selected {:container-id (state/current-container-id @(om/get-shared owner [:_app-state-do-not-use]))
                                                  :animate? false}]))
                   goog.events/unlistenByKey)))

(defn check-autoscroll [owner deltaY]
  (cond

   ;; autoscrolling and scroll up? That means stop autoscrolling.
   (and (neg? deltaY)
        (om/get-state owner [:autoscroll?]))
   (om/set-state! owner [:autoscroll?] false)

   ;; not autoscrolling and scroll down? If they scrolled all of the way down, better autoscroll
   (and (pos? deltaY)
        (not (om/get-state owner [:autoscroll?])))
   (let [container (om/get-node owner)]
     (when (> (.-height (goog.dom/getViewportSize))
              (.-bottom (.getBoundingClientRect container)))
       (om/set-state! owner [:autoscroll?] true)))

   :else nil))

(defn output [out owner]
  (reify
    om/IRender
    (render [_]
      (let [message-html (:converted-message out)]
        (html
         [:span.pre {:dangerouslySetInnerHTML
                     #js {"__html" message-html}}])))))

(defn truncation-notification [action ownder]
  (reify
    om/IRender
    (render [_]
      (html
       (when (:truncated-client-side? action)
         [:span.truncated
          (gstring/format
           "Your build output is too large to display in the browser. Only the first %s characters are displayed."
           action-model/max-output-size)
          [:br]
          (if (= "running" (:status action))
            "You can download the output once this step is finished."
            [:a {:href (:user-facing-output-url action)
                 :download (:user-facing-output-filename action)
                 :target "_blank"}
             (if (:truncated action)
               (gstring/format "Download the first %s as a file." (:truncation_len action))
               "Download the full output as a file.")])])))))

(defn action [action owner {:keys [container-id uses-parallelism?] :as opts}]
  (reify
    om/IRender
    (render [_]
      ;; TODO: the action should not be deciding if it is visible, the parent component
      ;; should decide which actions are visible and pass that information in.
      ;; See discussion here - https://github.com/circleci/frontend-private/pull/1269/files
      (let [visible? (action-model/visible? action container-id)
            step-id (str "action-" (:step action))
            header-classes  (concat [(:status action)]
                                    (when visible?
                                      ["open"])
                                    (when (action-model/has-content? action)
                                      ["contents"])
                                    (when (action-model/failed? action)
                                      ["failed"]))]
        (html
         [:div {:class (str "type-" (or (:type action) "none")) :id step-id}
          [:div.type-divider
           [:span (:type action)]]
          [:div.build-output
           [:div.action_header {:class header-classes}
            [:div.ah_wrapper
             [:div.header {:class (when (action-model/has-content? action)
                                    header-classes)
                           ;; TODO: figure out what to put here
                           :on-click #(raise! owner [:action-log-output-toggled
                                                     {:index (:index action)
                                                      :step (:step action)
                                                      :value (not visible?)}])}
              [:div.button {:class (when (action-model/has-content? action)
                                     header-classes)}
               (when (action-model/has-content? action)
                 [:i.fa.fa-chevron-right])]
              [:div.command {:class header-classes}
               [:span.command-text {:title (:bash_command action)}
                (str (when (= (:bash_command action)
                              (:name action))
                       "$ ")
                     (:name action)
                     (when (and uses-parallelism? (:parallel action))
                       (gstring/format " (%s)" (:index action))))]
               [:span.time {:title (str (:start_time action) " to "
                                        (:end_time action))}
                (om/build common/updating-duration {:start (:start_time action)
                                                    :stop (:end_time action)})
                (when (:timedout action) " (timed out)")]
               [:span.action-source
                [:span.action-source-inner {:title (source-title (:source action))}
                 (source-type (:source action))]]]]
             [:div.detail-wrapper
              (when (and visible? (action-model/has-content? action))
                [:div.detail {:class header-classes}
                 (if (and (:has_output action)
                          (nil? (:output action)))
                   (spinner)

                   [:div.action-log-messages
                    (common/messages (:messages action))
                    [:i.click-to-scroll.fa.fa-arrow-circle-o-down.pull-right
                     {:on-click #(utils/scroll-to-node-bottom (.-parentNode (.-currentTarget %)))}]
                    (when (:bash_command action)
                      [:span
                       (when (:exit_code action)
                         [:span.exit-code.pull-right
                          (str "Exit code: " (:exit_code action))])
                       [:pre.bash-command
                        {:title "The full bash command used to run this setup"}
                        (:bash_command action)]])
                    [:pre.output
                     (om/build truncation-notification action)
                     (om/build-all output (:output action) {:key :react-key})
                     (om/build truncation-notification action)]])])]]]]])))))

(defn container-view [{:keys [container non-parallel-actions]} owner {:keys [uses-parallelism?] :as opts}]
  (reify
    om/IRender
    (render [_]
      (let [container-id (container-model/id container)
            actions (remove :filler-action
                            (map (fn [action]
                                   (get non-parallel-actions (:step action) action))
                                 (:actions container)))]
        (html
         [:div.container-view {:id (str "container_" (:index container))}
          (om/build-all action actions {:key :step
                                        :opts (merge opts {:container-id container-id})})])))))

(defn container-build-steps [{:keys [containers selected-container-id]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:autoscroll? false})
    om/IDidMount
    (did-mount [_]
      (mount-browser-resize owner))
    om/IWillUnmount
    (will-unmount [_]
      (dispose (om/get-state owner [:browser-resize-key])))
    om/IDidUpdate
    (did-update [_ _ _]
      (when (om/get-state owner [:autoscroll?])
        (when-let [node (om/get-node owner)]
          (utils/scroll-to-node-bottom node))))
    om/IRender
    (render [_]
      (let [non-parallel-actions (->> containers
                                      first
                                      :actions
                                      (remove :parallel)
                                      (map (fn [action]
                                             [(:step action) action]))
                                      (into {}))]
        (html
          [:div {:on-wheel (fn [e]
                             (check-autoscroll owner (aget e "deltaY"))
                             (let [body (.-body js/document)]
                               (set! (.-scrollTop body) (+ (.-scrollTop body) (aget e "deltaY")))))
                 :class (str "selected_" selected-container-id)}
           (om/build container-view
                     {:container (get containers selected-container-id)
                      :non-parallel-actions non-parallel-actions}
                     {:opts {:uses-parallelism? (< 1 (count containers))}})])))))
