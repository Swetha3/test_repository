(ns frontend.util.docs
  (:require [fs]
            [clj-yaml.core :as yaml]))

(def doc-root "resources/assets/docs")

(defn drop-extension
  "Given a path, drop the extension (.clj) off the filename"
  [^String path]
  (-> path
      (.split "\\.")
      (into [])
      (butlast)
      (#(apply fs/join %))))

(defn extract-comment-block [doc-content]
  (second (re-find #"(?s)<!--(.*?)-->" doc-content)))

(defn file->partial-map [doc-file]
  (let [name-key (-> doc-file
                     fs/split
                     last
                     drop-extension
                     keyword)
        metadata (-> doc-file
                     slurp
                     extract-comment-block
                     yaml/parse-string)]
    {name-key metadata}))

(defn read-doc-manifest
  ([]
     (read-doc-manifest doc-root))
  ([root]
     (let [partials (->> (fs/listdir root)
                         (filter #(.endsWith % ".md"))
                         (map (partial fs/join doc-root))
                         (map file->partial-map))]
       (reduce merge partials))))
