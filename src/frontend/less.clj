(ns frontend.less
  (:require [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [fs])
  (:import [java.nio.file
            Path Paths FileSystems WatchEvent$Kind StandardWatchEventKinds
            Files WatchService FileVisitor FileVisitResult LinkOption]
           [java.nio.file.attribute BasicFileAttributes FileAttribute]
           [java.io Reader File BufferedReader InputStreamReader]
           [java.nio.charset StandardCharsets]
           [java.net URL URI]
           [com.sun.nio.file SensitivityWatchEventModifier]))

;; Compiles less file on every save. Most of this was extracted from
;; https://github.com/montoux/lein-less

(def less-dir "resources/assets/css")
(def less-file "resources/assets/css/app.css.less")
(def less-map "resources/app.css.map")
(def less-map-url "/app.css.map")
(def output-file "resources/assets/css/app.css")

(def lessc-path "node_modules/.bin/lessc")

(def watch-opts-cdm
  (into-array WatchEvent$Kind [StandardWatchEventKinds/ENTRY_CREATE
                               StandardWatchEventKinds/ENTRY_DELETE
                               StandardWatchEventKinds/ENTRY_MODIFY]))

(defn compile! [& {:keys [src dest minify]
                   :or {src less-file, dest output-file, minify false}}]
  (let [standard-flags (format "--source-map-basepath=$PWD/resources --source-map=%s --source-map-url=%s" less-map less-map-url)
        flags (if minify
                (str standard-flags " --compress")
                standard-flags)
        cmd (format "%s %s %s > %s" lessc-path flags src dest)
        res (shell/sh "bash" "-c" cmd)]
    (if (not= 0 (:exit res))
      (throw (Exception. (format "Couldn't compile less with %s returned exit code %s: %s %s" cmd (:exit res) (:out res) (:err res))))
      (:out res))))

(defn ->path [path]
  (.getPath (FileSystems/getDefault) path (into-array String [])))

(defn all-dirs
  "Returns a list of all directories in a directory"
  [dir & {:keys [recursive? max-depth matcher-fn]
          :or {recursive? true max-depth Integer/MAX_VALUE matcher-fn (constantly true)}}]
  (when (and (fs/exists? dir)
             (fs/directory? dir))
    (let [result (transient [])
          visitor (reify FileVisitor
                    (preVisitDirectory [this dir attrs]
                      (conj! result (str dir))
                      FileVisitResult/CONTINUE)
                    (visitFileFailed [this file exc]
                      FileVisitResult/CONTINUE)
                    (postVisitDirectory [this file exc]
                      FileVisitResult/CONTINUE)
                    (visitFile [this file attrs]
                      FileVisitResult/CONTINUE))]
      (Files/walkFileTree (->path dir)
                          (java.util.HashSet.)
                          (if recursive? max-depth 1)
                          visitor)
      (persistent! result))))

(defn register-watcher [dir watcher watch-opts]
  (doseq [d (all-dirs dir)]
    (.register ^Path (->path d) watcher watch-opts (into-array [SensitivityWatchEventModifier/HIGH]))))

(defn watch-files [dir callback-fn]
  (let [^WatchService watcher (.newWatchService (FileSystems/getDefault))]
    (register-watcher dir watcher watch-opts-cdm)
    (future
      (try
        (loop []
          (let [key (.take watcher)
                unix-files (map #(.context %) (.pollEvents key))]
            (callback-fn unix-files)
            ;; Figuring out if a new directory has been added is annoying, so
            ;; we'll just walk the tree and re-register watchers again for now
            (register-watcher dir watcher watch-opts-cdm)
            (.pollEvents key)
            (.reset key)
            (recur)))
        (catch Exception e
          (log/infof "File watcher on %s running %s canceled" dir callback-fn))
        (finally
          (.close watcher))))
    watcher))

(defn compile-less-callback! [files]
  (when (some #(re-find #"less$" (str %)) files)
    (log/infof "Found changes in %s, compiling app.css.less..." (pr-str (map str files)))
    (try
      (let [start (. System (nanoTime))]
        (compile!)
        (log/infof "Finished compiling app.css.less in %.0fms" (/ (double (- (. System (nanoTime)) start)) 1000000.0)))
      (catch Exception e
        (log/errorf e "Error compiling app.css.less")))))

(def watcher-atom (atom nil))

(defn stop-watcher! []
  (when @watcher-atom
    (.close @watcher-atom)))

(defn start-watcher! []
  (reset! watcher-atom (watch-files less-dir #'compile-less-callback!)))

(defn restart-watcher! []
  (stop-watcher!)
  (compile!)
  (start-watcher!))

(defn init []
  (compile!)
  (start-watcher!))
