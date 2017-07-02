(ns frontend.stefon)

(def hosted-scripts [{:path "js/hosted/intercom2.js"
                      :url "https://widget.intercom.io/widget/vnk4oztr"}
                     {:path "js/hosted/pusher.min.js"
                      :url "https://js.pusher.com/2.2/pusher.min.js"}
                     {:path "js/hosted/elevio.js"
                      :url "https://static.elev.io/js/v3.js"}
                     {:path "js/hosted/optimizely.js"
                      :url "https://cdn.optimizely.com/js/8188917468.js"}
                     {:path "js/hosted/segment-mAJ9W2SwLHgmJtFkpaXWCbwEeNk9D8CZ.js"
                      :url "https://cdn.segment.com/analytics.js/v1/mAJ9W2SwLHgmJtFkpaXWCbwEeNk9D8CZ/analytics.min.js"}
                     {:path "js/hosted/segment-AbgkrgN4cbRhAVEwlzMkHbwvrXnxHh35.js"
                      :url "https://cdn.segment.com/analytics.js/v1/AbgkrgN4cbRhAVEwlzMkHbwvrXnxHh35/analytics.min.js"}])

(def asset-roots ["resources/assets"])

(def precompiles
  (concat ["js/om-dev.js.stefon"
           "js/om-production.js.stefon"
           "js/om-whitespace.js.stefon"
           "css/app.css"
           "img/logo.png"
           "img/status-logos/success.svg"
           "img/status-logos/failure.svg"
           "img/status-logos/logo.svg"]
          (map :path hosted-scripts)))
