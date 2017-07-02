(ns frontend.stefon-with-sourcemaps
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [fs]
            [stefon.asset :as asset]
            [stefon.asset.stefon :as original-stefon]
            [stefon.digest :as digest]
            [stefon.path :as path]))

(defn compile-stefon-with-sourcemaps
  "Like stefon.asset.stefon, this compiles and concatenates the files referred to by a
  .stefon file. Additionally, it triggers compilation of sourcemaps referred to by those
  compiled files."
  [root adrf content]
  (let [current-offset-row (atom 0)
        current-sourcemap-sections (atom [])
        compiled-stefon (StringBuilder.)]
    (doseq [sf (original-stefon/stefon-files root adrf content)]
      (println "compile-stefon-with-sourcemaps: " sf)
      (let [compiled-content (->> sf
                                  (asset/compile root)
                                  second
                                  digest/->str)
            compiled-content-lines (str/split-lines compiled-content)]

        (when (seq compiled-content-lines)
          ;; append everything but the last line...
          (.append compiled-stefon (->> compiled-content-lines (butlast) (str/join "\n")))
          (.append compiled-stefon "\n")

          (let [[_ source-map] (re-matches #"//#\s*sourceMappingURL=(.*)"
                                           (last compiled-content-lines))]
            (if (and source-map
                     ;; WORKAROUND: When a JS file with a source map is included
                     ;; in a .stefon manifest which is included in a .stefon
                     ;; manifest, the inner manifest's source map is a compiled
                     ;; asset (represented as an asset-uri), not a
                     ;; to-be-compiled asset. Our source map compilation
                     ;; therefore doesn't compose well. In that situation,
                     ;; abandon the source map.
                     (not (path/asset-uri? source-map)))
              (do
                ;; if the last line is a sourcemapping, don't append it, but do compile it and
                ;; build up an internal sourcemap...
                (let [asset-compile-result (asset/compile
                                            (fs/join root (fs/dirname sf))
                                            source-map)
                      compiled-source-map (->> asset-compile-result second (String.))]
                  (try
                    (swap! current-sourcemap-sections conj {:offset {:line @current-offset-row
                                                                     :column 0}
                                                            :map (json/read-str compiled-source-map)})
                    (catch java.lang.ClassCastException e
                      (println "(asset/compile " (fs/join root (fs/dirname sf)) " " source-map ") gave " asset-compile-result)
                      (throw e)))))
              (do
                ;; if the last line isn't a sourcemapping, append it
                (.append compiled-stefon (last compiled-content-lines))
                (.append compiled-stefon "\n")
                (swap! current-offset-row inc))))
          (swap! current-offset-row + (dec (count compiled-content-lines))))))
    (when (seq @current-sourcemap-sections)
      ;; this implementation works around rollbar's lack of :sections support, but only if
      ;; there is exactly one embedded sourcemap -- hence the assert.
      (assert (= 1 (count @current-sourcemap-sections)))
      (let [section-sourcemap (first @current-sourcemap-sections)
            sourcemap (-> (:map section-sourcemap)
                          ;; prepend a ';' for each offset line to the mappings.
                          ;; ';' is the mapping line-separator, so this means 'this line
                          ;; has no mapping' for each offset line...
                          (update-in ["mappings"] (partial str (apply str (repeat (-> section-sourcemap :offset :line) ";")))))
            sourcemap-content (json/write-str sourcemap)
            sourcemap-name (str adrf ".sourcemap")
            sourcemap-digested (-> sourcemap-name
                                   (path/adrf->uri)
                                   (path/path->digested sourcemap-content))]
        (asset/write-asset sourcemap-content sourcemap-digested)
        (.append compiled-stefon (str "//# sourceMappingURL=" sourcemap-digested "\n"))))
    (.toString compiled-stefon)))

(defn compile-sourcemap
  "Stefon compiler for sourcemap files: this triggers assetification of all of the files
  referred to by the sourcemap's top-level 'sources' list, and transforms the sourcemap
  to refer to those assetified sources."
  [root source-map content]
  (-> (json/read-str (String. content))
      (update-in ["sources"] (partial map (comp first (partial asset/compile-and-save root))))
      (json/write-str)))

(defn register []
  (asset/register "map" compile-sourcemap)
  (asset/register "stefon" compile-stefon-with-sourcemaps))
