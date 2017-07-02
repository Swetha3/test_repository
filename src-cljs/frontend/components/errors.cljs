(ns frontend.components.errors
  (:require [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.footer :as footer]
            [frontend.config :as config]
            [frontend.models.feature :as feature]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [html]]
            [frontend.utils.github :as gh-utils :refer [auth-url]]
            [om.core :as om :include-macros true]))

(defn error-page-with-cta [{:keys [status logged-in?]} owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:.page.error
        [:.jumbotron
         common/language-background-jumbotron
         [:.banner
          [:.container
           (cond
             (= status 500) [:img.error-img {:src (utils/cdn-path "/img/outer/errors/500.svg")
                                             :alt "500"}]
             (or (= status 404) (= status 401)) [:img.error-img {:src (utils/cdn-path "/img/outer/errors/404.svg")
                                                                 :alt "404"}]
             :else [:span.error-zero])]]
         [:.container
          [:p "Something doesn't look right ..."]
          (cond
            (= status 500) [:div [:p.error-message "If the problem persists, feel free to check out our "
                                  [:a {:href "http://status.circleci.com/"} "status"]
                                  " or "
                                  [:a {:href "mailto:sayhi@circleci.com"} "contact us"]
                                  "."]]
            (or (= status 404) (= status 401))
            [:div
             [:h3 "You may have been logged out. "]
             [:p.error-message
              "Learn more about "
              [:a {:href "https://circleci.com/"} "CircleCI"]
              " , "
              [:a {:href "https://circleci.com/mobile/osx/"} "CircleCI for OS X"]
              ", or "
              [:a {:href "https://circleci.com/enterprise/"} "CircleCI for Enterprise"]
              "."]]
            :else "Something completely unexpected happened")]]
        [:.jumbotron
         common/language-background-jumbotron
         [:.container
          [:p.error-message
           [:span "Signing up with CircleCI is "]
           [:strong "free"]
           [:span ". Next, you'll be taken to GitHub or Bitbucket to authenticate so you can start shipping faster."]]
          (om/build common/sign-up-cta {})
          [:div.fine-print
           "By clicking on \"Authorize GitHub\" or \"Authorize Bitbucket\" you are agreeing to our "
           [:a {:href "https://circleci.com/terms-of-service/"} "Terms of Service"]
           " and "
           [:a {:href "https://circleci.com/privacy/"} "Privacy Policy"]
           "."]]]]))))

(defn error-page [app owner]
  (reify
    om/IRender
    (render [_]
      (let [data {:status (get-in app [:navigation-data :status])
                  :logged-in? (get-in app state/user-path)}
            orig-nav-point (get-in app [:original-navigation-point])
            _ (utils/mlog "error-page render with orig-nav-point " orig-nav-point " and logged-in? " (-> data :logged-in? boolean))]
        (html
         [:div
          (om/build error-page-with-cta data)
          [:footer.main-foot
           (footer/footer)]])))))
