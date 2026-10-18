(ns skillBoard.adapters.web.process-spec
  (:require
    [skillBoard.adapters.web.board :as board]
    [skillBoard.adapters.web.process :as process]
    [skillBoard.adapters.web.server :as server]
    [speclj.core :refer :all]))

(describe "server process"
  (it "starts a new java process on the server main"
    (let [command (process/server-command "/library/java" "/app/classes")]
      (should= "/library/java/bin/java" (first command))
      (should-contain "-Djava.awt.headless=true" command)
      (should-contain "/app/classes" command)
      (should-contain "clojure.main" command)
      (should-contain "skillBoard.adapters.web.server" command)
      (should-not-contain "sh" command)))

  (it "starts only when the port is closed"
    (should= true (process/start-needed? false))
    (should= false (process/start-needed? true)))

  (it "uses the board port"
    (should= board/port process/port))

  (it "knows whether the port is open"
    (let [info (server/start! 0)
          open (:port info)]
      (should= true (process/listening? open))
      (server/stop! info)
      (should= false (process/listening? open)))))
