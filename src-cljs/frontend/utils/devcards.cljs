(ns frontend.utils.devcards
  (:require [devcards.core :as dc]
            [devcards.util.utils :as dc-utils]
            [frontend.utils :refer-macros [html]]
            [goog.events :as gevents]
            [om.dom :as dom]
            [om.next :as om-next :refer-macros [defui]]))

(defui IFrame
  "Renders its children in an iframe. Perfect for demonstrating a component on a
  devcard at various viewport widths.

  :width  - (default: \"100%\") The CSS width of the iframe.
  :height - The CSS height of the iframe."

  Object
  (componentDidMount [this]
    (let [outer-doc (.-ownerDocument (dom/node this))
          inner-doc (.-contentDocument (dom/node this))
          ;; This <div> will be what we ReactDOM.render into. (React doesn't
          ;; like to render directly into a <body>.)
          react-container (.createElement inner-doc "div")]

      (.syncStyleSheets this)

      ;; Watch for figwheel reloading the CSS.
      (set! (.-figwheelHandlerKey this)
            (gevents/listen (.-body outer-doc)
                            "figwheel.css-reload"
                            #(.syncStyleSheets this)))

      ;; Append the container into the body.
      (.appendChild (.-body inner-doc) react-container)
      (set! (.-container this) react-container))
    (.renderToContainer this))

  (componentWillUnmount [this]
    (gevents/unlistenByKey (.-figwheelHandlerKey this)))

  (componentDidUpdate [this _ _]
    (.renderToContainer this))

  (syncStyleSheets [this]
    (let [outer-doc (.-ownerDocument (dom/node this))
          inner-doc (.-contentDocument (dom/node this))

          old-stylesheet-nodes
          (doall (map #(.-ownerNode %) (array-seq (.-styleSheets inner-doc))))]

      ;; Copy in outer doc's stylesheets.
      (doseq [sheet (array-seq (.-styleSheets outer-doc))]
        (.appendChild (.-head inner-doc)
                      (->> sheet .-ownerNode (.importNode inner-doc))))

      ;; Then remove old stylesheets.
      (doseq [sheet-node old-stylesheet-nodes]
        (.remove sheet-node))))

  (renderToContainer [this]
    (js/ReactDOM.render (html [:div (om-next/children this)])
                        (.-container this)))

  (render [this]
    (html [:iframe {:frame-border 0
                    :style {:outline "1px solid #CCCCCC"
                            :width (:width (om-next/props this) "100%")
                            :height (:height (om-next/props this))
                            ;; Center the iframe, even if it's wider than its parent.
                            :margin-left "50%"
                            :transform "translateX(-50%)"}}])))

(def iframe (om-next/factory IFrame))

(defn display-data [data]
  (dc/code-highlight (dc-utils/pprint-str data) "clojure"))
