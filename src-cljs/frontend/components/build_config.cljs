(ns frontend.components.build-config
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [html]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [clojure.string :as string]))

(defn config-error-snippet [{:keys [error config-string]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [node (om/get-node owner)
            highlight-target (goog.dom.getElementByClass "error-snippet" node)]
        ;; Prism is not yet available in Devcards.
        (when js/window.Prism
          (js/Prism.highlightElement highlight-target))))
    om/IRender
    (render [_]
      (let [lines (string/split-lines config-string)
            start-line (get-in error [:start :line])
            end-line (get-in error [:end :line])
            line-count (inc (- end-line start-line))
            snippet (->> lines
                         (drop start-line)
                         (take line-count)
                         (string/join "\n"))]
        (html
         [:pre.line-numbers {:data-start (inc start-line)}
          [:code.error-snippet.language-yaml
           snippet]])))))

(defn config-error [{:keys [error] :as data} owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:li
        (string/join "." (:path error))
        " "
        (:message error)
        (om/build config-error-snippet data)]))))

(defn config-errors [build owner]
  (reify
    om/IRender
    (render [_]
      (let [config (:circle_yml build)
            config-string (:string config)
            errors (:errors config)]
        (html
         [:div.config-errors
          [:div.alert.alert-danger.expanded
           [:div.alert-header
            [:img.alert-icon {:src (common/icon-path "Info-Error")}]
            (str "CIRCLE.YML - " (count errors) " WARNINGS")]
           [:div.alert-body
            [:div.dang
             "Dang! We spotted something wrong with your "
             [:span.circle-yml-highlight
              "circle.yml"]
             ". "
             "These may be causing your builds to fail. "
             "We recommend that you fix them as soon as possible. "
             "You may want to look at "
             [:a {:href "https://circleci.com/docs/configuration/"} "our docs"]
             " or "
             (common/contact-us-inner owner)
             " us if you’re having trouble."]
            [:ol
             (for [error errors]
               (om/build config-error {:error error
                                       :config-string config-string}))]]]])))))
