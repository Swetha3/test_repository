(ns frontend.timer
  (:require [om.core :as om :include-macros true]))

(defn update-components
  "Takes a set of components to refresh. Returns the set of components that are
  still mounted and were updated."
  [components]
  (reduce (fn [acc component]
            (if (.isMounted component) ;TODO: Om 0.8.0 has `om/mounted?
              (do (om/refresh! component)
                  acc)
              (disj acc component)))
          components
          components))

(defn initialize
  "Sets up an atom that will keep track of components that should refresh when
  the current time changes."
  []
  (let [components (atom #{})]
    (js/setInterval #(swap! components update-components) 1000)
    components))

(defn set-updating!
  "Registers or unregisters a component to be refreshed every time a global
  timer ticks.  A component must be un-registered when disposing a component
  via IWillUnmount."
  [owner enabled?]
  (swap! (om/get-shared owner [:timer-atom])
         (if enabled? conj disj)
         owner))
