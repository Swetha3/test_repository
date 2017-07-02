(ns frontend.data.projects)

(defn non-code-identity-table-data
  []
  [{:reponame "circleci-demo-ruby-rails"
    :parallel 4
    :follower-count 30}
   {:reponame "circleci-demo-java-spring"
    :parallel 6
    :follower-count 50}
   {:reponame "circleci-demo-python-django"
    :parallel 5
    :follower-count 20}
   {:reponame "circleci-demo-javascript-express"
    :parallel 3
    :follower-count 25}
   {:reponame "circleci-demo-go"
    :parallel 5
    :follower-count 50}
   {:reponame "circleci-demo-docker"
    :parallel 10
    :follower-count 50}])
