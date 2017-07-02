(ns frontend.utils.build
  (:require [frontend.models.build :as build-model]))

(defn default-tab
  "The default tab to show in the build page head, based on the stage of the build."
  [build scopes]
  (cond
    ;; show circle.yml errors first
    (build-model/config-errors? build) :config
    ;; default to ssh-info for SSH builds
    (build-model/ssh-enabled-now? build) :ssh-info
    ;; default to the queue tab if the build is currently usage queued, and
    ;; the user is has the right permissions (and is logged in).
    (and (:read-settings scopes)
         (build-model/in-usage-queue? build))
    :queue-placeholder
    ;; If there's no SSH info, build isn't finished, show the config or commits.
    ;; "config" takes up too much room for paid customers.
    (build-model/running? build) (if (:read-settings scopes)
                                   :queue-placeholder
                                   :config)
    ;; Otherwise, just use the first one.
    :else :tests))
