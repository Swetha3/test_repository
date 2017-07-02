(ns frontend.tasks.http
  (:require [frontend.stefon]
            [frontend.stefon-with-sourcemaps :as stefon-with-sourcemaps]
            [frontend.less :as less]
            [frontend.util.docs :as doc-utils]
            [fs]
            [stefon.core :as stefon]
            [stefon.manifest]
            [cheshire.core :as json]))

(def stefon-options {:asset-roots frontend.stefon/asset-roots
                     :precompiles frontend.stefon/precompiles
                     :serving-root "resources/public"
                     :manifest-file "resources/public/assets/stefon-manifest.json"
                     :mode :production})

(defn update-hosted-scripts [scripts]
  (doseq [script scripts]
    (println (format "Updating %s" script))
    (loop [tries 3]
      (when-not (try
                  (spit (str "resources/assets/" (:path script))
                        (slurp (:url script)))
                  true
                  (catch Exception e
                    (when (zero? tries)
                      (throw (ex-info "Couldn't update hosted script" script e)))))
        (recur (dec tries))))))

(defn precompile-assets []
  (update-hosted-scripts frontend.stefon/hosted-scripts)
  (less/compile! :minify true)
  (println (format "Stefon options: %s" stefon-options))
  (stefon-with-sourcemaps/register)
  (stefon/precompile stefon-options))
