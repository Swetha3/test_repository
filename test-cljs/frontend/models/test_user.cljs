(ns frontend.models.test-user
  (:require [cljs.test :refer-macros [is deftest testing]]
            [frontend.models.user :as user]))

(deftest missing-scopes-works
  (let [github-user (fn [scopes] {:github_oauth_scopes scopes})
        revoked-user (github-user nil)
        public-user (github-user ["user:email" "public_repo" "read:org"])
        incomplete-public-user (github-user ["user:email"])
        private-user (github-user ["user:email" "repo"])
        private-user-with-extras (github-user ["user:email" "repo" "extra1" "extra2"])
        ;; some older users may have full user scope instead of user:email
        legacy-user (github-user ["user" "repo"])
        legacy-public-user (github-user ["user" "public_repo" "read:org"])
        incomplete-legacy-user (github-user ["user"])]

    (testing "user with revoked scopes should be missing all public scopes"
      (is (= #{"read:org" "user:email" "public_repo"}
             (user/missing-scopes revoked-user))))

    (testing "users with complete scopes should be missing no scopes"
      (is (= #{}
             (user/missing-scopes public-user)))
      (is (= #{}
             (user/missing-scopes private-user)))
      (is (= #{}
             (user/missing-scopes private-user-with-extras))))

    (testing "user with incomplete public scopes should be missing remaining public scopes"
      (is (= #{"read:org" "public_repo"}
             (user/missing-scopes incomplete-public-user))))

    (testing "users with full user scope should not be missing user:email scope"
      (is (= #{}
             (user/missing-scopes legacy-user)))
      (is (= #{}
             (user/missing-scopes legacy-public-user)))
      (is (= #{"public_repo"}
             (user/missing-scopes incomplete-legacy-user))))))


(deftest primary-email-works
  (let [selected-email "selected@email.com"
        all-emails ["all@emails.com" "last@emails.com"]
        user {:selected_email selected-email
              :all_emails all-emails}]

   (testing "if they have a :selected-email we use that"
     ;; The user should always have a :selected_email since we insert the primary-email into
     ;; that key in the api layer.
    (is (= selected-email (user/primary-email user))))

   (testing "otherwise its nil"
     (is (= nil (user/primary-email (-> user
                                        (dissoc :selected_email))))))))
