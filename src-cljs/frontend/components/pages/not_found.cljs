(ns frontend.components.pages.not-found
  (:require [frontend.utils.legacy :refer [build-legacy]]
            [om.next :as om-next :refer-macros [defui]]
            [frontend.components.errors :as errors]
            [frontend.components.templates.main :as main-template]))

(defui Page
  static om-next/IQuery
  (query [this]
    ['{:legacy/state [*]}])
  Object
  (render [this]
    (let [legacy-state (-> (:legacy/state (om-next/props this))
                           (assoc :navigation-point :error)
                           (assoc-in [:navigation-data :status] 404))]
      (main-template/template
       {:app legacy-state
        :main-content (build-legacy errors/error-page legacy-state)}))))