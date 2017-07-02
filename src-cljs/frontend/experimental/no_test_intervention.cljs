(ns frontend.experimental.no-test-intervention
  (:require [frontend.components.common :as common]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.modal :as modal]
            [frontend.models.feature :as feature]
            [frontend.utils :refer-macros [html]]
            [om.core :as om :include-macros true]))

(defn ab-test-treatment []
  (feature/ab-test-treatment :setup-docs-ab-test))

(defn- track-setup-docs-clicked [track-fn]
  (track-fn {:event-type :setup-docs-clicked}))

(defn- track-setup-docs-impression [track-fn]
  (track-fn {:event-type :setup-docs-impression}))

(defn show-intervention? [build]
  (-> build :outcome keyword (= :no_tests)))

(defn show-setup-docs-modal? [build]
  (and (show-intervention? build)
       (-> build :build_num (<= 3))))

(defn- setup-docs-link-props [track-fn]
  {:href "https://circleci.com/docs/manually"
   :target "_blank"
   :on-click #(track-setup-docs-clicked track-fn)})

(defn setup-docs-banner [_ owner]
  (let [track-fn (om/get-shared owner :track-event)]
    (reify
      om/IDidMount
      (did-mount [_]
        (track-setup-docs-impression track-fn))
      om/IRender
      (render [_]
        (html
          (let [content
                (html
                  [:div
                   [:span
                    (str "We couldn't detect the settings for your project! "
                         "Please make sure you have a circle.yml configuration "
                         "file in place, and check our doc about ")
                    [:a (setup-docs-link-props track-fn)
                        "manual build setup"]
                    "."]])]
            (common/message {:type :warning
                             :content content})))))))

(defn setup-docs-modal [{:keys [close-fn]} owner]
  (let [track-fn (om/get-shared owner :track-event)]
    (reify
      om/IDidMount
      (did-mount [_]
        (track-setup-docs-impression track-fn))
      om/IRender
      (render [_]
        (modal/modal-dialog
          {:title "We couldn't determine the test settings for your project"
           :body (html
                   [:span
                    (str "We're sorry about that! Please make sure you have a "
                         "circle.yml configuration file in place, and check "
                         "our doc about ")
                    [:a (setup-docs-link-props track-fn)
                        "manual build setup"]
                    "."])
           :actions [(button/button {:kind :primary
                                     :on-click close-fn}
                                    "Got it")]
           :close-fn close-fn})))))
