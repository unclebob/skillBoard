(ns skillBoard.adapters.web.server-spec
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.edn :as edn]
    [skillBoard.adapters.web.server :as server]
    [speclj.core :refer :all]))

(defn- url [base path]
  (str base path))

(describe "web server"
  (with server-info (server/start! 0))
  (with base (str "http://127.0.0.1:" (:port @server-info)))
  (after (server/stop! @server-info))

  (it "serves the board page"
    (let [response (http/get (url @base "/"))]
      (should= 200 (:status response))
      (should-contain "Skill Board" (:body response))))

  (it "stores the latest snapshot"
    (http/post (url @base "/snapshot")
               {:body (pr-str {:n 1})
                :content-type "application/edn"})
    (http/post (url @base "/snapshot")
               {:body (pr-str {:n 2})
                :content-type "application/edn"})
    (should= {:n 2} (:snapshot (server/board @server-info))))

  (it "rejects a snapshot that is not edn"
    (let [response (http/post (url @base "/snapshot")
                              {:body "{not edn"
                               :content-type "application/edn"
                               :throw-exceptions false})]
      (should= 400 (:status response))))

  (it "gives a poll command to the app once"
    (http/post (url @base "/command")
               {:body "{:key \"p\"}"
                :content-type "application/edn"})
    (let [first (json/read-str (:body (http/get (url @base "/command"))) :key-fn keyword)
          second (json/read-str (:body (http/get (url @base "/command"))) :key-fn keyword)]
      (should= true (:poll first))
      (should= false (:poll second))))

  (it "ignores a key other than p"
    (http/post (url @base "/command")
               {:body "{:key \"space\"}"
                :content-type "application/edn"})
    (let [command (json/read-str (:body (http/get (url @base "/command"))) :key-fn keyword)]
      (should= false (:poll command))))

  (it "accepts a keyword poll key"
    (http/post (url @base "/command")
               {:body "{:key :p}"
                :content-type "application/edn"})
    (let [command (json/read-str (:body (http/get (url @base "/command"))) :key-fn keyword)]
      (should= true (:poll command))))

  (it "ignores a command that is not edn"
    (let [response (http/post (url @base "/command")
                              {:body "{not edn"
                               :content-type "application/edn"
                               :throw-exceptions false})
          command (json/read-str (:body (http/get (url @base "/command"))) :key-fn keyword)]
      (should= 204 (:status response))
      (should= false (:poll command))))

  (it "serves the page assets"
    (doseq [[path type] [["/board.js" "text/javascript"]
                         ["/board.css" "text/css"]
                         ["/logo.png" "image/png"]
                         ["/fonts/skyfont.otf" "font/otf"]
                         ["/fonts/bahnschrift.ttf" "font/ttf"]
                         ["/fonts/dseg7.ttf" "font/ttf"]]]
      (let [response (http/get (url @base path) {:as :byte-array})]
        (should= 200 (:status response))
        (should-contain type (get-in response [:headers "Content-Type"])))))

  (it "defaults the board size"
    (let [view (json/read-str (:body (http/get (url @base "/board"))) :key-fn keyword)]
      (should= 24 (:rows view))
      (should= 1280 (:width view))
      (should= 720 (:height view))))

  (it "rejects an unusable board size"
    (let [response (http/get (url @base "/board")
                             {:query-params {:rows "0" :width "no" :height "-3"}})
          view (json/read-str (:body response) :key-fn keyword)]
      (should= 24 (:rows view))
      (should= 1280 (:width view))
      (should= 720 (:height view))))

  (it "serves the board view as json"
    (http/post (url @base "/snapshot")
               {:body (pr-str {:com-errors {:reservations 4 :adsb 0 :weather 0 :open-meteo-ok? true}})
                :content-type "application/edn"})
    (let [response (http/get (url @base "/board") {:query-params {:rows 8 :width 320 :height 240}})
          view (json/read-str (:body response) :key-fn keyword)]
      (should= 200 (:status response))
      (should-contain "application/json" (get-in response [:headers "Content-Type"]))
      (should= "FLIGHT OPERATIONS" (get-in view [:screens :flights :header]))
      (should= [255 0 0] (get-in view [:lights :reservations]))))

  (it "answers 404 for an unknown path"
    (let [response (http/get (url @base "/missing") {:throw-exceptions false})]
      (should= 404 (:status response)))))
