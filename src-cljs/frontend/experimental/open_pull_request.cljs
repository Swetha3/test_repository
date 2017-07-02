(ns frontend.experimental.open-pull-request
  (:require [frontend.components.pieces.button :as button]
            [frontend.components.pieces.icon :as icon]
            [frontend.models.build :as build-model]
            [frontend.utils :refer-macros [html]]
            [om.core :as om :include-macros true]))

(defn- open-pull-request-action [{:keys [build]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      ((om/get-shared owner :track-event) {:event-type :open-pull-request-impression
                                           :properties {:branch (:branch build)
                                                        :build-outcome (:outcome build)}}))

    om/IRender
    (render [_]
      (html
        (button/icon-link {:href (build-model/new-pull-request-url build)
                           :label "Open a Pull Request"
                           :target "_blank"
                           :on-click #((om/get-shared owner :track-event) {:event-type :open-pull-request-clicked
                                                                           :properties {:branch (:branch build)
                                                                                        :build-outcome (:outcome build)}})
                           :bordered? true}
                          (icon/git-pull-request))))))
