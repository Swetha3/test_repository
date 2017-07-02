(ns frontend.components.forms
  (:require [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.utils :as utils :include-macros true]
            [frontend.disposable :as disposable :refer [dispose]]
            [om.core :as om :include-macros true])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [html]]))


;; Example usage for managed-button:

;; component html:
;; [:form (forms/managed-button [:input {:type "submit" :on-click #(raise! owner [:my-control])}])]

;; controls handler:
;; (defmethod post-control-event! :my-control
;;   [target message args previous-state current-state]
;;   (let [status (do-something)
;;         uuid frontend.async/*uuid*]
;;     (forms/release-button! uuid status)))

(def event-owners (atom {}))

(defn set-status!
  "Updates the status of a managed button. Ignores updates if a new event is happening.
  Updates to finished states (:success or :failure) are cleared back to :idle after a timeout."
  [owner event-id status]
  (when (.isMounted owner)
    (om/update-state! owner (fn [state]
                              (if (= (:event-id state) event-id)
                                (assoc state :status status)
                                state)))
    (when (#{:success :failed} status)
      (js/setTimeout #(set-status! owner event-id :idle) 1000))
    (when (= :idle status)
      (swap! event-owners dissoc event-id))))

(defn release-button!
  "Used by the controls controller to set the button state. status should be a valid button state,
  :success, :failed, or :idle"
  [event-id status]
  (when-let [owner (get @event-owners event-id)]
    (set-status! owner event-id status)))

(defn wrap-managed-button-handler
  "Wraps the on-click handler with a uuid binding that enables release-button! to do it's thing. "
  [handler owner]
  (fn [& args]
    (let [event-id (utils/uuid)]
      (swap! event-owners assoc event-id owner)
      (om/set-state! owner {:event-id event-id :status :loading})
      (binding [frontend.async/*uuid* event-id]
        (apply handler args)))))

(defn managed-button*
  "Takes an ordinary input or button hiccup form.
   Automatically disables the button until the controls handler calls release-button!"
  [hiccup-form owner]
  (reify

    om/IDisplayName
    (display-name [_] "Managed button")

    om/IInitState
    (init-state [_]
      {:status :idle})

    om/IWillMount
    (will-mount [_]
      (om/set-state! owner :status :idle))

    om/IRenderState
    (render-state [_ {:keys [status]}]
      (let [[tag attrs & body] hiccup-form
            data-field (keyword (str "data-" (name status) "-text"))
            new-value (-> (merge {:data-loading-text "..."
                                  :data-success-text "Saved"
                                  :data-failed-text "Failed"}
                                 attrs)
                          (get data-field (:value attrs)))
            new-body (if (:data-spinner attrs)
                       (if (= :loading status) (spinner) body)
                       (if (= :idle status) body new-value))
            new-attrs (-> attrs
                          ;; Don't unset 'disabled' if it's already set but not loading.
                          (update-in [:disabled] #(or % (= :loading status)))
                          (update-in [:class] (fn [c] (cond (not= :loading status) c
                                                            (string? c) (str c " disabled")
                                                            (coll? c) (conj c "disabled")
                                                            :else "disabled")))
                          (update-in [:on-click] wrap-managed-button-handler owner)
                          (update-in [:value] #(or new-value %)))]
        (html
         (vec (concat [tag new-attrs]
                      [new-body])))))

    ))

(defn managed-button
  "Takes an ordinary input or button hiccup form.
   Disables the button while the controls handler waits for any API responses to come back.
   When the button is clicked, it replaces the button value with data-loading-text,
   when the response comes back, and the control handler calls release-button! it replaces the
   button with the data-:status-text for a second."
  [hiccup-form]
  (om/build managed-button* hiccup-form))
