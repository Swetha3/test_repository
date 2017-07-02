(ns frontend.devcards.test-morphs
  (:require [cljs.core.async :as async :refer [<! chan]]
            [cljs.test :refer-macros [async is testing]]
            [clojure.spec :as s :include-macros true]
            [clojure.test.check.generators :as gen]
            [frontend.devcards.morphs :as morphs]
            [om.core :as om]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros
   [cljs.core.async.macros :refer [go]]
   [devcards.core :as dc :refer [defcard deftest]]
   [sablono.core :refer [html]]))

(deftest signature-test
  (is (= {:type "div"
          "className" "foo"
          :children [{:type "span"
                      "className" "baz"
                      :children []}]}
         (morphs/signature (html [:div {:class "foo" :title "bar"}
                                  [:span {:class "baz" :title "qux"}
                                   "Some text."]])))))

(defn demo-child-component-om-prev [{:keys [description]} owner]
  (reify
    om/IRender
    (render [_]
      ;; A class name that varies will produce morphs. But this demonstrates
      ;; that a class name inside a child component won't produce morphs of its
      ;; parent. The child is treated as a black box.
      (html [:div {:class description}]))))

(defui DemoChildComponentOmNext
  Object
  (render [this]
    (let [{:keys [description]} (om-next/props this)]
      ;; (See demo-child-component-om-prev.)
      (html [:div {:class description}]))))

(def demo-child-component-om-next (om-next/factory DemoChildComponentOmNext))

(defn demo-component [props]
  (html
   [:div {:class (name (::type props))}
    [:.description
     (when (< 2 (count (::description props))) {:class "long"})
     (::description props)

     ;; Child components should be ignored.
     (om/build demo-child-component-om-prev {:description (::description props)})
     (demo-child-component-om-next {:description (::description props)})]]))

(defui DemoComponentOmNext
  Object
  (render [this]
    (demo-component (om-next/props this))))

(def demo-component-om-next (om-next/factory DemoComponentOmNext))

(defn infinite-morph-component [props]
  (html [:div {:class (::description props)}]))


(s/fdef demo-component
  :args (s/cat :data ::data-for-component))

(s/fdef demo-component-om-next
  :args (s/cat :data ::data-for-component))

(s/fdef infinite-morph-component
  :args (s/cat :data ::data-for-component))


(s/def ::data-for-component (s/keys :req [::type ::description]))
(s/def ::type #{:type-a :type-b})
(s/def ::description (s/and string? seq))


(deftest morph-data-test
  (testing "Generates one set of data for each morph of a functional component"
    (async done
      (go
        (dotimes [_ 10]
          (let [data (<! (async/into [] (morphs/generate (chan) #'demo-component)))
                ;; Each value is a signature, morph pair.
                morphs (map second data)]
            (is (every? (partial s/valid? (:args (s/get-spec #'demo-component))) morphs))
            (is (= 4 (count morphs)))
            (is (= 2 (count (group-by (comp ::type first) morphs))))))
        (done))))

  (testing "Generates one set of data for each morph of an Om component"
    (async done
      (go
        (dotimes [_ 10]
          (let [data (<! (async/into [] (morphs/generate (chan) #'demo-component-om-next)))
                ;; Each value is a signature, morph pair.
                morphs (map second data)]
            (is (every? (partial s/valid? (:args (s/get-spec #'demo-component-om-next))) morphs))
            (is (= 4 (count morphs)))
            (is (= 2 (count (group-by (comp ::type first) morphs))))))
        (done))))

  (testing "Limited to 100 morphs"
    (async done
      (go
        (let [data (<! (async/into [] (morphs/generate (chan) #'infinite-morph-component)))]
          ;; We generate 100 samples. infinite-morph-component should generate a
          ;; different morph for every sample, but because this is random,
          ;; sometimes we get a duplicate or two. Testing that we get more than
          ;; 95 is resilient and serves the purpose.
          (is (< 95 (count data)))
          (done)))))

  (testing "Accepts generator overrides"
    (async done
      (go
        (dotimes [_ 10]
          (let [data (<! (async/into [] (morphs/generate (chan) #'demo-component {::description #(gen/return "Mostly harmless.")})))
                ;; Each value is a signature, morph pair.
                morphs (map second data)]
            (is (every? (partial = "Mostly harmless.") (map (comp ::description first) morphs)))))
        (done)))))

(defcard demo
  (html
   [:div
    [:style
     ".type-a { background-color: blue; }
      .type-b { background-color: green; }
      .long { color: red; }"]
    (morphs/render #'demo-component
                   (fn [morphs]
                     (html [:div (map (partial apply demo-component) morphs)])))]))
