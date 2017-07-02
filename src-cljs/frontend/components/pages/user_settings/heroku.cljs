(ns frontend.components.pages.user-settings.heroku
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.async :refer [raise!]]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.form :as form]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [html]]
            [om.core :as om :include-macros true]))

(defn- api-key-card
  [{:keys [heroku-api-key heroku-api-key-input on-change-key-input submit-form!]}]
  (card/basic
   [:div
    [:p
     "Add your " [:a {:href "https://dashboard.heroku.com/account"} "Heroku API Key"]
     " to set up deployment with Heroku. You'll also need to set yourself as the Heroku deploy user from your project's settings page."]
    (when heroku-api-key
      [:p "Your Heroku Key is currently " heroku-api-key])
    (form/form
     {}
     (om/build form/text-field {:label "Heroku Key"
                                :value heroku-api-key-input
                                :on-change on-change-key-input})
     (button/managed-button
      {:loading-text "Saving..."
       :failed-text "Failed to save key"
       :success-text "Saved"
       :kind :primary
       :on-click submit-form!}
      "Save Heroku Key"))]))

(defn subpage [app owner]
  (reify
    om/IRenderState
    (render-state [_ _]
      (let [heroku-api-key (get-in app (conj state/user-path :heroku_api_key))
            heroku-api-key-input (get-in app (conj state/user-path :heroku-api-key-input))
            submit-form! #(raise! owner [:heroku-key-add-attempted {:heroku_api_key heroku-api-key-input}])]
        (html
         ;; TODO: Dismantle this class and use Component-Oriented Style instead.
         ;; This class is used by each subpage, so that'll take a bit of work.
         [:div.account-settings-subpage
          [:legend "Heroku API Key"]
          (api-key-card {:heroku-api-key heroku-api-key
                         :heroku-api-key-input heroku-api-key-input
                         :on-change-key-input #(utils/edit-input owner (conj state/user-path :heroku-api-key-input) %)
                         :submit-form! submit-form!})])))))

(dc/do
  (defcard api-key-card-with-no-key-set
    "Card as displayed when no key is set yet. Here, the user has just entered a new key
    and not yet saved it."
    (api-key-card {:heroku-api-key nil
                   :heroku-api-key-input "75d775d775d775d775d775d775d775d7"
                   :submit-form! #(.preventDefault %)})
    {}
    {:classname "background-gray"})

  (defcard api-key-card-with-key-set
    "Card as displayed when a key is already set. Here, the user has just entered a new
    key and not yet saved it."
    (api-key-card {:heroku-api-key "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx75d7"
                   :heroku-api-key-input "75d775d775d775d775d775d775d775d7"
                   :submit-form! #(.preventDefault %)})
    {}
    {:classname "background-gray"}))
