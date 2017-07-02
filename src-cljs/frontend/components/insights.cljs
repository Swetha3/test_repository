(ns frontend.components.insights
  (:require [clojure.string :as string]
            [devcards.core :as dc :refer-macros [defcard]]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.components.pieces.status :as status]
            [frontend.config :as config]
            [frontend.data.insights :as test-data]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.models.feature :as feature]
            [frontend.models.project :as project-model]
            [frontend.models.user :as user]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :refer [unexterned-prop] :refer-macros [defrender html]]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.events :as gevents]
            [goog.string :as gstring]
            [om.core :as om :include-macros true]
            [schema.core :as s :include-macros true]))

(def BarChartableBuild
  {:build_num s/Int
   :start_time (s/pred #(re-matches #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d:\d\d.\d\d\dZ" %))
   :build_time_millis s/Int
   :queued_time_millis s/Int
   :outcome s/Str
   s/Any s/Any})

(def default-plot-info
  {:top 10
   :right 10
   :bottom 10
   :left 30
   :max-bars 55
   :positive-y% 0.6
   :show-bar-title? true})

(defn add-legend [plot-info svg]
  (let [{:keys [square-size item-width item-height spacing]} (:legend-info plot-info)
        left-legend-enter (-> svg
                              (.select ".legend-container")
                              (.append "svg")
                              (.attr #js {"x" 0
                                          "y" 0
                                          "class" "left-legend-container"})
                              (.style #js {"overflow" "visible"})
                              (.selectAll ".legend")
                              (.data (clj->js (:left-legend-items plot-info)))
                              (.enter)
                              (.append "g")
                              (.attr #js {"class" "legend left"
                                          "transform"
                                          (fn [item i]
                                            (let [tr-x (* i item-width)
                                                  tr-y (- (- item-height
                                                             (/ (- item-height square-size)
                                                                2)))]
                                              (gstring/format "translate(%s,%s)" tr-x  tr-y)))}))
        right-legend-enter (-> svg
                               (.select ".legend-container")
                               (.append "svg")
                               (.attr #js {"x" "90%"
                                           "y" 0
                                           "class" "right-legend-container"})
                               (.style #js {"overflow" "visible"})
                               (.selectAll ".legend")
                               (.data (clj->js (:right-legend-items plot-info)))
                               (.enter)
                               (.append "g")
                               (.attr #js {"class" "legend right"
                                           "transform"
                                           (fn [item i]
                                             (let [tr-x (- (:width plot-info) (* (inc i) item-width))
                                                   tr-y (- (- item-height
                                                              (/ (- item-height square-size)
                                                                 2)))]
                                               (gstring/format "translate(%s,%s)" tr-x  tr-y)))}))]
    ;; left legend
    (-> left-legend-enter
        (.append "rect")
        (.attr #js {"width" square-size
                    "height" square-size
                    ;; `aget` must be used here instead of direct field access.  See note in preamble.
                    "class" #(aget % "classname")
                    "transform"
                    (fn [item i]
                      (gstring/format "translate(%s,%s)" 0  (- (+ square-size))))}))
    (-> left-legend-enter
        (.append "text")
        (.attr #js {"x" (+ square-size spacing)})
        (.text #(aget % "text")))

    ;; right legend
    (-> right-legend-enter
        (.append "rect")
        (.attr #js {"width" square-size
                    "height" square-size
                    "class" #(aget % "classname")
                    "transform"
                    (fn [item i]
                      (gstring/format "translate(%s,%s)" 0  (- (+ square-size))))}))
    (-> right-legend-enter
        (.append "text")
        (.attr #js {"x" (+ square-size
                           spacing)})
        (.text #(aget % "text")))))

(defn add-queued-time [build]
  (let [queued-time (max (build-model/queued-time build) 0)]
    (assoc build :queued_time_millis queued-time)))

(def insights-outcome-mapping
  {"success" "success",
   "failed" "failed",
   "timedout" "failed",
   "canceled" "canceled"})

(def fail-outcomes
  (->> insights-outcome-mapping
       (filter #(= "failed" (second %)))
       (into {})))

(def pass-fail-outcomes
  (->> insights-outcome-mapping
       (filter #(#{"success" "failed"} (second %)))
       (into {})))

(defn build-failed? [{:keys [outcome]}]
  (-> outcome
      fail-outcomes
      boolean))

(defn build-chartable? [{:keys [outcome build_time_millis]}]
  (boolean
   (or (pass-fail-outcomes outcome)
       (and (= "canceled" outcome)
            build_time_millis))))

(defn build-timing-url [build]
  (str (utils/uri-to-relative (unexterned-prop build "build_url"))
       "#build-timing"))

(defn visualize-insights-bar! [plot-info el builds {:keys [on-focus-build on-mouse-move]
                                                    :or {on-focus-build (constantly nil)
                                                         on-mouse-move (constantly nil)}
                                                    :as events} owner]
  (let [[y-pos-max y-neg-max] (->> [:build_time_millis :queued_time_millis]
                                   (map #(->> builds
                                              (map %)
                                              (apply max))))
        svg (-> js/d3
                (.select el)
                (.select "svg")
                ;; Set the SVG up to redraw itself when it resizes.
                (.property "redraw-fn" (constantly #(visualize-insights-bar! plot-info el builds events owner)))
                (.on "mousemove" #(on-mouse-move (d3.mouse el))))
        svg-bounds (-> svg
                       ffirst
                       .getBoundingClientRect)

        ;; The width and height of the area we'll draw the bars in. (Excludes,
        ;; for instance, the Y-axis labels on the left.)
        width (- (.-width svg-bounds) (:left plot-info) (:right plot-info))
        height (- (.-height svg-bounds) (:top plot-info) (:bottom plot-info))

        y-zero (* height (:positive-y% plot-info))
        show-bar-title? (:show-bar-title? plot-info)
        y-pos-scale (-> (js/d3.scale.linear)
                        (.domain #js[0 y-pos-max])
                        (.range #js[y-zero 0]))
        y-neg-scale (-> (js/d3.scale.linear)
                        (.domain #js[0 y-neg-max])
                        (.range #js[y-zero height]))
        y-pos-floored-max (datetime/nice-floor-duration y-pos-max)
        y-pos-tick-values [y-pos-floored-max 0]
        y-neg-tick-values [(datetime/nice-floor-duration y-neg-max)]
        [y-pos-axis y-neg-axis] (for [[scale tick-values] [[y-pos-scale y-pos-tick-values]
                                                           [y-neg-scale y-neg-tick-values]]]
                                  (-> (js/d3.svg.axis)
                                      (.scale scale)
                                      (.orient "left")
                                      (.tickValues (clj->js tick-values))
                                      (.tickFormat #(first (datetime/millis-to-float-duration % {:decimals 0})))
                                      (.tickSize 0 0)
                                      (.tickPadding 3)))
        scale-filler (->> (- (:max-bars plot-info) (count builds))
                          range
                          (map (partial str "xx-")))
        x-scale (-> (js/d3.scale.ordinal)
                    (.domain (clj->js
                              (concat scale-filler (map :build_num builds))))
                    (.rangeBands #js[0 width] 0.4))
        plot (-> svg
                 (.select "g.plot-area"))
        bars-join (-> plot
                      (.select "g > g.bars")
                      (.selectAll "g.bar-pair")
                      (.data (clj->js builds))
                      (.on "mouseover" #(on-focus-build %))
                      (.on "mousemove" #(on-focus-build %)))
        bars-enter-g (-> bars-join
                         (.enter)
                         (.append "g")
                         (.attr "class" "bar-pair"))
        grid-y-pos-vals (for [tick (remove zero? y-pos-tick-values)] (y-pos-scale tick))
        grid-y-neg-vals (for [tick (remove zero? y-neg-tick-values)] (y-neg-scale tick))
        grid-lines-join (-> plot
                            (.select "g.grid-lines")
                            (.selectAll "line.horizontal")
                            (.data (clj->js (concat grid-y-pos-vals grid-y-neg-vals))))]

    ;; top bar enter
    (-> bars-enter-g
        (.append "a")
        (.attr "class" "top")
        (.append "rect")
        (.attr "class" "bar"))

    ;; bottom (queue time) bar enter
    (-> bars-enter-g
        (.append "a")
        (.attr "class" "bottom")
        (.append "rect")
        (.attr "class" "bar bottom queue"))

    ;; top bars enter and update
    (-> bars-join
        (.select ".top")
        (.attr #js {"xlink:href" build-timing-url})
        (.attr #js {"xlink:title" #(when show-bar-title?
                                     (let [duration-str (datetime/as-duration (unexterned-prop % "build_time_millis"))]
                                       (gstring/format "%s in %s"
                                                       (gstring/toTitleCase (unexterned-prop % "outcome"))
                                                       duration-str)))})
        (.on #js {"click" #((om/get-shared owner :track-event) {:event-type :insights-bar-clicked
                                                                :properties {:build-url (unexterned-prop % "build_url")}})})
        (.select "rect.bar")
        (.attr #js {"class" #(str "bar " (-> %
                                             (unexterned-prop "outcome")
                                             insights-outcome-mapping))
                    "y" #(y-pos-scale (unexterned-prop % "build_time_millis"))
                    "x" #(x-scale (unexterned-prop % "build_num"))
                    "width" (.rangeBand x-scale)
                    "height" #(- y-zero (y-pos-scale (unexterned-prop % "build_time_millis")))}))

    ;; bottom bar enter and update
    (-> bars-join
        (.select ".bottom")
        (.attr #js {"xlink:href" build-timing-url})
        (.attr #js {"xlink:title" #(when show-bar-title?
                                     (let [duration-str (datetime/as-duration (unexterned-prop % "queued_time_millis"))]
                                       (gstring/format "Queue time %s" duration-str)))})

        (.on #js {"click" #((om/get-shared owner :track-event) {:event-type :insights-bar-clicked
                                                                :properties {:build-url (unexterned-prop % "build_url")}})})
        (.select "rect.bar")
        (.attr #js {"y" y-zero
                    "x" #(x-scale (unexterned-prop % "build_num"))
                    "width" (.rangeBand x-scale)
                    "height" #(- (y-neg-scale (unexterned-prop % "queued_time_millis")) y-zero)}))

    ;; bars exit
    (-> bars-join
        (.exit)
        (.remove))

    ;; y-axis
    (-> plot
        (.select ".axis-container g.y-axis.positive")
        (.call y-pos-axis))
    (-> plot
        (.select ".axis-container g.y-axis.negative")
        (.call y-neg-axis))
    ;; x-axis
    (-> plot
        (.select ".axis-container g.axis.x-axis line")
        (.attr #js {"y1" y-zero
                    "y2" y-zero
                    "x1" 0
                    "x2" width}))

    ;; grid lines enter
    (-> grid-lines-join
        (.enter)
        (.append "line"))
    ;; grid lines enter and update
    (-> grid-lines-join
        (.attr #js {"class" "horizontal"
                    "y1" (fn [y] y)
                    "y2" (fn [y] y)
                    "x1" 0
                    "x2" width}))))

(defn insert-skeleton [plot-info el]
  (let [svg (-> js/d3
                (.select el)
                (.append "svg"))
        plot-area (-> svg
                      (.attr #js {"xlink" "http://www.w3.org/1999/xlink"})
                      (.append "g")
                      (.attr "class" "plot-area")
                      (.attr "transform" (gstring/format "translate(%s,%s)"
                                                         (:left plot-info)
                                                         (:top plot-info))))]

    ;; Call the svg's "redraw-fn" (if set) whenever the svg resizes.
    ;;
    ;; There's no reliable way to get the svg to fire an event when it resizes.
    ;; There's an SVGResize event that sometimes works, and a resize event that
    ;; sometimes works, and an onresize attribute that sometimes works. The one
    ;; thing that works consistently is adding an invisible iframe pinned to the
    ;; size of the svg and listening to *its* resize event.
    (-> svg
        (.append "foreignObject")
        (.attr #js {:width "100%"
                    :height "100%"})
        (.style #js {:visibility "hidden"})
        (.append "xhtml:iframe")
        (.attr #js {:width "100%"
                    :height "100%"})
        ffirst
        .-contentWindow
        (gevents/listen "resize" #(when-let [redraw-fn (.property svg "redraw-fn")] (redraw-fn))))

    (when-let [legend-info (:legend-info plot-info)]
      (-> svg
          (.append "g")
          (.attr #js {"class" "legend-container"
                      "transform" (gstring/format "translate(%s,%s)"
                                                  (:left plot-info)
                                                  (:top legend-info))}))
      (add-legend plot-info svg))

    (-> plot-area
        (.append "g")
        (.attr "class" "grid-lines"))
    (-> plot-area
        (.append "g")
        (.attr "class" "bars"))

    (let [axis-container (-> plot-area
                             (.append "g")
                             (.attr "class" "axis-container"))]
      (-> axis-container
          (.append "g")
          (.attr "class" "x-axis axis")
          (.append "line"))
      (-> axis-container
          (.append "g")
          (.attr "class" "y-axis positive axis"))
      (-> axis-container
          (.append "g")
          (.attr "class" "y-axis negative axis")))))

(defn filter-chartable-builds [builds max-count]
  (some->> builds
           (filter build-chartable?)
           (take max-count)
           reverse
           (map add-queued-time)))

(defn median [xs]
  (let [nums (sort xs)
        c (count nums)
        mid-i (js/Math.floor (/ c 2))]
    (cond
      (zero? c) nil
      (odd? c) (nth nums mid-i)
      :else (/ (+ (nth nums mid-i)
                  (nth nums (dec mid-i)))
               2))))

(defn pass-percent [builds]
  (-> (map :outcome builds)
      frequencies
      (get "success")
      (/ (count builds))
      (* 100)
      (->> (gstring/format "%.1f %%"))))

(defn build-status-bar-chart [{:keys [plot-info builds] :as params} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [el (om/get-node owner)]
        (insert-skeleton plot-info el)
        (visualize-insights-bar! plot-info el builds (select-keys params [:on-focus-build :on-mouse-move]) owner)))
    om/IDidUpdate
    (did-update [_ prev-props prev-state]
      (let [el (om/get-node owner)]
        (visualize-insights-bar! plot-info el builds (select-keys params [:on-focus-build :on-mouse-move]) owner)))
    om/IRender
    (render [_]
      (html
       [:div {:data-component (str `build-status-bar-chart)}]))))

(defn formatted-project-name [{:keys [username reponame]}]
  (gstring/format "%s/%s" username reponame))

(defn project-insights
  [{{:keys [show-insights? reponame username branches recent-builds chartable-builds sort-category parallel default_branch vcs_type]
     :as project} :project
    {:keys [clickable-header?]
     :or {clickable-header? true}
     :as opts} :opts} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (when (not show-insights?)
        ((om/get-shared owner :track-event) {:event-type :build-insights-upsell-impression
                                             :properties {:repo (project-model/repo-name project)
                                                          :org (project-model/org-name project)}})))
    om/IRender
    (render [_]
      (html
        (let [branch default_branch
              latest-build (last chartable-builds)
              org-name (project-model/org-name project)
              repo-name (project-model/repo-name project)
              vcs-icon (case vcs_type
                         "github" (icon/github)
                         "bitbucket" (icon/bitbucket)
                         nil)]
          [:div.project-block {:class (str "build-" (name sort-category))}
           [:h1.project-header
            [:.last-build-status (if latest-build
                                   (status/build-icon (build-model/build-status latest-build))
                                   (status/icon :status-class/succeeded))]
            [:.project-name
             (if (and show-insights?
                      clickable-header?)
               [:a {:href (routes/v1-project-insights-path {:org org-name
                                                            :repo repo-name
                                                            :branch (:default_branch project)
                                                            :vcs_type (:vcs_type project)})}
                (formatted-project-name project)]
               (formatted-project-name project))]
            [:.vcs-icon
             (if clickable-header?
               [:a.exception {:href (:vcs_url project)} vcs-icon]
               [:span vcs-icon])]
            (when (and (project-model/can-write-settings? project)
                       clickable-header?)
             [:.settings-icon
              [:a.exception {:href (routes/v1-project-settings-path {:org username
                                                                     :repo reponame
                                                                     :vcs_type vcs_type})}
               (icon/settings)]])]
           [:h4 (if show-insights?
                  (str "Branch: " branch)
                  (gstring/unescapeEntities "&nbsp;"))]
           (cond (nil? (get recent-builds default_branch)) (spinner)
                 (not show-insights?) [:div.no-insights
                                       [:div.message "This release of Insights is only available for projects belonging to paid plans."]
                                       [:a.upgrade-link {:href (routes/v1-org-settings-path {:org (vcs-url/org-name (:vcs_url project))
                                                                                             :vcs_type (:vcs_type project)})
                                                         :on-click #((om/get-shared owner :track-event) {:event-type :build-insights-upsell-click
                                                                                                         :properties  {:repo repo-name
                                                                                                                       :org org-name}})} "Upgrade here"]]
                (empty? chartable-builds) [:div.no-builds "No builds to display for this project"]
                :else
                (list
                  [:div.above-info
                   [:dl
                    [:dt "median build"]
                    [:dd (datetime/as-duration (median (map :build_time_millis chartable-builds))) " min"]]
                   [:dl
                    [:dt "median queue"]
                    [:dd (datetime/as-duration (median (map :queued_time_millis chartable-builds))) " min"]]
                   [:dl
                    [:dt "last build"]
                    [:dd (om/build common/updating-duration
                                   {:start (->> chartable-builds
                                                reverse
                                                (filter :start_time)
                                                first
                                                :start_time)}
                                   {:opts {:formatter datetime/as-time-since
                                           :formatter-use-start? true}})]]]
                  [:div.body-info
                   (om/build build-status-bar-chart {:plot-info default-plot-info
                                                     :builds chartable-builds})]
                  [:div.below-info
                   [:dl
                    [:dt "success rate"]
                   [:dd (pass-percent chartable-builds)]]
                  [:dl
                   [:dt "parallelism"]
                   [:dd parallel]]]))])))))

(defrender no-projects [data owner]
  (html
   [:div.no-projects-block
    [:div.content
     [:div.row
      [:div.header.text-center "No Insights yet"]]
     [:div.details.text-center "Add projects from your Github orgs and start building on CircleCI to view insights."]
     [:div.row.text-center
      [:a.btn.btn-success {:href (routes/v1-add-projects)} "Add Project"]]]]))

(defn project-sort-category
  "Returns symbol representing category for sorting.

  One of #{:pass :fail :other}"
  [{:keys [show-insights? chartable-builds] :as project}]
  (let [outcome (some->> chartable-builds
                         (map :outcome)
                         reverse
                         (filter pass-fail-outcomes)
                         first)]
    (if-let [result (and show-insights?
                         (-> outcome
                             pass-fail-outcomes
                             keyword))]
      result
      :other)))

(defn project-latest-build-time [project]
  (let [start-time (-> project
                       :chartable-builds
                       last
                       :start_time)]
    (js/Date. start-time)))

(defn decorate-project
  "Add keys to project related to insights - :show-insights? :sort-category :chartable-builds ."
  [{:keys [max-bars] :as plot-info} plans {:keys [recent-builds default_branch] :as project}]
  (let [chartable-builds (filter-chartable-builds (get recent-builds default_branch)
                                                  max-bars)]
    (-> project
        (assoc :chartable-builds chartable-builds)
        (#(assoc % :show-insights? (project-model/show-insights? plans %)))
        (#(assoc % :sort-category (project-sort-category %)))
        (#(assoc % :latest-build-time (project-latest-build-time %))))))

(defrender cards [{:keys [projects selected-filter selected-sorting]} owner]
  (let [categories (-> (group-by :sort-category projects)
                       (assoc :all projects))
        filtered-projects (selected-filter categories)
        sorted-projects (case selected-sorting
                          :alphabetical (->> filtered-projects
                                             (sort-by #(-> %
                                                           formatted-project-name
                                                           ((juxt string/lower-case identity)))))
                          :recency (->> filtered-projects
                                        (sort-by :latest-build-time)
                                        reverse))]
    (html
     [:div
      [:div.controls
       [:span.filtering
        (for [[filter-name filter-label] [[:all "All"]
                                          [:success "Success"]
                                          [:failed "Failed"]]]
          (let [filter-input-id (str "insights-filter-" (name filter-name))]
            (list
             [:input {:id filter-input-id
                      :type "radio"
                      :name "selected-filter"
                      :checked (= selected-filter filter-name)
                      :on-change #(raise! owner [:insights-filter-changed {:new-filter filter-name}])}]
             [:label {:for filter-input-id}
              (gstring/format "%s (%s)" filter-label (count (filter-name categories)))])))]
       [:span.sorting
        [:label "Sort: "]
        [:select {:class "toggle-sorting"
                  :on-change #(raise! owner [:insights-sorting-changed {:new-sorting (keyword (.. % -target -value))}])
                  :value (name selected-sorting)}
         [:option {:value "alphabetical"} "Alphabetical"]
         [:option {:value "recency"} "Recent"]]]]
      [:div.blocks-container
       (om/build-all project-insights
                     (map (fn [proj]
                            {:project proj})
                          sorted-projects))]])))

(defrender build-insights [state owner]
  (let [selected-org-login (:login (get-in state state/selected-org-path))
        selected-org-vcs-type (:vcs_type (get-in state state/selected-org-path))
        projects (if (feature/enabled? "top-bar-ui-v-1")
                   (filter #(and (= selected-org-login
                                    (:username %))
                                 (= selected-org-vcs-type
                                    (:vcs_type %)))
                         (get-in state state/projects-path))
                   (get-in state state/projects-path))
        plans (get-in state state/user-plans-path)
        navigation-data (:navigation-data state)
        decorate (partial decorate-project default-plot-info plans)]
    (html
     [:div#build-insights
      (cond
        (not (user/has-code-identity? (get-in state state/user-path)))
        (om/build empty-state/full-page-empty-state
          {:name "Insights"
           :icon (icon/insights)
           :description "An interactive graph of your software builds, highlighting failed and successful builds. This page allows you to monitor your build performance holistically."
           :demo-heading "Demos"
           :demo-description "The following graph is shown for demonstration. Click the title link for a larger graph of build performance or click a bar to see details for a single build."
           :content [:div.insights-empty-state-content
                     (om/build project-insights {:project test-data/test-project
                                                 :opts {:clickable-header? false}})]})

        ;; Still loading projects
        (nil? projects)
        [:div.empty-placeholder (spinner)]

        ;; User has no projects
        (empty? projects)
        (om/build no-projects state)

        ;; User is looking at all projects
        :else
        (om/build cards {:projects (map decorate projects)
                         :selected-filter (get-in state state/insights-filter-path)
                         :selected-sorting (get-in state state/insights-sorting-path)}))])))


(when config/client-dev?
  (defcard build-status-bar-chart
    (om/build build-status-bar-chart {:plot-info default-plot-info
                                      :builds test-data/some-builds})))
