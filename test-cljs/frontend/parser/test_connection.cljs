(ns frontend.parser.test-connection
  (:require [bodhi.core :as bodhi]
            [bodhi.param-indexing :as param-indexing]
            [cljs.test :refer-macros [is testing]]
            [frontend.parser.connection :as connection]
            [om.next :as om-next])
  (:require-macros [devcards.core :as dc :refer [deftest]]))

(deftest read-works
  (let [parser (om-next/parser
                {:read (bodhi/read-fn
                        (-> bodhi/basic-read
                            ;; Throw in param-indexing to prove that we remove
                            ;; the connection-related params before passing the
                            ;; read along.
                            param-indexing/read
                            connection/read))})
        state (atom {:root/pets-connection {:connection/total-count 5
                                            :connection/edges [{:edge/node {:pet/name "Milo"
                                                                            :pet/species :pet-species/cat
                                                                            :pet/description "orange tabby"}}
                                                               {:edge/node {:pet/name "Otis"
                                                                            :pet/species :pet-species/dog
                                                                            :pet/description "pug"}}
                                                               {:edge/node {:pet/name "Chance"
                                                                            :pet/species :pet-species/dog
                                                                            :pet/description "American bulldog"}}
                                                               {:edge/node {:pet/name "Shadow"
                                                                            :pet/species :pet-species/dog
                                                                            :pet/description "golden retriever"}}
                                                               {:edge/node {:pet/name "Sassy"
                                                                            :pet/species :pet-species/cat
                                                                            :pet/description "Himalayan"}}]}})]

    (testing "Locally"
      (testing "without an offset or limit, returns all edges."
        (is (= {:root/pets-connection {:connection/total-count 5
                                       :connection/edges [{:edge/node {:pet/name "Milo"}}
                                                          {:edge/node {:pet/name "Otis"}}
                                                          {:edge/node {:pet/name "Chance"}}
                                                          {:edge/node {:pet/name "Shadow"}}
                                                          {:edge/node {:pet/name "Sassy"}}]}}
               (parser {:state state} '[{:root/pets-connection
                                         [:connection/total-count
                                          {:connection/edges [{:edge/node [:pet/name]}]}]}] nil))))

      (testing "with a offset and limit, returns the slice specified."
        (is (= {:root/pets-connection {:connection/total-count 5
                                       :connection/offset 1
                                       :connection/limit 2
                                       :connection/edges [{:edge/node {:pet/name "Otis"}}
                                                          {:edge/node {:pet/name "Chance"}}]}}
               (parser {:state state} '[{(:root/pets-connection {:connection/offset 1 :connection/limit 2})
                                         [:connection/total-count
                                          :connection/offset
                                          :connection/limit
                                          {:connection/edges [{:edge/node [:pet/name]}]}]}] nil))))

      (testing "with a limit which reaches past the end of the collection, returns as much as exists."
        (is (= {:root/pets-connection {:connection/total-count 5
                                       :connection/edges [{:edge/node {:pet/name "Sassy"}}]}}
               (parser {:state state} '[{(:root/pets-connection {:connection/offset 4 :connection/limit 2})
                                         [:connection/total-count
                                          {:connection/edges [{:edge/node [:pet/name]}]}]}] nil))))

      (testing "with an offset which starts past the end of the collection, returns no edges."
        (is (= {:root/pets-connection {:connection/total-count 5
                                       :connection/edges []}}
               (parser {:state state} '[{(:root/pets-connection {:connection/offset 6 :connection/limit 2})
                                         [:connection/total-count
                                          {:connection/edges [{:edge/node [:pet/name]}]}]}] nil))))

      (testing "with only an offset or a limit, throws."
        (is (thrown? js/Error (parser {:state state} '[{(:root/pets-connection {:connection/offset 4})
                                                        [:connection/total-count
                                                         {:connection/edges [{:edge/node [:pet/name]}]}]}] nil)))
        (is (thrown? js/Error (parser {:state state} '[{(:root/pets-connection {:connection/limit 2})
                                                        [:connection/total-count
                                                         {:connection/edges [{:edge/node [:pet/name]}]}]}] nil)))))

    (testing "Remotely"
      (testing "strips out :connection/offset and :connection/limit props, which are local concerns"
        (is (= (om-next/query->ast
                '[{(:root/pets-connection {:connection/offset 1 :connection/limit 2})
                   [:connection/total-count
                    {:connection/edges [{:edge/node [:pet/name]}]}]}])
               (om-next/query->ast
                (parser {:state state} '[{(:root/pets-connection {:connection/offset 1 :connection/limit 2})
                                          [:connection/total-count
                                           :connection/offset
                                           :connection/limit
                                           {:connection/edges [{:edge/node [:pet/name]}]}]}]
                        :remote))))))))


(deftest merge-works
  (let [merge (bodhi/merge-fn
               (-> bodhi/basic-merge
                   ;; Throw in param-indexing to prove that we remove
                   ;; the connection-related params before passing the
                   ;; merge along.
                   param-indexing/merge
                   connection/merge))]
    (testing "Leaves other slots nil"
      (let [state {}
            novelty {:root/pets-connection {:connection/total-count 5
                                            :connection/edges [{:edge/node {:pet/name "Otis"}}
                                                               {:edge/node {:pet/name "Chance"}}]}}
            query '[{(:root/pets-connection {:connection/offset 1 :connection/limit 2})
                     [:connection/total-count
                      :connection/offset
                      :connection/limit
                      {:connection/edges [{:edge/node [:pet/name]}]}]}]]
        (is (= {:keys #{}
                :next {:root/pets-connection {:connection/total-count 5
                                              :connection/edges [nil
                                                                 {:edge/node {:pet/name "Otis"}}
                                                                 {:edge/node {:pet/name "Chance"}}
                                                                 nil
                                                                 nil]}}}
               (merge {} state novelty query)))))

    (testing "Overwrites the correct slots"
      (let [state {:root/pets-connection {:connection/total-count 5
                                          :connection/edges [nil
                                                             {:edge/node {:pet/name "Otis"}}
                                                             {:edge/node {:pet/name "Chance"}}
                                                             nil
                                                             nil]}}
            novelty {:root/pets-connection {:connection/total-count 5
                                            :connection/edges [{:edge/node {:pet/name "Milo"}}
                                                               {:edge/node {:pet/name "Otis"}}]}}
            query '[{(:root/pets-connection {:connection/offset 0 :connection/limit 2})
                     [:connection/total-count
                      :connection/offset
                      :connection/limit
                      {:connection/edges [{:edge/node [:pet/name]}]}]}]]
        (is (= {:keys #{}
                :next {:root/pets-connection {:connection/total-count 5
                                              :connection/edges [{:edge/node {:pet/name "Milo"}}
                                                                 {:edge/node {:pet/name "Otis"}}
                                                                 {:edge/node {:pet/name "Chance"}}
                                                                 nil
                                                                 nil]}}}
               (merge {} state novelty query)))))

    (testing "Shortens the edge vector when the total count goes down"
      (let [state {:root/pets-connection {:connection/total-count 5
                                          :connection/edges [nil
                                                             {:edge/node {:pet/name "Otis"}}
                                                             {:edge/node {:pet/name "Chance"}}
                                                             nil
                                                             nil]}}
            novelty {:root/pets-connection {:connection/total-count 3
                                            :connection/edges [{:edge/node {:pet/name "Milo"}}
                                                               {:edge/node {:pet/name "Otis"}}]}}
            query '[{(:root/pets-connection {:connection/offset 0 :connection/limit 2})
                     [:connection/total-count
                      :connection/offset
                      :connection/limit
                      {:connection/edges [{:edge/node [:pet/name]}]}]}]]
        (is (= {:keys #{}
                :next {:root/pets-connection {:connection/total-count 3
                                              :connection/edges [{:edge/node {:pet/name "Milo"}}
                                                                 {:edge/node {:pet/name "Otis"}}
                                                                 {:edge/node {:pet/name "Chance"}}]}}}
               (merge {} state novelty query)))))

    (testing "Leaves the state alone when there's no novelty on this key"
      (let [state {:root/pets-connection {:connection/total-count 5
                                          :connection/edges [nil
                                                             {:edge/node {:pet/name "Otis"}}
                                                             {:edge/node {:pet/name "Chance"}}
                                                             nil
                                                             nil]}}
            novelty {:only 1 :other 2 :data 2}
            query '[{(:root/pets-connection {:connection/offset 0 :connection/limit 2})
                     [:connection/total-count
                      :connection/offset
                      :connection/limit
                      {:connection/edges [{:edge/node [:pet/name]}]}]}]]
        (is (= {:keys #{}
                :next {:root/pets-connection {:connection/total-count 5
                                              :connection/edges [nil
                                                                 {:edge/node {:pet/name "Otis"}}
                                                                 {:edge/node {:pet/name "Chance"}}
                                                                 nil
                                                                 nil]}}}
               (merge {} state novelty query)))))

    (testing "Recurses on nodes"
      (let [state {}
            novelty {:root/pets-connection {:connection/total-count 5
                                            :connection/edges [{:edge/node {:pet/name "Otis"
                                                                            :pet/species :pet-species/dog
                                                                            :pet/description "pug"}}
                                                               {:edge/node {:pet/name "Chance"
                                                                            :pet/species :pet-species/dog
                                                                            :pet/description "American bulldog"}}]}}
            query '[{(:root/pets-connection {:connection/offset 1 :connection/limit 2})
                     [:connection/total-count
                      :connection/offset
                      :connection/limit
                      {:connection/edges [{:edge/node [:pet/name]}]}]}]]
        (is (= {:keys #{}
                :next {:root/pets-connection {:connection/total-count 5
                                              :connection/edges [nil
                                                                 {:edge/node {:pet/name "Otis"}}
                                                                 {:edge/node {:pet/name "Chance"}}
                                                                 nil
                                                                 nil]}}}
               (merge {} state novelty query)))))))
