(ns frontend.send.test-resolve
  (:require [bodhi.aliasing :as aliasing]
            [bodhi.core :as bodhi]
            [cljs.core.async :as async :refer [chan take!]]
            [clojure.set :as set]
            [clojure.test :refer-macros [async is]]
            [frontend.send.resolve :as resolve]
            [om.next :as om]
            [promesa.core :as p :include-macros true])
  (:require-macros [devcards.core :as dc :refer [deftest]]))

(def parser
  (om/parser {:read (bodhi/read-fn
                     (-> bodhi/basic-read
                         aliasing/read))}))

(def resolvers
  {'do/something
   (fn [env ast]
     (let [do-something (get-in env [:apis :do-something])]
       (do-something (:params ast))))

   :root/user
   (fn [env ast]
     (resolve/resolve (assoc env :user/name (:user/name (:params ast)))
                      ast
                      (chan)))

   :user/name
   (fn [env ast]
     (:user/name env))

   :user/imaginary-friend
   (fn [_ _] (p/promise nil))

   #{:user/favorite-color :user/favorite-number :user/vehicle}
   (fn [env asts]
     (p/alet [get-user (get-in env [:apis :get-user])
              user (p/await (get-user {:name (:user/name env)}))]
       (-> user
           (set/rename-keys {:favorite-color :user/favorite-color
                             :favorite-number :user/favorite-number
                             :vehicle :user/vehicle})
           (update :user/vehicle set/rename-keys {:color :vehicle/color
                                                  :make :vehicle/make
                                                  :model :vehicle/model})
           (update :user/vehicle (partial resolve/query (:user/vehicle asts))))))

   :user/pets
   (fn [env ast]
     (p/alet [get-user-pets (get-in env [:apis :get-user-pets])
              pets (p/await (get-user-pets {:name (:user/name env)}))]
       (->> pets
            (map #(set/rename-keys % {:name :pet/name
                                      :species :pet/species
                                      :description :pet/description}))
            (map (partial resolve/query ast)))))

   :user/favorite-fellow-user
   (fn [env ast]
     (p/alet [get-user (get-in env [:apis :get-user])
              user (p/await (get-user {:name (:user/name env)}))]
       (resolve/resolve (assoc env :user/name (:favorite-fellow-user-name user))
                        ast
                        (chan))))})

#_(deftest resolve-works
  (async done
    (let [api-calls (atom [])
          ;; Note that the "backend" data uses different keys from the client.
          ;; The resolver must translate.
          users {{:name "nipponfarm"} {:favorite-color :color/blue
                                       :favorite-number 42
                                       :vehicle {:color :color/white
                                                 :make "Toyota"
                                                 :model "Hilux"}
                                       :pets [{:name "Milo"
                                               :species :pet-species/cat
                                               :description "orange tabby"}
                                              {:name "Otis"
                                               :species :pet-species/dog
                                               :description "pug"}]
                                       :favorite-fellow-user-name "jburnford"}
                 {:name "jburnford"} {:favorite-color :color/red
                                      :favorite-number 7}}
          user-pets {{:name "nipponfarm"} [{:name "Milo"
                                            :species :pet-species/cat
                                            :description "orange tabby"}
                                           {:name "Otis"
                                            :species :pet-species/dog
                                            :description "pug"}]}
          data-chan (resolve/resolve {:apis {:get-user
                                             (memoize
                                              (fn [params]
                                                (p/do*
                                                 (swap! api-calls conj [:get-user params])
                                                 (get users params))))

                                             :get-user-pets
                                             (memoize
                                              (fn [params]
                                                (p/do*
                                                 (swap! api-calls conj [:get-user-pets params])
                                                 (get user-pets params))))

                                             :do-something
                                             (fn [params]
                                               (p/do*
                                                (swap! api-calls conj [:do-something params])
                                                "Did something."))}
                                      :resolvers resolvers}
                                     '[(do/something {:very "important"})
                                       {(:root/user {:user/name "nipponfarm"})
                                        [:user/name
                                         :user/imaginary-friend
                                         :user/favorite-color
                                         :user/favorite-number
                                         {:user/vehicle [:vehicle/make
                                                         (:the-model {:< :vehicle/model})]}
                                         {:user/pets [:pet/name]}
                                         {:user/favorite-fellow-user [:user/name
                                                                      :user/favorite-color
                                                                      :user/favorite-number]}]}
                                       {(:jamie {:< :root/user :user/name "jburnford"})
                                        [:user/name
                                         :user/favorite-color]}]
                                     (chan))]
      (take!
       (async/into #{} data-chan)
       (fn [v]
         (is (= #{{'do/something "Did something."}
                  {:root/user {:user/name "nipponfarm"}}
                  {:root/user {:user/imaginary-friend nil}}
                  {:root/user {:user/favorite-color :color/blue}}
                  {:root/user {:user/favorite-number 42}}
                  {:root/user {:user/vehicle {:vehicle/make "Toyota"
                                              :the-model "Hilux"}}}
                  {:root/user {:user/pets [{:pet/name "Milo"}
                                           {:pet/name "Otis"}]}}
                  {:root/user {:user/favorite-fellow-user {:user/name "jburnford"}}}
                  {:root/user {:user/favorite-fellow-user {:user/favorite-color :color/red}}}
                  {:root/user {:user/favorite-fellow-user {:user/favorite-number 7}}}
                  {:jamie {:user/name "jburnford"}}
                  {:jamie {:user/favorite-color :color/red}}}
                v))
         (is (= [[:do-something {:very "important"}]
                 [:get-user {:name "nipponfarm"}]
                 [:get-user-pets {:name "nipponfarm"}]
                 [:get-user {:name "jburnford"}]]
                @api-calls))
         (done))))))
