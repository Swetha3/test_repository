(ns frontend.send.resolve
  (:require [bodhi.aliasing :as aliasing]
            [bodhi.core :as bodhi]
            [cljs.core.async :as async :refer [chan close! put!]]
            [cljs.core.async.impl.protocols :as async-impl]
            [goog.log :as glog]
            [om.next :as om]
            [promesa.core :as p :include-macros true])
  (:import goog.debug.Console))

(defonce *logger*
  (when ^boolean goog.DEBUG
    (.setCapturing (Console.) true)
    (glog/getLogger "frontend.send.resolve")))

(def ^:private parser
  (om/parser {:read (bodhi/read-fn
                     (-> bodhi/basic-read
                         aliasing/read))}))

(defn query
  "An easy way to return a value from a resolver when you have complex data from
  an API. Queries the given AST against the value given. This will limit the
  result to keys requested in the AST, as well as apply aliases to the keys."
  [ast value]
  (parser {:state (atom value)} (mapv om/ast->query (:children ast))))

(defn- read-port? [x]
  (implements? async-impl/ReadPort x))

(defn- pipe-values
  "Pipes value(s) from `from` to `to`, returning `to`. If `from` is a channel or
  a promise of a channel, pipes to that channel as with core.async/pipe. If
  `from` is a value or a promise of a value, puts that value on `to` and closes
  `to`."
  [from to]
  (p/then
   from
   (fn [v]
     (if (read-port? v)
       (async/pipe v to)
       (doto to (put! (if (nil? v) ::nil v)) close!))))
  to)

(defn resolve
  "Takes an env map, a query or a query ast, and a channel. Resolves the query
  using the resolver functions in the map at `(:resolvers env)`, and streams the
  results onto the channel. It's like a parser, but it's asynchronous, so it can
  be used to fetch data from a server.

  The resolver map is a map of query keys and sets of query keys to functions.
  When the map key is a single query key, the function should resolve to the
  value for that key. When the map key is a set of keys, the function should
  resolve to a map giving a value to each of those query keys. (This makes it
  easy to implement several query keys with a single function, rather than
  writing it out multiple times.)

  To resolve to a value, the function must return one of the following:

  * The simple value,
  * A (Promesa) promise of the value,
  * A core.async channel which will carry the value (or several values) and then
    close, or
  * A (Promesa) promise of such a channel.

  A resolver function takes two args: the env map and the ast of the query node
  that's being resolved (for single-key resolvers) or all of the asts being
  resolved as a map (for set-of-keys resolvers).

  resolve returns `channel` and will put the novelty onto that channel. The
  novelty will come in pieces, streaming in as promises resolve (that is, as API
  calls finish). Each piece of novelty will be in a form that matches the
  original query, so it will be suitable to be merged into the app state using
  that query. For instance, the query
  `[{:app/current-user [:user/name :user/favorite-color]}] might yield the
  following separate maps on the channel:

  * `{:app/current-user {:user/name \"Sarah\"}`
  * `{:app/current-user {:user/favorite-color :color/red}
  * `nil` (channel closed)

  Rather than wait for the entire query to be fulfilled, this allows the app to
  display data as it becomes available.

  See the tests for examples."
  [env query-or-ast channel]
  (let [ast (if (vector? query-or-ast)
              (om/query->ast query-or-ast)
              query-or-ast)
        resolvers (:resolvers env)
        children (:children ast)]
    (async/pipe
     (async/merge
      (for [ast children
            :let [read-from-key (get-in ast [:params :<] (:key ast))
                  ast (update ast :params dissoc :<)]]
        (if (contains? resolvers read-from-key)
          (let [resolver (get resolvers read-from-key)]
            (pipe-values (resolver env ast)
                         (chan 1 (comp
                                  (map #(if (= ::nil %) nil %))
                                  (map #(hash-map (:key ast) %))))))
          (if-let [[_keys resolver]
                   (first (filter #(contains? (key %) read-from-key) resolvers))]
            (pipe-values (resolver env {read-from-key ast})
                         (chan 1 (comp
                                  (map #(get % read-from-key))
                                  (map #(if (= ::nil %) nil %))
                                  (map #(hash-map (:key ast) %)))))
            (do
              (glog/error *logger* (str "No resolver found for key " read-from-key))
              (close! chan))))))
     channel)))