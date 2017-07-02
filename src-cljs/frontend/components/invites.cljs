(ns frontend.components.invites
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [html]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils.vcs :as vcs]
            [frontend.routes :as routes]
            [om.core :as om :include-macros true])
  (:import [goog Uri]))

(defn invitees
  "Filters users to invite and returns only fields needed by invitation API"
  [users]
  (->> users
       (filter (fn [u] (and (:email u)
                            (:checked u))))
       ;; select all of login and id (GitHub) and username and uuid (Bitbucket)
       (map (fn [u] (select-keys u [:email :login :username :id :uuid])))
       vec))

(defn invite-tile [user owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [email login index]} user]
        (html
         [:li
          [:div.invite-gravatar
           [:img {:src (gh-utils/make-avatar-url user)}]]
          [:div.invite-profile
           login
           [:input {:on-change #(utils/edit-input owner (conj (state/build-invite-member-path index) :email) %)
                    :required true
                    :type "email"
                    :value email
                    :id (str login "-email")}]
           [:label.no-email {:for (str login "-email") :title "We could not retrieve this teammate's email address because it has been set as private."}
            [:i.fa.fa-exclamation-circle]
            " Click to add an email address."]]
          [:label.invite-select {:id (str login "-label")
                                 :for (str login "-checkbox")}
           [:input {:type "checkbox"
                    :id (str login "-checkbox")
                    :checked (boolean (:checked user))
                    :on-change #(utils/toggle-input owner (conj (state/build-invite-member-path index) :checked) %)}]
           [:div.checked \uf046]
           [:div.unchecked \uf096]]])))))

(defn invites [users owner opts]
  (reify
    om/IRender
    (render [_]
      (html
        [:div
         [:section
          [:a {:role "button"
               :on-click #(raise! owner [:invite-selected-all])}
           "all"]
          " / "
          [:a {:role "button"
               :on-click #(raise! owner [:invite-selected-none])}
           "none"]
          [:ul
           (om/build-all invite-tile users {:key :login})]]
         [:footer
          (forms/managed-button
           [:button.btn.btn-success (let [users-to-invite (invitees users)]
                                      {:data-success-text "Sent"
                                       :on-click #(raise! owner [:invited-team-members
                                                                 (merge {:invitees users-to-invite
                                                                         :vcs_type (:vcs_type opts)}
                                                                        (if (:project-name opts)
                                                                          {:project-name (:project-name opts)}
                                                                          {:org-name (:org-name opts)}))])})

            "Send Invites "
            [:i.fa.fa-envelope-o]])]]))))

(defn build-invites [invite-data owner opts]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [project-name (:project-name opts)
            vcs_type (:vcs_type opts)]
        (raise! owner [:load-first-green-build-github-users {:vcs_type vcs_type
                                                             :project-name project-name}])))
    om/IRender
    (render [_]
      (let [users (remove :following (:org-members invite-data))
            dismiss-form (:dismiss-invite-form invite-data)]
        (html
         [:div.first-green.invite-form {:class (when (or (empty? users) dismiss-form)
                                                 "animation-fadeout-collapse")}
          [:button {:on-click #(raise! owner [:dismiss-invite-form])}
           [:span "Dismiss "] [:i.fa.fa-times-circle]]
          [:header
           [:div.head-left
            (common/icon {:type :status :name :pass})]
           [:div.head-right
            [:h2 "Congratulations!"]
            [:p "You just got your first green build! Invite some of your collaborators below and never test alone!"]]]
          (om/build invites users {:opts opts})])))))

(defn side-item [org owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:li.side-item
         [:a {:href (str "/invite-teammates/organization/"
                         (vcs/long-to-short-vcs (:vcs_type org))
                         "/" (:login org))}
          [:img {:src (gh-utils/make-avatar-url org :size 25)
                 :width 25 :height 25}]
          [:div.orgname (:login org)]]]))))
