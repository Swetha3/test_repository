(ns frontend.components.landing
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.shared :as shared]
            [frontend.scroll :as scroll]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :refer-macros [defrender]]
            [frontend.utils.github :refer [auth-url]]
            [frontend.utils.seq :refer [select-in]]
            [goog.events]
            [goog.dom]
            [goog.style]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]])
  (:import [goog.ui IdGenerator]))

(defn maybe-set-state! [owner korks value]
  (when (not= (om/get-state owner korks) value)
    (om/set-state! owner korks value)))


(def nav-height 70)

(def view "home")

(defn home-cta [{:keys [source cta-class]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      ((om/get-shared owner :track-event) {:event-type :signup-impression}))
    om/IRender
    (render [_]
      (html
        (if (= :page (om/get-shared owner [:ab-tests :auth-button-vs-page]))
          [:a.home-action {:class cta-class
                           :href "/signup/"
                           :role "button"
                           :on-mouse-up #((om/get-shared owner :track-event) {:event-type :signup-clicked})}
           (str (common/sign-up-text))]
          [:a.home-action
           {:href  (auth-url :destination "/dashboard")
            :role "button"
            :on-click #(raise! owner  [:track-external-link-clicked
                                       {:event "oauth_authorize_click"
                                        :properties  {"oauth_provider" "github"}
                                        :path  (auth-url :destination "/dashboard")}])}
           (str (common/sign-up-text))])))))

(defn prolog [data owner {:keys [logo-visibility-callback
                                 cta-visibility-callback
                                 prolog-visibility-callback
                                 header-overlap-callback]}]
  (reify
    om/IDisplayName (display-name [_] "Home Prolog")
    om/IInitState (init-state [_] {:logo-visible? (atom nil)
                                   :cta-visible? (atom nil)
                                   :prolog-visible? (atom nil)
                                   :header-overlap-px (atom 0)})
    om/IDidMount
    (did-mount [_]
      (scroll/register
       owner
       #(let [logo (om/get-node owner "center-logo")
              container (om/get-node owner)
              cta (utils/sel1 container ".prolog-cta")
              prolog (om/get-node owner "home-prolog")
              logo-visible? (neg? (.-bottom (.getBoundingClientRect logo)))
              cta-visible? (neg? (.-bottom (.getBoundingClientRect cta)))
              prolog-visible? (>= (.-bottom (.getBoundingClientRect prolog)) nav-height)
              cta-visible? (neg? (.-bottom (.getBoundingClientRect cta)))
              header-overlap-px (- nav-height (.-bottom (.getBoundingClientRect prolog)))
              ;; normalize the overlap
              header-overlap-px (min nav-height (max 0 header-overlap-px))]
          (when-not (= logo-visible? @(om/get-state owner :logo-visible?))
            (reset! (om/get-state owner :logo-visible?) logo-visible?)
            (logo-visibility-callback logo-visible?))
          (when-not (= cta-visible? @(om/get-state owner :cta-visible?))
            (reset! (om/get-state owner :cta-visible?) cta-visible?)
            (cta-visibility-callback cta-visible?))
          (when-not (= prolog-visible? @(om/get-state owner :prolog-visible?))
            (reset! (om/get-state owner :prolog-visible?) prolog-visible?)
            (prolog-visibility-callback prolog-visible?))
          (when-not (= header-overlap-px @(om/get-state owner :header-overlap-px))
            (reset! (om/get-state owner :header-overlap-px) header-overlap-px)
            (header-overlap-callback header-overlap-px)))))
    om/IWillUnmount
    (will-unmount [_]
      (scroll/dispose owner))
    om/IRender
    (render [_]
      (html
       [:section.home-prolog {:ref "home-prolog"}
        (om/build home-cta {:source view
                            :cta-class "prolog-cta"})
        [:div.home-top-shelf]
        [:div.home-slogans
         [:h1.slogan.proverb {:item-prop "Ship better code, faster."}
          "Ship better code, faster."]
         [:h3.slogan.context.top-line {:item-prop "You have a product to focus on, let CircleCI handle your Continuous Integration & Deployment."}
          "You have a product to focus on, let CircleCI handle your Continuous Integration & Deployment."]]
        [:div.home-avatars
         [:div.avatars
          [:div.avatar-github
           (common/ico :github)]
          [:div.avatar-circle {:ref "center-logo"}
           (common/ico :logo)]]]
        [:div.home-bottom-shelf
         [:a {:on-click #(raise! owner [:home-scroll-1st-clicked])}
          "Learn more"
          (common/ico :chevron-down)]]]))))

(defn purpose [data owner]
  (reify
    om/IDisplayName (display-name [_] "Home Purpose")
    om/IDidMount
    (did-mount [_]
      (scroll/register owner
       #(let [article (om/get-node owner "purpose-article")
              vh (.-height (goog.dom/getViewportSize))
              animate? (< (.-bottom (.getBoundingClientRect article)) vh)]
          (maybe-set-state! owner [:first-fig-animate] animate?))))
    om/IWillUnmount
    (will-unmount [_]
      (scroll/dispose owner))
    om/IRender
    (render [_]
      (html
       [:section.home-purpose {:class (when (om/get-state owner [:first-fig-animate]) "animate")}
        [:div.home-top-shelf]
        [:div.home-purpose-content
         [:div.home-articles
          [:article {:ref "purpose-article"}
           [:h2 "Launches are dead, long live iteration."]
           [:p "We believe that rapid iteration, tight feedback loops, and team communication are the keys to a great product workflow.
                       That's why we designed the world's leading continuous integration and delivery solution.
                       Continuous integration and delivery is revolutionizing the way development teams operate by reducing barriers between your ideas and your production code.
                       Remember, it doesn't count until it ships."]
           [:p
            [:a.shopify-link {:href "https://circleci.com/customers/shopify/"}
             "See how Shopify does it"
             (common/ico :slim-arrow-right)]]]]]
        [:div.home-bottom-shelf
         [:a {:on-click #(raise! owner [:home-scroll-2nd-clicked])}
          ;; "Continue" ; hold off on this line of copy, it's cleanr w/o
          (common/ico :chevron-down)]]]))))

(defn practice [app owner]
  (reify
    om/IDisplayName (display-name [_] "Home Practice")
    om/IRender
    (render [_]
      (let [selected-customer (get-in app state/customer-logo-customer-path :shopify)
            selected-toolset (get-in app state/selected-toolset-path :languages)]
        (html
         [:section.home-practice
          [:div.practice-articles
           [:article
            [:h2 "Devs rely on us to just work; we support the right tools."]
            [:p
             (for [toolset ["Languages" "databases" "queues" "browsers" "deployment"]]
               (list
                [:a {:on-mouse-enter #(raise! owner [:toolset-clicked {:toolset (keyword (str/lower-case toolset))}])} toolset] ", "))
             "we support all of your tools.
              If it runs on Linux, then it will work on CircleCI.
              We'll even be around to help you install your own tools.
              The best development teams in the world trust us as their continuous integration and delivery solution because of our unmatched support and our ability to scale with them.
              We're built for teams."]]]
          [:div.home-bottom-shelf
           [:a {:on-click #(raise! owner [:home-scroll-3rd-clicked])}
            ;; "Continue" ; hold off on this line of copy, it's cleanr w/o
            (common/ico :chevron-down)]]])))))

(defn potential [data owner]
  (reify
    om/IDisplayName (display-name [_] "Home Potential")
    om/IDidMount
    (did-mount [_]
      (scroll/register owner
       #(let [article (om/get-node owner "potential-article")
              vh (.-height (goog.dom/getViewportSize))
              animate? (< (.-bottom (.getBoundingClientRect article)) vh)]
          (maybe-set-state! owner [:second-fig-animate] animate?))))
    om/IWillUnmount
    (will-unmount [_]
      (scroll/dispose owner))
    om/IRender
    (render [_]
      (html
       [:section.home-potential {:class (when (om/get-state owner [:second-fig-animate]) "animate")}
        [:div.home-top-shelf]
        [:div.home-potential-content
         [:div.home-articles
          [:article {:ref "potential-article"}
           [:h2 "Look under the hood & check the bullet points."]
           [:div.home-potential-bullets
            [:ul
             [:li "Quick & easy setup"]
             [:li "Lightning fast builds"]
             [:li "Deep Customization"]
             [:li "Easy debugging"]]
            [:ul
             [:li "Smart notifications"]
             [:li "Loving support"]
             [:li "Automatic parallelization"]
             [:li "Continuous Deployment"]]
            [:ul
             [:li "Build artifacts"]
             [:li "Clean build environments"]
             [:li "GitHub Integration"]
             [:li "Free Open Source Support"]]]]]]
        [:div.home-bottom-shelf
         [:a {:on-click #(raise! owner [:home-scroll-4th-clicked])}
          ;; "Continue" ; hold off on this line of copy, it's cleanr w/o
          (common/ico :chevron-down)]]]))))

(defn epilog [data owner {:keys [cta-visibility-callback
                                 epilog-visibility-callback
                                 header-overlap-callback]}]
  (reify
    om/IDisplayName (display-name [_] "Home Epilog")
    om/IInitState (init-state [_] {:cta-visible? (atom nil)
                                   :epilog-visible? (atom nil)
                                   :header-overlap-px (atom 0)})
    om/IDidMount
    (did-mount [_]
      (scroll/register
       owner
       #(let [vh (.-height (goog.dom/getViewportSize))
              container (om/get-node owner)
              cta (utils/sel1 container ".epilog-cta")
              epilog (om/get-node owner "home-epilog")
              cta-visible? (< (.-top (.getBoundingClientRect cta)) vh)
              epilog-visible? (< (.-top (.getBoundingClientRect epilog)) nav-height)
              header-overlap-px (.-top (.getBoundingClientRect epilog))
              ;; normalize the overlap
              header-overlap-px (min nav-height (max 0 header-overlap-px))]
          (when-not (= cta-visible? @(om/get-state owner :cta-visible?))
            (reset! (om/get-state owner :cta-visible?) cta-visible?)
            (cta-visibility-callback cta-visible?))
          (when-not (= epilog-visible? @(om/get-state owner :epilog-visible?))
            (reset! (om/get-state owner :epilog-visible?) epilog-visible?)
            (epilog-visibility-callback epilog-visible?))
          (when-not (= header-overlap-px @(om/get-state owner :header-overlap-px))
            (reset! (om/get-state owner :header-overlap-px) header-overlap-px)
            (header-overlap-callback header-overlap-px)))))
    om/IWillUnmount
    (will-unmount [_]
      (scroll/dispose owner))
    om/IRender
    (render [_]
      (html
       [:section.home-epilog {:ref "home-epilog"}
        (om/build home-cta {:source view
                            :cta-class "epilog-cta"})
        [:div.home-top-shelf]
        [:div.home-slogans
         [:h2.slogan.proverb {:item-prop "So, ready to ship faster?"}
          "So, ready to ship faster?"]
         [:h3.slogan.context.top-line {:item-prop "Next you'll just need to log in using your GitHub account. Still not convinced? Check out our pricing."}
          "Next you'll just need to log in using your GitHub account. Still not convinced? Check out our "
          [:a {:href "https://circleci.com/pricing/"} "pricing"]
          "."]]
        [:div.home-avatars
         [:div.avatars
          [:div.avatar-github
           (common/ico :github)]
          [:div.avatar-circle
           (common/ico :logo)]]]
        [:div.home-bottom-shelf]]))))

(defn home [app owner]
  (reify
    om/IDisplayName (display-name [_] "Homepage")
    om/IInitState
    (init-state [_]
      {:header-logo-visible false
       :header-cta-visible false
       :header-bkg-visible false
       :header-cta-invisible false
       :header-bkg-invisible false})
    om/IRender
    (render [_]
      (html
       [:div.home.page
        (om/build prolog {} {:opts {:logo-visibility-callback
                                    (fn [visible?]
                                      (om/set-state! owner :header-logo-visible visible?))
                                    :cta-visibility-callback
                                    (fn [visible?]
                                      (om/set-state! owner :header-cta-visible visible?))
                                    :prolog-visibility-callback
                                    (fn [visible?]
                                      (om/set-state! owner :header-bkg-visible (not visible?)))
                                    :header-overlap-callback
                                    (fn [visible-px]
                                      (om/set-state! owner :header-bkg-scroller visible-px))}})
        (om/build purpose {})
        (om/build practice (select-in app [state/customer-logo-customer-path
                                           state/selected-toolset-path]))
        (om/build potential {})
        (om/build epilog {} {:opts {:cta-visibility-callback
                                    (fn [visible?]
                                      (om/set-state! owner :header-cta-invisible visible?))
                                    :epilog-visibility-callback
                                    (fn [visible?]
                                      (om/set-state! owner :header-bkg-invisible visible?))
                                    :header-overlap-callback
                                    (fn [visible-px]
                                      (om/set-state! owner :header-bkg-scroller visible-px))}})]))))
