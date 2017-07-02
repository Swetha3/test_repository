(ns frontend.models.container
  (:require [clojure.set :refer (intersection)]
            goog.string.format))

(defn id [container]
  (:index container))

(defn status [container build-running?]
  ; `insignificant` is the step which failure can be ignored because it doesn't affect build itlself
  (let [actions (->> container :actions (remove :filler-action) (remove :insignificant))
        action-statuses (->> actions (map :status) (remove nil?) set)]
    (cond
     ;; If there are no action statuses, or the last one is running, it's 'running'.
     (or (empty? action-statuses)
         (= "running" (last action-statuses)))
     :running
     ;; If it has any of the failure-like statuses, it's 'failed'.
     ;; `signaled` means the process terminated abruptly, potentially because of memory usage,
     ;; or another process in background killed it.
     (some action-statuses ["failed" "timedout" "canceled" "infrastructure_fail" "signaled"])
     :failed
     ;; If any of the actions have been canceled, it's 'canceled'.
     (some :canceled actions)
     :canceled
     ;; If there's only one status, and it's "success", it's 'success'.
     (and (= action-statuses #{"success"}))
     (if build-running? :waiting :success))))

(defn status->classes [status]
  (some-> status name (cons nil)))
