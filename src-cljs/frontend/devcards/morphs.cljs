(ns frontend.devcards.morphs
  (:require [cljs.core.async :as async :refer [<! chan close!]]
            [clojure.spec :as s :include-macros true]
            [clojure.test.check.generators :as gen]
            [goog.object :as gobject]
            [medley.core :refer [distinct-by]]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def ignored-props #{"title" "href" "onClick"})
(defn signature [element]
  (let [props (gobject/get element "props")
        children (->> (gobject/get props "children")
                      ;; React.Children.toArray is missing from the cljsjs
                      ;; externs, and so fails under advanced compliation. When
                      ;; this PR is accepted, we can use
                      ;; js/React.Children.toArray directly.
                      ;; https://github.com/cljsjs/packages/pull/1230
                      ((gobject/get js/React.Children "toArray"))
                      (filter js/React.isValidElement)
                      (filter #(string? (gobject/get % "type"))))]
    (into {:type (gobject/get element "type")
           :children (map signature children)}
          (map (juxt identity (partial gobject/get props))
               (remove (conj ignored-props "children")
                       (gobject/getKeys props))))))

(defn- shallow-render
  "Shallow-renders the component factory. If the factory actually returns a
  React DOM element rather than a custom component element, returns that."
  [factory props & children]
  (let [elt (apply factory props children)]
    (if (string? (gobject/get elt "type"))
      elt
      (let [renderer (js/React.addons.TestUtils.createRenderer)]
        (.render renderer elt)
        (.getRenderOutput renderer)))))

(defn friendly-pipe
  "Like core.async/pipe, but gives the browser an opportunity to run higher
  priority tasks between each element."
  ([from to] (friendly-pipe from to true))
  ([from to close?]
   (go-loop []
     (let [v (<! from)]
       (if (nil? v)
         (when close? (close! to))
         (when (>! to v)
           ;; This gives the browser a chance to break away from the pipe
           ;; process and return later.
           (<! (async/timeout 0))
           (recur)))))
   to))


(defn generate
  "Generates signature, morph pairs for component-factory-var (using optional
  generator overrides) and puts them onto ch."
  ([ch component-factory-var] (generate ch component-factory-var {}))
  ([ch component-factory-var overrides]
   (let [sample-size 100
         spec (:args (s/get-spec component-factory-var))
         samples (take sample-size (gen/sample-seq (s/gen spec overrides)))
         signature-of-sample (comp signature (partial apply shallow-render (deref component-factory-var)))
         samples-ch (chan)
         morphs-ch (chan 1 (comp
                            (map (juxt signature-of-sample identity))
                            (distinct-by first)))]
     (async/onto-chan samples-ch samples)
     (friendly-pipe samples-ch morphs-ch)
     (async/pipe morphs-ch ch))))


(defn- update-morphs [this props]
  ;; This code does some juggling to handle Figwheel reloading well. In
  ;; particular, it has two goals:
  ;;
  ;; * When the code reloads, we should still see the old morphs while new ones
  ;;   are generated, as it would be annoying for them to disappear.
  ;;
  ;; * When the new version of the component no longer generates a particular
  ;;   morph, *that* morph should disappear.
  ;;
  ;; Outdated morphs can't be thrown away until the latest generator is
  ;; finished: this is when we can say for a fact that the latest code doesn't
  ;; generate a particular morph. To do this, we keep the current generator's
  ;; morphs separate from any previous morphs in the component state. Once we
  ;; successfully complete generating the latest version's morphs (and there's
  ;; been no new version of the component in the meantime), we can throw away
  ;; the previous morphs, knowing that the current morphs are exactly the morphs
  ;; we want to see.
  (let [morph-ch (:morphs props)]
    (let [previous-channel (:current-channel (om-next/get-state this))]
      ;; On update, any current channel becomes the previous channel.
      (om-next/update-state! this (fn [state]
                                    (-> state
                                        ;; Move any morphs from :current-morphs to :previous-morphs
                                        (update :previous-morphs merge (:current-morphs state))
                                        ;; Clear the :current-morphs and note the new :current-morphs
                                        (assoc :current-morphs {}
                                               :current-channel morph-ch))))

      ;; Now close the previous-channel, if any. This will halt the morph
      ;; generator behind that channel, so we don't waste effort.
      (when previous-channel (close! previous-channel)))

    (go-loop []
      ;; Read morphs from the new channel.
      (if-let [morph (<! morph-ch)]
        (do
          ;; Add each morph from the :current-channel to the :current-morphs.
          (om-next/update-state! this
                                 (fn [state]
                                   (if (= morph-ch (:current-channel state))
                                     (update state :current-morphs conj morph)
                                     state)))
          (recur))
        ;; When the channel closes, if it's still the current channel, clear
        ;; the :previous-morphs. This will happen only when the generator has
        ;; finished generating morphs and the component has not reloaded and
        ;; started a new morph generation process in the meantime. This means
        ;; that :current-morphs is not a complete set of morphs, and any morphs
        ;; in :previous-morphs but not :current-morphs are out of date and
        ;; should disappear.
        (om-next/update-state! this (fn [state]
                                      (if (= morph-ch (:current-channel state))
                                        (dissoc state :previous-morphs)
                                        state)))))))

(defui ^:once ^:private MorphDisplay
  Object
  (componentDidMount [this]
    (update-morphs this (om-next/props this)))
  (componentWillReceiveProps [this next-props]
    (update-morphs this next-props))
  (render [this]
    (let [{:keys [render-morphs]} (om-next/props this)
          {:keys [previous-morphs current-morphs]} (om-next/get-state this)]
      (->> (merge previous-morphs current-morphs)
           (sort-by (comp hash key))
           vals
           render-morphs))))

(def ^:private morph-display (om-next/factory MorphDisplay))

(defn render
  "Renders morphs of component-factory-var (generated using optional generator
  overrides). render-fn will be called with a collection of morphs and should
  return a React element to render."
  ([component-factory-var render-fn]
   (render component-factory-var {} render-fn))
  ([component-factory-var overrides render-fn]
   (morph-display {:morphs (generate (chan) component-factory-var overrides)
                   :render-morphs render-fn})))
