(ns frontend.components.pieces.form
  (:require [devcards.core :as dc :refer-macros [defcard defcard-om]]
            [om.core :as om :include-macros true]
            [om.next :as om-next :refer-macros [defui]]
            [frontend.components.pieces.button :as button]
            [frontend.utils :refer-macros [component element html]]
            [goog.events :as gevents]))

(defn- field-wrapper [{:keys [label id validation-error field]} owner]
  (reify
    om/IRenderState
    (render-state [_ _]
      (component
        (html
         [:div {:class (when validation-error "invalid")}
          [:label {:for id} label]
          [:.field
           field
           (when validation-error
             [:.validation-error validation-error])]])))))

(defn text-field
  "A text field, with a label.

  :label            - The label for the field.
  :value            - The value of the field.
  :on-change        - An on-change handler for the field. Receives the change
                      event.
  :password?        - (optional) If true, renders as a password field.
  :size             - (optional) The size of the field. One of #{:full :medium}.
                      (default: :full)
  :validation-error - (optional) A validation error to display. If given, the
                      field will appear as invalid.
  :disabled?        - (optional) If true, the field is disabled.
                      (default: false)
  :auto-complete    - (optional) If given, the value of the field's
                      :auto-complete attribute.
  :auto-focus       - (optional) If given, the text field will be focused on render."

  [{:keys [label value on-change password? size validation-error disabled? auto-complete auto-focus] :or {size :full}}]
  (reify
    om/IInitState
    (init-state [_]
      {:id (random-uuid)})

    om/IRenderState
    (render-state [_ {:keys [id]}]
      (component
        (om/build field-wrapper
                  {:label label
                   :id id
                   :validation-error validation-error
                   :field
                   (element :input
                     (html
                      ;; This `.dumb` class opts us out of old global form styles. We can
                      ;; remove it once those styles are dead.
                      [:input.dumb
                       (cond-> {:id id
                                :class (remove nil? [(when validation-error "invalid")
                                                     (case size
                                                       :full nil
                                                       :medium "medium")])
                                :type (if password? "password" "text")
                                :value value
                                :disabled disabled?
                                :on-change on-change}
                         auto-complete (assoc :auto-complete auto-complete)
                         auto-focus (assoc :auto-focus auto-focus))]))})))))

(defn text-area
  "A text area, with a label.

  :label            - The label for the field.
  :value            - The value of the field.
  :on-change        - An on-change handler for the field. Receives the change
                      event.
  :validation-error - (optional) A validation error to display. If given, the
                      field will appear as invalid.
  :disabled?        - (optional) If true, the field is disabled.
                      (default: false)
  :auto-focus       - (optional) If given, the text area will be focused on render."
  [{:keys [label value on-change validation-error disabled? auto-focus size] :or {size :full}}]
  (reify
    om/IInitState
    (init-state [_]
      {:id (random-uuid)})

    om/IRenderState
    (render-state [_ {:keys [id]}]
      (component
        (om/build field-wrapper
                  {:label label
                   :id id
                   :validation-error validation-error
                   :field
                   (element :textarea
                     (html
                      ;; This `.dumb` class opts us out of old global form styles. We can
                      ;; remove it once those styles are dead.
                      [:textarea.dumb
                       (cond-> {:id id
                                :class (when validation-error "invalid")
                                :value value
                                :disabled disabled?
                                :on-change on-change}
                               auto-focus (assoc :auto-focus auto-focus))]))})))))

(defn file-selector
  "A file selector, which accepts dropped files and provides a button which
  opens a file picker.

  :label     - The label for the selector.
  :file-name - The name of the selected file. Displayed in the selector.
  :on-change - A function called when the value of the selector changes. The
               function should take a map:
               :file-name    - The name of the selected file.
               :file-content - The data read from the selected file."
  [{:keys [label file-name validation-error on-change]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:id (random-uuid)
       :dragged-over? false})

    om/IRenderState
    (render-state [_ {:keys [id dragged-over?]}]
      (component
        (let [file-selected-fn
              (fn [file]
                (doto (js/FileReader.)
                  (gevents/listen "load" #(on-change {:file-name (.-name file)
                                                      :file-content (.. % -target -result)}))
                  (.readAsBinaryString file)))]

          (om/build field-wrapper
                    {:label label
                     :id id
                     :validation-error validation-error
                     :field
                     (element :drop-zone
                       (html
                        [:div {:class (when dragged-over? "dragged-over")
                               :on-drag-enter #(do (om/set-state! owner :dragged-over? true))
                               :on-drag-over #(.preventDefault %)
                               :on-drag-leave #(om/set-state! owner :dragged-over? false)
                               :on-drop #(do
                                           (.stopPropagation %)
                                           (.preventDefault %)
                                           (om/set-state! owner :dragged-over? false)
                                           (file-selected-fn (-> % .-dataTransfer .-files (aget 0))))}
                         [:.file-name
                          (or file-name
                              (list "Drop your file here or click " [:b "Choose File"] " below to select them manually."))]
                         [:input {:id id
                                  :type "file"
                                  :ref "file-input"
                                  :on-change #(file-selected-fn (-> % .-target .-files (aget 0)))}]
                         (button/button {:on-click #(.click (om/get-node owner "file-input"))
                                         :size :small}
                                        (element :button-label
                                          (html
                                           [:span
                                            [:i.material-icons "file_upload"]
                                            "Choose File"])))]))}))))))


;; Presents a series of fields (the Form's children), laid out appropriately.
(defui Form
  Object
  (render [this]
    (component
      (html
       [:form
        ;; WORKAROUND: When a form contains exactly one field, pressing
        ;; Return/Enter in that field submits the form. This is an old and
        ;; quirky browser behavior designed to support things like search boxes
        ;; without submit buttons. For us, it just makes it hard to handle
        ;; things, since we take a more hands-on approach to submission. An
        ;; actual form submission simply reloads the page, discarding the user's
        ;; input. It's never something we want to happen.
        ;;
        ;; This hidden text field ensures that there's always an additional
        ;; field in the form, disabling this behavior. It should have no other
        ;; effect.
        [:input.prevent-enter-from-submitting {:type "text"}]
        (js/React.Children.map (om-next/children this)
                               #(html [:.field %]))]))))

(def form (om-next/factory Form))


(dc/do
  (defcard text-field
    (fn [state]
      (om/build text-field {:label "Hostname"
                            :value (:value @state)
                            :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value "circleci.com"})

  (defcard text-field-medium
    (fn [state]
      (om/build text-field {:label "Hostname"
                            :value (:value @state)
                            :on-change #(swap! state assoc :value (.. % -target -value))
                            :size :medium}))
    {:value "circleci.com"})

  (defcard text-field-disabled
    (fn [state]
      (om/build text-field {:label "Hostname"
                            :value (:value @state)
                            :disabled? true
                            :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value "circleci.com"})

  (defcard text-field-invalid
    (fn [state]
      (om/build text-field {:label "Hostname"
                            :value (:value @state)
                            :validation-error "\"foobar.example\" is not a valid hostname."
                            :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value "foobar.example"})

  (def lorem-ipsum
    "Lorem ipsum dolor sit amet, cum expetenda maluisset comprehensam et. Has no eirmod labores disputando, diam atqui ius at, has fugit pertinacia reprimique te. Habeo partem tamquam qui et, eam ei inermis accommodare, te enim fugit utamur mea. Ius rebum nominavi indoctum ne.

Assum consul gubergren usu eu. Ei aeterno ancillae mea, nam ne veri molestie consectetuer. Eum habeo omnes exerci ut, ea malorum feugait scripserit vix. Nobis pericula est id. Cu atqui feugait vix, solum tamquam malorum cu eos, duo quaeque disputando ne. Sit et maiestatis comprehensam, no mea etiam regione definitiones, vis ornatus omittam in. Simul expetendis inciderint no has.

Eros ridens at quo, vix an alienum democritum. Ea eos adhuc lorem dicam, omnis sanctus ad sit. Quas elitr adipisci sed id, nec ei aeque apeirian invenire. Eam et congue aliquip urbanitas, ius cu laudem omnesque suscipiantur, posse voluptatibus usu et.

Sea ei equidem torquatos, duo in quis porro voluptaria, quo eligendi recusabo intellegat ne. Nam an sint detraxit volutpat. Mei congue nominavi sadipscing ea, per amet accusata scribentur at, enim nonumy facilisis ex pro. Eam molestie salutandi democritum no, vulputate forensibus sed ei, vix aperiri impedit dissentiunt ad. Mutat postea ad cum.")

  (defcard text-area
    (fn [state]
      (om/build text-area {:label "Favorite Latin Passage"
                           :value (:value @state)
                           :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value lorem-ipsum})

  (defcard text-area-disabled
    (fn [state]
      (om/build text-area {:label "Favorite Latin Passage"
                           :value (:value @state)
                           :disabled? true
                           :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value lorem-ipsum})

  (defcard text-area-invalid
    (fn [state]
      (om/build text-area {:label "Favorite Latin Passage"
                           :value (:value @state)
                           :validation-error "That's not even real Latin."
                           :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value lorem-ipsum})

  (defcard-om file-selector
    (fn [{:keys [file-name] :as app-state} owner]
      (om/component
        (html
         [:div
          (om/build file-selector {:label "Favorite File"
                                   :file-name file-name
                                   :on-change #(om/update! app-state
                                                           {:file-name (:file-name %)
                                                            :file-content (:file-content %)})})
          [:div {:style {:margin "10px 0"}}
           (button/button {:on-click #(om/update! app-state
                                                  {:file-name nil
                                                   :file-content nil})}
                          "Reset")]])))
    {:file-name nil
     :file-content nil}
    {}
    {:inspect-data true})

  (defcard file-selector-invalid
    (om/build file-selector {:label "Favorite File"
                             :file-name "a-file.txt"
                             :validation-error "That file's not all that great."
                             :on-change (constantly nil)}))

  (defcard-om form
    (fn [{:keys [text-field-value text-field-2-value text-area-value file-name] :as app-state} owner]
      (om/component
        (form {}
              (om/build text-field {:label "Hostname"
                                    :value text-field-value
                                    :on-change #(om/update! app-state :text-field-value (.. % -target -value))})
              (om/build text-field {:label "Favorite Band"
                                    :value text-field-2-value
                                    :validation-error (when (seq text-field-2-value)
                                                        (str text-field-2-value " is so last year."))
                                    :on-change #(om/update! app-state :text-field-2-value (.. % -target -value))})
              (om/build text-area {:label "Favorite Latin Passage"
                                   :value text-area-value
                                   :on-change #(om/update! app-state :text-area-value (.. % -target -value))})
              (om/build file-selector {:label "Favorite File"
                                       :file-name file-name
                                       :on-change #(om/update! app-state :file-name (:file-name %))}))))
    {:text-area-value lorem-ipsum}))
