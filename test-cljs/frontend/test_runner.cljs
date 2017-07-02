(ns frontend.test-runner
  (:require [cljs.test :refer-macros [use-fixtures]]
            [clojure.string]
            [doo.runner :refer-macros [doo-all-tests]]
            [frontend.analytics.test-core]
            [frontend.analytics.test-segment]
            [frontend.analytics.test-track]
            [frontend.components.pages.build.test-head]
            [frontend.components.project.test-common]
            [frontend.components.test-build]
            [frontend.components.test-insights]
            [frontend.components.test-org-settings]
            [frontend.components.test-statuspage]
            [frontend.controllers.test-api]
            [frontend.controllers.test-controls]
            [frontend.controllers.test-ws]
            [frontend.devcards.test-morphs]
            [frontend.models.test-action]
            [frontend.models.test-build]
            [frontend.models.test-feature]
            [frontend.models.test-plan]
            [frontend.models.test-project]
            [frontend.models.test-user]
            [frontend.models.test-test]
            [frontend.parser.test-connection]
            [frontend.send.test-resolve]
            [frontend.test-analytics]
            [frontend.test-datetime]
            [frontend.test-elevio]
            [frontend.test-parser]
            [frontend.test-pusher]
            [frontend.test-routes]
            [frontend.utils-test]
            [frontend.utils.test-build]
            [frontend.utils.test-expr-ast]
            [frontend.utils.test-function-query]
            [frontend.utils.test-seq]))

(aset js/window "renderContext" "{}")
(aset js/window "SVGInjector" (fn [node] node))

(defmethod cljs.test/report [:jx.reporter.karma/karma :begin-test-ns] [m]
  ;; Noop. Avoid filling up the output with "LOG: 'Testing every.namespace'".
  nil)

(doo-all-tests)
