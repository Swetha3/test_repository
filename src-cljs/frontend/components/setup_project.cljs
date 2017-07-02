(ns frontend.components.setup-project
  (:require [frontend.async :refer [raise!]]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.dropdown :as dropdown]
            [frontend.components.pieces.popover :as popover]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.models.repo :as repo-model]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [defrender html]]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]))

(defn- projects-dropdown [{:keys [projects selected-project]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:.projects-dropdown
         [:h2 "Repository"]
         (if (nil? projects)
           [:.spinner-placeholder (spinner)]
           (dropdown/dropdown
             {:label "Repo"
              :on-change #(raise! owner [:setup-project-select-project %])
              :default-text "Select a repository"
              :value (repo-model/id selected-project)
              :options (->> projects
                            vals
                            (sort-by #(vcs-url/project-name (:vcs_url %)))
                            (map (fn [repo]
                                   (let [repo-id (repo-model/id repo)]
                                     [repo-id (vcs-url/project-name (:vcs_url repo))]))))}))]))))

(defn- one-platform [{:keys [selected-project]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        (let [org-login (:username selected-project)
              repo-name (:name selected-project)
              vcs-type (:vcs_type selected-project)]
          [:div.one-platform
           [:p "Know what you are doing? " [:a {:href "#"} "Read Documentation"]]])))))

(defn- two-platform [{:keys [selected-project]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div "Language: " (:language selected-project)]))))

(def ^:private platform-osx-default {:os :osx :platform "1.0"})
(def ^:private platform-linux-default {:os :linux :platform "1.0"})
(defn- default-platform [project]
  (if (repo-model/likely-osx-repo? project)
    platform-osx-default
    platform-linux-default))

(defn- platform-selector [{:keys [selected-project]} owner]
  (reify
    om/IInitState
    (init-state [_]
      (default-platform selected-project))

    om/IWillReceiveProps
    (will-receive-props [_ new-props]
      (let [new-selected-project (:selected-project new-props)]
        (when (and (not= (repo-model/id selected-project)
                         (repo-model/id new-selected-project)))
          (om/set-state! owner (default-platform new-selected-project)))))

    om/IRenderState
    (render-state [_ {:keys [platform os]}]
      (let [maybe-osx-disabled-tooltip (if (= os :osx)
                                         #(popover/popover {:title nil
                                                            :body [:span "We're currently working on OS X in 2.0 platform."]
                                                            :placement :left
                                                            :trigger-mode :hover}
                                                           %)
                                         identity)]

        (html
          [:form.platform-selector
           [:div
            [:h1 "Operating System"]
            [:ul
             [:li.radio
              [:label.os-label
               [:input
                {:type "radio"
                 :checked (= os :linux)
                 :on-change #(om/set-state! owner platform-linux-default)}]
               [:i.fa.fa-linux.fa-lg]
               "Linux"]]
             [:li.radio
              [:label.os-label
               [:input
                {:type "radio"
                 :checked (= os :osx)
                 :on-change #(om/set-state! owner platform-osx-default)}]
               [:i.fa.fa-apple.fa-lg]
               "OS X"]]]]
           [:div.platform
            [:h1 "Platform"]
            [:ul
             [:div.platform-item
              [:li.radio
               [:label
                [:input
                 {:type "radio"
                  :checked (= platform "1.0")
                  :on-change #(om/set-state! owner :platform "1.0")}]
                "1.0"]]
              [:p "The classic version of our platform offers all the standard features available for CI/CD minus the configurability and speed made available on CircleCI 2.0."]]
             [:div.platform-item
              [:li.radio
               (maybe-osx-disabled-tooltip
                 [:label
                  [:input
                   {:type "radio"
                    :disabled (= os :osx)
                    :checked (= platform "2.0")
                    :on-change #(om/set-state! owner :platform "2.0")}]
                  "2.0 "
                  [:span.new-badge]])]
              [:p "CircleCI 2.0 offers teams more power, flexibility, and control with configurable Jobs that are broken into Steps. Compose these steps within a job at your discretion. Also supports most public Docker images and custom images with your own dependencies."]]]]
           (if (= platform "1.0")
             (om/build one-platform {:selected-project selected-project})
             (om/build two-platform {:selected-project selected-project}))])))))

; https://stackoverflow.com/a/30810322
(defn- copy-to-clipboard [config-string]
  [:div
   (button/button
     {:kind :primary
      :on-click (fn []
                  ; Select the hidden text area.
                  (-> js/document
                      (.querySelector ".hidden-config")
                      .select)
                  ; Copy to clipboard.
                  (.execCommand js/document "copy"))}
     "Copy to Clipboard")
   [:textarea.hidden-config
    {:value config-string}]])

(defrender setup-project [data owner]
  (let [projects (get-in data state/setup-project-projects-path)
        selected-project (get-in data state/setup-project-selected-project-path)]
    (html
      [:div#setup-project
       (card/collection
         [(card/basic
            (html
              [:div
               [:h1 "Setup Project"]
               [:p "CircleCI helps you ship better code, faster. Let's add some projects on CircleCI. To kick things off, you'll need to choose a project to build. We'll start a new build for you each time someone pushes a new commit."]
               (om/build projects-dropdown {:projects projects
                                            :selected-project selected-project})]))
          (when selected-project
            (card/basic
              (html
                [:div
                 (om/build platform-selector {:selected-project selected-project})])))])])))
