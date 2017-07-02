(ns frontend.utils.function-query
  "A facility for functions to specify their Om queries, just like components,
  to be composed into component queries.

  Ideally, a component query should only mention things that the component
  itself uses. If a component uses a property, it should include the property in
  its query. If a component's *subcomponent* uses a property, the component
  should compose the subcomponent's query into its own. Otherwise, the
  component's query would need to be edited any time the subcomponent's query is
  altered. This is a coupling problem.

  This would be bad code:

  (defui Pet
    om/IQuery
    (query [this]
      [:pet/name])
    Object
    (render [this]
      (html
       [:div
        [:.name (:pet/name (om/props this))]])))

  (def pet (om/factory Pet))

  (defui Person
    om/IQuery
    (query [this]
      [:person/name
       ;; Repeating Pet's query here is bad.
       {:person/pet [:pet/name]}])
    Object
    (render [this]
      (html
       [:div
        [:.name (:person/name (om/props this))]
        [:.pet (pet (:person/pet (om/props this)))]])))

  Instead, Person should compose the query for Pet:

  (defui Person
    om/IQuery
    (query [this]
      [:person/name
       ;; Now the query is composed.
       {:person/pet (om/get-query Pet)}]))

  The same problem holds for functions the component passes data to, but because
  functions don't have queries, there's no solution. That is, we still have this
  coupling problem:

  (defn form-of-address [person]
    (case (:person/profession person)
      :professor (str \"Professor \" (:person/last-name))
      :president (str \"President \" (:person/last-name))
      (str (:person/first-name) (:person/last-name))))

  (defui Person
    om/IQuery
    (query [this]
      ;; The Person component knows what form-of-address needs.
      [:person/first-name
       :person/last-name
       :person/profession])
    Object
    (render [this]
      (html
       [:div
        [:.name (form-of-address person)]])))

  This namespace helps you assign queries to functions and fetch them to compose
  into component queries. The code above becomes:

  (defn form-of-address
    {::fq/queries {:person [:person/first-name
                            :person/last-name
                            :person/profession]}}
    [person]
    (case (:person/profession person)
      :professor (str \"Professor \" (:person/last-name))
      :president (str \"President \" (:person/last-name))
      (str (:person/first-name) (:person/last-name))))

  (defui Person
    om/IQuery
    (query [this]
      ;; The Person component only knows that it uses form-of-address.
      (fq/get form-of-address :person))
    Object
    (render [this]
      (html
       [:div
        [:.name (form-of-address person)]])))

  (where `fq` is an alias for this namespace)

  There are a couple of important things to be aware of:

  1. A function has a map of queries, so that each argument can have its own
     query as needed. By convention, the keys in this map should match the names
     of the arguments they correspond to, but there's no technical connection
     between the names, and it may be useful in some cases to use names that
     don't correspond directly to argument names. Name things however is
     clearest for the reader.

  2. A function query can include queries from other functions using
     `(get function :query-name)`. However, ClojureScript `defn` metadata maps
     are quoted (unlike Clojure `defn` metadata maps, which are evaluated at
     compile-time). To make deeper composition possible, `get` expands
     recursively, but this doesn't happen until (and unless) the query is
     actually used, and error messages may point to the wrong source location.

  3. Composing component queries serves more of a purpose than simply decoupling
     your components. Om uses the tree of query composition to index the
     structure of your UI. This means Om can re-render particular components
     when their data changes. It also means that when a component instance's
     query changes with om/set-query!, the appropriate part of the root query
     changes.

     None of this applies to function queries. Functions are not components, and
     cannot be rendered independently. Function queries are static, and cannot
     change at runtime. This also means that, while component queries must be
     used unmodified at a particular key in the query tree, function queries may
     be freely mixed and merged wherever they are needed. The `merge` function is
     provided for this purpose."

  (:refer-clojure :exclude [merge])
  (:require [om.util :as om-util]))

(defn- query->map
  "Turns a query q into a map of keys to subqueries, or to nil for non-joins."
  [q]
  (into {}
        (map #(if (om-util/join? %) % {% nil}))
        q))

(defn- map->query
  "Turns a query map m back into a query."
  [m]
  (into []
        (map #(if (val %)
                {(key %) (val %)}
                (key %)))
        m))


(defn merge
  "Merge several queries together, merging subqueries for matching keys."
  [& queries]
  (->> queries
       (map query->map)
       (reduce (partial merge-with merge))
       map->query))
