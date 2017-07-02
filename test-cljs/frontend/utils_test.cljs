(ns frontend.utils-test
  (:require [cljs.test :as test :refer-macros [deftest is are testing]]
            [frontend.utils :as utils]
            [frontend.utils.github :as gh-utils]))


(deftest deep-merge-works
  (testing "one-level"
    (is (= {:a 1 :b 2} (utils/deep-merge {:a 1} {:b 2})))
    (is (= {:a 2} (utils/deep-merge {:a 1} {:a 2})))
    (is (= {:a 1} (utils/deep-merge nil {:a 1})))
    (is (= {:a 1} (utils/deep-merge {:a 1} nil)))
    (is (thrown? js/Error (utils/deep-merge {:a 1} [1 2])))
    (is (thrown? js/Error (utils/deep-merge [1 2] {:a 1}))))

  (testing "nested maps"
    (is (= {:a {:b 1, :c 2}} (utils/deep-merge {:a {:b 1}} {:a {:c 2}})))
    (is (= {:a {:b {:c 2}}} (utils/deep-merge {:a {:b 1}} {:a {:b {:c 2}}})))
    (is (= {:a {:b {:e 2, :c 15}, :f 3}}
           (utils/deep-merge {:a {:b {:c 1}}}
                             {:a {:b {:c 15 :e 2} :f 3}}))))

  (testing "maps with other data-structures"
    (is (= {:a [1]} (utils/deep-merge {:a {:b 2}} {:a [1]})))
    (is (= {:a {:b 2}} (utils/deep-merge {:a [1]} {:a {:b 2}}))))

  (testing "explicit nils in later maps override earlier values"
    (is (= {:a nil} (utils/deep-merge {:a 1} {:a nil})))
    (is (= {:a {:b nil, :d {:e 2, :f 3}}}
           (utils/deep-merge {:a {:b {:c 1} :d {:e 2}}}
                             {:a {:b nil :d {:f 3}}})))))

(deftest avatar-url
  (testing "typical input"
    (is (contains? #{"https://avatars0.githubusercontent.com/u/1551784?v=2&s=200"
                     "https://avatars0.githubusercontent.com/u/1551784?s=200&v=2"}
                   (gh-utils/make-avatar-url {:avatar_url "https://avatars0.githubusercontent.com/u/1551784?v=2"
                                              :login "someuser"
                                              :gravatar_id "somegravatarid"}))))

  (testing "specified size"
    (is (contains? #{"http://example.com?v=2&s=17"
                     "http://example.com?s=17&v=2"}
           (gh-utils/make-avatar-url {:avatar_url "http://example.com?v=2"} :size 17))))

  (testing "without parameters"
    (is (= "http://example.com?s=200"
           (gh-utils/make-avatar-url {:avatar_url "http://example.com"}))))
 
  (testing "fall back to gravatar/identicon"
    (is (contains? #{"https://secure.gravatar.com/avatar/bar?d=https%3A%2F%2Fidenticons.github.com%2Ffoo.png&s=200"
                     "https://secure.gravatar.com/avatar/bar?s=200&d=https%3A%2F%2Fidenticons.github.com%2Ffoo.png"}
                   (gh-utils/make-avatar-url {:login "foo" :gravatar_id "bar"})))))


(deftest split-map-values-at-works
  (let [orig (into (sorted-map) [[:a [1 2 3]], [:b [4 5 6]]])]
    (testing "split spill over to bottom"
      (is (= [{:a [1 2]} {:a [3] :b [4 5 6]}]
             (utils/split-map-values-at orig 2))))
    (testing "even split"
      (is (= [{:a [1 2 3]} {:b [4 5 6]}]
             (utils/split-map-values-at orig 3))))))

(deftest clj-keys-with-dashes->clj-keys-with-underscores-works
  (let [input-map {:foo-bar {:baz-foo "a"}
                   :foo-baz_bar "b"}
        expected-output {:foo_bar {:baz_foo "a"}
                         :foo_baz_bar "b"}]
    (is (= (utils/clj-keys-with-dashes->clj-keys-with-underscores input-map)
           expected-output))))

(deftest clj-keys-with-dashes->js-keys-with-underscores-works
  (let [input-map {:foo-bar {:baz-foo "a"}
                   :foo-baz_bar "b"}
        expected-output (-> {:foo_bar {:baz_foo "a"}
                             :foo_baz_bar "b"}
                            clj->js)]
    ;; Apparently you can't compared javascript objects, so convert them back
    ;; to javascript.
    (is (= (-> (utils/clj-keys-with-dashes->js-keys-with-underscores input-map)
               js->clj)
           (js->clj expected-output)))))

(deftest hiccup-element?-works
  (are [result entity] (= result (#'utils/hiccup-element? entity))
    true [:div {} "hi"]
    true [:div "hi"]
    true [:.item {:on-click #(js/console.log %)} "hi"]
    false '([:.item "hi"] [:.item "hi"])))

(deftest hiccup?-works
  (are [hiccup] (utils/hiccup? hiccup)
    [:div]
    [:div "hi"]
    [:div {:on-click #(identity %)} "hi"]
    [[:div "hi"] [:div {:on-click #(identity %)} "hi"]]
    (list [:div "hi"] [:div {:on-click #(identity %)} "hi"])))

(deftest flatten-hiccup-children-works
  (are [x y] (= x (utils/flatten-hiccup-children y))
    '([:div "a"] [:div "b"]) '([:div "a"] [:div "b"])
    '([:div "a"] [:div "b"]) '([:div "a"] ([:div "b"]))
    '([:div "a"] [:div "b"] [:div "c"] "hello" nil) '([:div "a"] ([:div "b"] ([:div "c"] "hello" nil)))
    ;; doesn't flatten hiccup elements themselves
    '([:div "a" [:div "b" [:div "c"]]]) '([:div "a" [:div "b" [:div "c"]]])))

(deftest dissoc-nil-react-keys-works
  (testing "Adds an attribute map to hiccup elements that don't have one"
   (is (= [:div {} "hi"] (utils/dissoc-nil-react-keys [:div "hi"]))))
  (testing "Removes falsey `:key` values from attribute maps"
    (is (= [:div {} "hi"] (utils/dissoc-nil-react-keys [:div {:key nil} "hi"]))))
  (testing "Leaves other attirbute map values alone"
    (is (= [:div {:class "blah"} "hi"] (utils/dissoc-nil-react-keys [:div {:key nil :class "blah"} "hi"]))))
  (testing "Expands lists of hiccup elements"
    (is (= [:div {}
            [:div {} "hi"]
            [:div {} "hi"]
            [:div {} "hi"]
            [:div {} "hi"]
            [:div {} "hi"]
            [:div {} "hi"]
            [:div {} "hi"]
            [:div {} "hi"]
            [:div {} "hi"]
            [:div {} "hi"]]
           (utils/dissoc-nil-react-keys [:div (repeat 10 [:div {:key nil} "hi"])]))))
  (testing "Leaves non-hiccup elements alone"
    (is (= nil (utils/dissoc-nil-react-keys nil)))
    (is (= "hi" (utils/dissoc-nil-react-keys "hi")))
    (is (= [:div {} "hi" nil] (utils/dissoc-nil-react-keys [:div '(("hi" nil))])))))

(deftest add-white-border-works
  (testing "returns hiccup structures without attribute maps as-is"
    (is (= [:div "hi"] (utils/add-white-border [:div "hi"]))))
  (testing "returns non-hiccup arguments as-is"
    (is (nil? (utils/add-white-border nil))))
  (testing "adds borders to hiccup structurs that have attribute maps"
    (let [[_tag attrs] (utils/add-white-border [:a {:href "/dashboard"} "To the dashboard!"])]
      (is (re-matches #"^5px solid rgb\(\d+,\d+,\d+\)$"
                      (get-in attrs [:style :border])))
      (is (= "/dashboard" (:href attrs))))))
