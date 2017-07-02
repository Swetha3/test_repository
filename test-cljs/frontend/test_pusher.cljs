(ns frontend.test-pusher
  (:require [frontend.pusher :as pusher]
            [frontend.utils.seq :refer (submap?)]
            [cljs.test :refer-macros [is deftest testing]]))

(deftest pusher-config-works-with-default
  (let [config {:key "app-key"}]
    (is (= {:encrypted true
            :authEndpoint "/auth/pusher"}
           (-> (pusher/pusher-object-config config)
               (dissoc :auth))))))

(deftest pusher-config-works-with-custom-endpoints
  (testing "unencrypted end points"
    (let [config {:key "app-key"
                  :ws_endpoint "ws://localhost:4321"}]
      (is (submap? {:encrypted false
                    :wsHost "localhost"
                    :wsPort 4321
                    :authEndpoint "/auth/pusher"}
                   (pusher/pusher-object-config config)))))
  (testing "encrypted end points"
    (let [config {:key "app-key"
                  :ws_endpoint "wss://localhost:4321"}]
      (is (submap? {:encrypted true
                    :wsHost "localhost"
                    :wssPort 4321
                    :authEndpoint "/auth/pusher"}
                   (pusher/pusher-object-config config))))))
