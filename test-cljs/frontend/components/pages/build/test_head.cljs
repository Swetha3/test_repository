(ns frontend.components.pages.build.test-head
  (:require [cljs.test :refer-macros [deftest is]]
            [frontend.components.pages.build.head :as head]))

(deftest maybe-project-linkify-works
  (with-redefs [frontend.config/github-endpoint (constantly "https://github.com")]
    (let [issue-mentioned "Merged #42"
          no-issue-mentioned "Merged 42 commits"
          bb-pr-mentioned "Merged in some/repo (pull request #42)"]
      (is (re-find #"<a href='https://github\.com/a/b/issues/42' target='_blank'>#42</a>"
                   (head/maybe-project-linkify issue-mentioned "github" "a/b")))
      (is (re-find #"<a href='https://bitbucket\.org/a/b/issues/42' target='_blank'>#42</a>"
                   (head/maybe-project-linkify issue-mentioned "bitbucket" "a/b")))

      (is (not (re-find #"<a href='https://github\.com/a/b/issues/42' target='_blank'>#42</a>"
                        (head/maybe-project-linkify no-issue-mentioned "github" "a/b"))))
      (is (not (re-find #"<a href='https://bitbucket\.org/a/b/issues/42' target='_blank'>#42</a>"
                        (head/maybe-project-linkify no-issue-mentioned "bitbucket" "a/b"))))

      (is (re-find #"<a href='https://bitbucket\.org/a/b/pull-requests/42' target='_blank'>pull request #42</a>"
                   (head/maybe-project-linkify bb-pr-mentioned "bitbucket" "a/b")))
      (is (not (re-find #"<a href='https://github\.com/a/b/pull-requests/42' target='_blank'>pull request #42</a>"
                        (head/maybe-project-linkify bb-pr-mentioned "github" "a/b")))))))
