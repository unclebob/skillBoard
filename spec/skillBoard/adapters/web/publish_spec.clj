(ns skillBoard.adapters.web.publish-spec
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [skillBoard.adapters.web.publish :as publish]
    [skillBoard.adapters.web.server :as server]
    [skillBoard.foundation.atoms :as atoms]
    [speclj.core :refer :all]))

(describe "publish"
  (with info (server/start! 0))
  (with port (:port @info))
  (after
    (server/stop! @info)
    (reset! publish/latest nil))

  (it "posts a changed snapshot and leaves an unchanged one"
    (should= {:n 1} (publish/exchange! @port ::publish/none {:n 1} (fn [])))
    (should= {:n 1} (:snapshot (server/board @info)))
    (should= {:n 1} (publish/exchange! @port {:n 1} {:n 1} (fn []))))

  (it "publishes the snapshot the board remembered"
    (publish/remember! {:n 4})
    (should= {:n 4} (publish/publish-once! @port publish/none))
    (should= {:n 4} (:snapshot (server/board @info))))

  (it "posts nothing before a snapshot is remembered"
    (reset! publish/latest nil)
    (should= ::kept (publish/publish-once! @port ::kept))
    (should-be-nil (:snapshot (server/board @info))))

  (it "keeps the previous snapshot when the server is down"
    (publish/remember! {:n 9})
    (let [down (server/start! 0)
          closed (:port down)]
      (server/stop! down)
      (should= ::kept (publish/publish-once! closed ::kept))
      (should-be-nil (:snapshot (server/board @info)))))

  (it "sets the poll key when the page asked for a poll"
    (http/post (str "http://127.0.0.1:" @port "/command")
               {:body "{:key \"p\"}"
                :content-type "application/edn"})
    (let [polls (atom 0)
          before @atoms/poll-key]
      (publish/exchange! @port {:n 1} {:n 1} #(swap! polls inc))
      (should= 1 @polls)
      (should= false (:poll (json/read-str
                              (:body (http/get (str "http://127.0.0.1:" @port "/command")))
                              :key-fn keyword)))
      (reset! atoms/poll-key before))))
