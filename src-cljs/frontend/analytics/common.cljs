(ns frontend.analytics.common
  (:require [schema.core :as s]))

(def UserProperties
  {:ab-test-treatments {s/Keyword s/Keyword}
   :all_emails [s/Str]
   :basic_email_prefs s/Str
   :bitbucket_authorized s/Bool
   :created_at s/Str
   :in_beta_program s/Bool
   :login s/Str
   :name s/Str
   :num-projects-followed s/Int
   :selected_email s/Str
   :sign_in_count s/Int})
