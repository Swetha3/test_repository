(ns frontend.components.pieces.modal
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.pieces.button :as button]
            [frontend.utils :as utils :refer-macros [component element html]]
            [om.next :as om :refer-macros [defui]]))

(defn dialog
  "A dialog box suitable for presenting in a modal.

  :title    - Text that appears in the header of the dialog.
  :body     - The body of the dialog.
  :actions  - A sequence of action elements, typically buttons, which will appear
              along the bottom of the dialog.
  :close-fn - A function to call when the close button is clicked."
  [{:keys [title body actions close-fn]}]
  (component
    (html
     [:div
      [:.header
       [:.modal-component-title title]
       [:i.material-icons.close-button {:on-click close-fn} "clear"]]
      [:.body body]
      (when (seq actions)
        [:.actions
         (for [action actions]
           [:.action action])])])))

(defui
  ^{:doc
    "Opens a portal div as a child of the document's body and renders its
    children inside that div, wrapped in a CSSTransitionGroup. When this
    component is unmounted, the portal element and the children will remain in
    the DOM as the Leave transition plays, then be removed.

    Props:
    :transition-name    - The CSSTransitionGroup's transitionName.
    :transition-timeout - The timeout for (all) the CSSTransitionGroup's
                          transitions (in ms). Set this to a duration at least
                          as long as the longest of the Appear, Enter, and Leave
                          transitions."}

  TransitionedPortal

  Object
  (componentDidMount [this]
    (let [destination (.-body js/document)
          container (.createElement (.-ownerDocument destination) "div")]
      (.appendChild destination container)
      (set! (.-container this) container))
    (.renderToContainer this (om/children this)))

  (componentWillUnmount [this]
    (.renderToContainer this nil)
    (js/setTimeout #(.remove (.-container this))
                   (:transition-timeout (om/props this))))

  (componentDidUpdate [this _ _]
    (.renderToContainer this (om/children this)))

  (renderToContainer [this element]
    (let [{:keys [transition-name transition-timeout]} (om/props this)]
      (js/ReactDOM.render
       (js/React.createElement
        js/React.addons.CSSTransitionGroup
        #js {:transitionName transition-name
             :transitionAppear true
             :transitionAppearTimeout transition-timeout
             :transitionEnterTimeout transition-timeout
             :transitionLeaveTimeout transition-timeout}
        element)
       (.-container this))))

  (render [this] nil))

(def transitioned-portal (om/factory TransitionedPortal))


(defn modal
  "A modal presentation. The given content will be displayed centered over a
  darkened background. The modal will animate in when this component is mounted
  and out when it's unmounted.

  :close-fn - A function to call when the background is clicked. Should cause
              the modal's parent to stop rendering it, causing it to leave the
              screen."
  [{:keys [close-fn]} content]
  (component
    (transitioned-portal
     {:transition-name "modal"
      :transition-timeout 500}
     (element :modal
       (html
         [:div {:on-click #(do (utils/disable-natural-form-submission %)
                               (when (= (.-target %) (.-currentTarget %))
                                 (close-fn)))}
         [:.box
          content]])))))


(defn modal-dialog
  "A dialog displayed in a modal presentation. Takes the props that both
  components take, in a single map; :close-fn is the same for both."
  [opts]
  (modal (select-keys opts [:close-fn])
         (dialog (select-keys opts [:title :body :actions :close-fn]))))


(dc/do
  (defcard dialog
    (dialog {:title "Are you sure?"
             :body "Are you sure you want to remove the \"Foo\" Apple Code Signing Key?"
             :actions [(button/button {:kind :flat} "Cancel")
                       (button/button {:kind :primary} "Delete")]
             :close-fn identity})
    {}
    {:classname "background-gray"})

  (defcard modal
    (fn [state]
      (html
       [:div {:style {:display "flex"
                      :justify-content "center"
                      :align-items "center"
                      :height "100%"}}
        [:div
         (button/button {:on-click #(swap! state assoc :shown? true)
                         :kind :primary}
                        "Show Modal")
         (when (:shown? @state)
           (modal {:close-fn #(swap! state assoc :shown? false)}
                  (html
                   [:div {:style {:height "200px"
                                  :display "flex"
                                  :justify-content "center"
                                  :align-items "center"}}
                    "Modal Content"])))]]))
    {:shown? false})

  (defcard modal-dialog
    (fn [state]
      (html
       [:div {:style {:display "flex"
                      :justify-content "center"
                      :align-items "center"
                      :height "100%"}}
        [:div
         (button/button {:on-click #(swap! state assoc :shown? true)
                         :kind :primary}
                        "Show Modal")
         (when (:shown? @state)
           (modal-dialog {:title "Are you sure?"
                          :body "Are you sure you want to remove the \"Foo\" Apple Code Signing Key?"
                          :actions [(button/button {:kind :flat} "Cancel")
                                    (button/button {:kind :primary} "Delete")]
                          :close-fn #(swap! state assoc :shown? false)}))]]))
    {:shown? false}))
