(ns frontend.proxy-test
  (:require [clojure.test :refer :all]
            [frontend.proxy :as proxy]))

(deftest strip-secure-cookie-works
  (are [expected header-val] (= expected (proxy/strip-secure-cookie header-val))

       ;; non secure are ok
       "a=b" "a=b"
       ["a=b" "c=d"] ["a=b" "c=d"]

       ;; secure are ok
       "a=b" "a=b;Secure"
       "a=b" "a=b;  Secure"
       ["a=b" "c=d"] ["a=b; Secure" "c=d"]

       ;;other flags are untouched
       "a=b; HttpOnly" "a=b; HttpOnly; Secure"
       "a=b; HttpOnly" "a=b; Secure; HttpOnly" ))
