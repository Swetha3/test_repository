(ns frontend.elevio
  (:require [frontend.utils :as utils]
            [frontend.models.user :as user]
            [frontend.utils :as utils :refer [mlog]]
            [goog.string :as gstring]
            [goog.dom :as gdom]
            [goog.dom.classlist :as class-list]))

(def account-id "5639122987b91")

(defn open-module-fn []
  (some-> (aget js/window "_elev")
          (aget "openModule")))

(defn broken?
  "Returns true if elevio is broken, false otherwise"
  []
  (nil? (open-module-fn)))

(defn open-module! [module-name]
  (when-not (broken?)
    ((open-module-fn) module-name)))

(defn show-support! []
  (open-module! "support"))

(defn show-status! []
  (open-module! "status"))

(defn get-root []
  (gdom/getElement "elevio-widget"))

(defn disable! []
  (when-let [el (get-root)]
    (gdom/removeNode el))
  (aset js/window "_elev" #js {}))

(defn set-elev! [k v]
  (aset (aget js/window "_elev") k v))

(defn maybe-change-user!
  "Update the cached elevio user.

  Elevio caches the user info in some hidden object and then uses that
  to populate user data before sending that over the wire. We use the _elev.user
  object as our source of truth, but we need to sync it back to the hidden
  elevio user object so that the data is sent over the wire to sources like
  zendesk."
  []
  (when-let [change-user! (-> js/window
                             (aget "_elev")
                             (aget "changeUser"))]
    (change-user! (-> js/window
                      (aget "_elev")
                      (aget "user")))))

(defn flatten-traits
  "The _elev.user.traits must be a flat map. If they include an object,
  then in zendesk it will show up at [object object]. so, recursively flatten
  the map but concatenate the keys together to preserve namespacing."
  [traits]
  (let [concatenate-keys (fn [k [sub-k v]]
                           [(-> (gstring/format "%s-%s" (name k) (name sub-k))
                                keyword)
                            v])]
    (->> traits
         (reduce-kv (fn [to-return k v]
                      (if (map? v)
                        (merge to-return (->> (flatten-traits v)
                                              (map (partial concatenate-keys k))
                                              (into {})))
                        (assoc to-return k v)))
                    {}))))

(defn normalize-traits [traits]
  (-> traits
      (flatten-traits)
      (utils/clj-keys-with-dashes->clj-keys-with-underscores)))

(defn add-user-traits!
  "Given a map of user traits, add it to the _elev.user.traits object.

  This object must be flat, so flatten any nested maps while preserving
  the path information."
  [new-traits]
  (let [user (-> js/window
                 (aget "_elev")
                 (aget "user"))
        current-traits (-> user
                           (aget "traits")
                           js->clj)
        new-traits (normalize-traits new-traits)]
    (-> user
        (aset "traits" (-> (merge current-traits new-traits)
                           clj->js))))
  (maybe-change-user!))

(defn init-user
  "Initialize the _elev.user map.

  Because of some race condition I could not diagnose, during the initialization of
  the elevio calling _elev.changeUser() throws. So don't call add-user-traits! as it
  calls _elev.changeUser() and will cause the app to go :boom: and die."
  [initial-user-data]
  (set-elev! "user" (-> js/window
                        (aget "elevSettings")
                        (aget "user")))
  (add-user-traits! initial-user-data))

(defn enable! [initial-user-data]
  (class-list/add js/document.body "circle-elevio")
  (aset js/window "_elev" (or (aget js/window "_elev") #js {}))
  ;; Some subset of the user data is currently set server side by adding
  ;; a js/window.elevSettings.user in a script tag in the DOM. We should
  ;; probably add all user traits client side, but as this is not broke I
  ;; am not going to fix it.
  (let [support-module-id "support"
        discuss-link-module-id 3003
        discuss-support-link-module-id 3762]
    (if user/support-eligible?
      ;; enable zendesk support, disable discuss support
      (set-elev! "disabledModules" #js [discuss-support-link-module-id])
      ;; enable discuss support, disable zendesk support
      (set-elev! "disabledModules" #js [support-module-id discuss-link-module-id]))
    (set-elev! "account_id" account-id)
    (init-user initial-user-data)
    (set-elev! "pushin" "false")
    (set-elev! "translations"
          #js {"loading"
               #js {"loading_ticket" "Loading support request"
                    "loading_tickets" "Loading support requests"
                    "reloading_ticket" "Reloading support request"},
               "modules"
               #js {"support"
                    #js {"create_new_ticket" "Create new support request"
                         "submit" "Submit support request"
                         "reply_placeholder" "Write your reply here"
                         "no_tickets" "Currently no existing support requests"
                         "back_to_tickets" "Back to your support requests"
                         "thankyou" "Thanks for submitting a support request. We try to respond to all tickets within 1-2 business days. If you need assistance sooner, <a target=\"_blank\" href=\"https://discuss.circleci.com/\">our community</a> may be able to help."
                         "delayed_appearance" ""
                         "deflect" "Before you submit a support request, please check to see if your question has already been answered on <a target=\"_blank\" href=\"https://discuss.circleci.com/\">Discuss</a>."}}})))
