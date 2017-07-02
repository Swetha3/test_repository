(ns frontend.utils
  (:require [cljs.core :refer [this-as]]
            [sablono.core :as html]))

(defmacro inspect
  "prints the expression '<name> is <value>', and returns the value"
  [value]
  `(do
     (let [name# (quote ~value)
           result# ~value]
       (print (pr-str name#) "is" (pr-str result#))
       result#)))

(defmacro timing
  "Evaluates expr and prints the label and the time it took.
  Returns the value of expr."
  {:added "1.0"}
  [label expr]
  `(let [global-start# (or (aget js/window "__global_time")
                          (aset js/window "__global_time" (.getTime (js/Date.))))
         start# (.getTime (js/Date.))
         ret# ~expr
         global-time# (- (.getTime (js/Date.)) global-start#)]
     (aset js/window "__global_time" (.getTime (js/Date.)))
     (prn (str ~label " elapsed time: " (- (.getTime (js/Date.)) start#) " ms, " global-time# " ms since last"))
     ret#))

(defmacro swallow-errors
  "wraps errors in a try/catch statement, logging issues to the console
   and optionally rethrowing them if configured to do so."
  [& action]
  `(try
     (try ~@action
          (catch js/Error e#
            (merror e#)
            (when (:rethrow-errors? initial-query-map)
              (js/eval "debugger")
              (throw e#))))
     (catch :default e2#
       (merror e2#)
       (when (:rethrow-errors? initial-query-map)
         (js/eval "debugger")
         (throw e2#)))))

(defmacro defrender
  "defs a function which reifies an IRender component that only has a render
  function and splices the body into the render function"
  [name args & body]
  `(defn ~name ~args
     (reify
       om.core/IDisplayName
       (~'display-name [~'_] ~(str name))
       om.core/IRender
       (~'render [~'_] ~@body))))

(defmacro defrendermethod
  "defs a method which reifies an IRender component that only has a render
  function and splices the body into the render function"
  [multifn dispatch-val args & body]
  `(defmethod ~multifn ~dispatch-val ~args
     (reify
       om.core/IDisplayName
       (~'display-name [~'_] ~(str multifn " (" dispatch-val ")"))
       om.core/IRender
       (~'render [~'_] ~@body))))

(defmacro html [hiccup]
  `(let [hiccup# ~hiccup]
     (if-not hiccup#
       (html/html hiccup#)
       (let [safe-hiccup# (dissoc-nil-react-keys hiccup#)]
         (if-not (:render-colors? initial-query-map)
           (html/html safe-hiccup#)
           (try
             (html/html (add-white-border safe-hiccup#))
             (catch :default e#
               (html/html safe-hiccup#))))))))

(def ^:private component-name-symbol
  "A symbol which will be (lexically) bound to the current component name inside
  a component form."
  (gensym "component-name"))

(defmacro component
  "Assigns the current component's name as the data-component attribute of a
  React element. `body` is wrapped in an implicit `do`, and should evaluate to a
  React element (such as a call to `sablono.core/html`). In an Om Next
  component, the component name is the fully-qualified name of the component
  class; in an Om Previous component or a pure functional component, it's the
  fully-qualified name of the (outermost) function generating the component
  contents.

  `component` also sets the component name that `element` will use to build an
  element name.

  BUG/WORKAROUND: Functional components with multiple arities or a variable
  arity need to be treated slightly differently. `component` relies on the
  `:fn-scope` that the analyzer sets, and during the complier's processing of
  multiple and variable arity funcitons, the function bodies end up outside the
  actual function, and so the `:fn-scope` is no longer available to the macro.
  As a workaround, use an explicit `def` and `fn` rather than `defn`. When the
  compiler expands the `fn`, the function bodies remain inside the `def`, which
  makes the name still available. See examples below.

  Examples:

  ;; Functional stateless component
  (defn fancy-button [on-click title]
    (component
      (html [:button {:on-click on-click} title])))

  ;; Functional stateless component with varargs workaround
  (def card
    (fn [& children]
      (component (html [:div children]))))

  ;; Om Previous component
  (defn person [person-data owner]
    (reify
      om/IRender
      (render [_]
        (component
          (html
            [:div
             [:.name (:name person-data)]
             [:.hair-color (:hair-color person-data)]]))))

  ;; Om Next component
  (defui Post
    static om/IQuery
    (query [this]
      [:title :author :content])
    Object
    (render [this]
      (let [{:keys [title author content]} (om/props)]
        (component
          (html
           [:article
            [:h1 title]
            [:h2 \"by \" author]
            [:div.body content]])))))"
  [& body]
  (let [call-component* `(frontend.utils/component* ~component-name-symbol ~@body)]
    (if-let [fn-scope (first (:fn-scope &env))]
      ;; Om Previous or pure functional component
      (let [name (:name fn-scope)
            ns (-> fn-scope :info :ns)
            full-name (str ns "/" name)]
        `(let [~component-name-symbol ~full-name]
           ~call-component*))

      ;; Om Next class
      `(this-as this#
         (assert (goog.object/containsKey (.-constructor this#) "displayName")
                 (str
                  "Couldn't find a name for this component. Make sure the "
                  "component macro is at the top of the component's render "
                  "method."))
         (let [~component-name-symbol (.-displayName (.-constructor this#))]
           ~call-component*)))))

(defmacro element
  "Assigns an element name (a data-element attribute) to a React element.
  `body` is wrapped in an implicit `do`, and should evaluate to a React
  element (such as a call to `sablono.core/html`).`name` is an unqualified
  keyword. The full element name will take the form
  `component-namespace/component-name/element-name`

  The element macro is used to give a component-namespaced identifier to a DOM
  node which is passed to another component as a param. Without this, the
  element's component's stylesheet would have no safe way to select the correct
  DOM node.

  Example:

  (ns example.core)

  (defn card [title content]
    (component
      (html
       [:div
        [:.title title]
        [:.body content]])))

  (defn library-info-card [books]
    (component
      (card
       \"Library Info\"
       (element :card-content
         (html
          [:.stats \"The library contains \" (count books) \" books.\"]
          [:ul.books
           (for [book books]
             [:li
              [:.title (:title book)]
              [:.author (:author book)]])])))))

  Without using `element`, there would be no safe way to select and style the
  book's `.title`. Consider the problem with these attempts:

      [data-component='example.core/library-info-card'] .title

  This also matches the title of the card itself.

      [data-component='example.core/library-info-card']
        > div > .body > .books > li > .title

  This requires knowledge of the DOM structure of a `card`. If `card`'s
  definition changes, the selector will break.

  There's one other big reason we can't use either of those selectors: there's
  no DOM element with that `data-component` value. The topmost DOM node in the
  `library-info-card` component isn't rendered directly by `library-info-card`,
  it's rendered by `card`. That node's `data-component` is `example.core/card`.
  This doesn't apply in all cases, but it applies to any component which renders
  another component at the top of its tree.

  All this is why we need the `element` macro. With it, we can now select:

      [data-element='example.core/library-info-card/card-content']
        > .books > li > .title

  That will always match exactly the node we mean."
  [element-name & body]
  (assert (and (keyword? element-name)
               (nil? (namespace element-name)))
          (str "Element name should be given as an unqualified keyword, but was given as " (prn-str element-name)))
  (assert (contains? (:locals &env) component-name-symbol)
          "element form must appear within a component form.")
  `(element* ~(name element-name) ~component-name-symbol (do ~@body)))

(defmacro while-let
  "Repeatedly executes body while test expression is true, with form bound to
  test."
  [[form test] & body]
  `(loop [temp# ~test]
     (when temp#
       (let [~form temp#]
         ~@body
         (recur ~test)))))

(defmacro ns-docstring
  "Returns the docstring of the current namespace. Useful for devcards. This has
  to be a macro, because namespaces aren't reified in the JS runtime; they only
  exist during compilation."
  []
  (-> &env :ns :doc))
