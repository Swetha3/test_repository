(ns frontend.controllers.test-api
  (:require [frontend.controllers.api :as api]
            [frontend.controllers.ws :as ws]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [cljs.test :refer-macros [deftest is testing]]))

(deftest follow-repo-event
  (testing "selected repo is marked as being followed"
    (let [repo1      {:following false :name "test1" :username "blah"}
          repo2      {:following false :name "test2" :username "blah"}
          context    {:name "test2"
                      :login "blah"}
          state      (assoc-in {} state/repos-path [repo1 repo2])

          new-state  (api/api-event nil :follow-repo :success {:context context} state)

          repo2-following? (get-in new-state (conj (state/repo-path 1) :following))
          repo1-following? (get-in new-state (conj (state/repo-path 0) :following))]
    (is repo2-following?)
    (is (not repo1-following?)))))

(deftest filter-piggieback
  (testing "piggiebacked orgs are removed from list"
    (let [orgs [{:login "foo1"
                 :avatar_url "http://localhost/foo1.png"
                 :org true
                 :piggieback_orgs ["piggie1" "piggie2"]}
                {:login "piggie1"
                 :avatar_url "http://localhost/piggie1.png"
                 :org true
                 :piggieback_orgs []}
                {:login "piggie2"
                 :avatar_url "http://localhost/piggie2.png"
                 :org true
                 :piggieback_orgs []}]
          filtered-orgs (api/filter-piggieback orgs)]
      (is (= filtered-orgs (take 1 orgs))))))

(deftest api-event-project-envvar-success
  (let [dummy-project-name "a project name"
        dummy-state {:unique-state-value "very unique"
                     :project-settings-project-name dummy-project-name}
        test-fn (fn [args state]
                  (api/api-event :_target
                                 :project-envvar
                                 :success
                                 args
                                 state))]
    (testing "arbitrary response, project names don't match, state is returned"
      (is (= dummy-state
             (test-fn {:resp 314159
                       :context {:project-name "nope"}}
                      dummy-state))))
    (testing "response is empty, project names match, envvars should be empty"
      (doseq [empty-seq [[] '() nil]]
        (is (= (assoc-in dummy-state
                         state/project-envvars-path
                         {})
               (test-fn {:resp empty-seq
                         :context {:project-name dummy-project-name}}
                        dummy-state)))))
    (testing "response has many entries, project names match, envvars should be a populated map"
      (is (= (assoc-in dummy-state
                       state/project-envvars-path
                       {"foo" "xxxxcvbn"
                        "test" "xxxx456"})
             (test-fn {:resp [{:name "foo" :value "xxxxcvbn"}
                              {:name "test" :value "xxxx456"}]
                       :context {:project-name dummy-project-name}}
                      dummy-state))))))

(deftest api-event-create-env-var-success
  (let [dummy-project-id "http://a.url"
        dummy-state (assoc-in {:unique-state-value "very unique"}
                              (conj state/project-path :vcs_url)
                              dummy-project-id)
        test-fn (fn [args state]
                  (api/api-event :_target
                                 :create-env-var
                                 :success
                                 args
                                 state))]
    (testing "arbitrary response, project ids don't match, state is returned"
      (is (= dummy-state
             (test-fn {:resp 314159
                       :context {:project-id "nope"}}
                      dummy-state))))
    (testing "single var, empty state, project names match, envvars should be singly populated"
      (is (= (assoc
              (assoc-in dummy-state
                        state/project-envvars-path
                        {"a" 1})
              :inputs {:new-env-var-name "" :new-env-var-value ""}
              :flash-notification {:number 1 :message "Environment variable added successfully."})
             (test-fn {:resp {:name "a" :value 1}
                       :context {:project-id dummy-project-id}}
                      dummy-state))))
    (testing "response has many entries, project names match, envvars should be a populated map"
      (is (= (assoc
              (assoc-in dummy-state
                        state/project-envvars-path
                        {"a" 1})
              :inputs {:new-env-var-name "" :new-env-var-value ""}
              :flash-notification {:number 1 :message "Environment variable added successfully."})
             (test-fn {:resp {:name "a" :value 1}
                       :context {:project-id dummy-project-id}}
                      (assoc-in dummy-state
                                state/project-envvars-path
                                {"a" 2})))))))
