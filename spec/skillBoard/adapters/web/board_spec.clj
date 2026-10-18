(ns skillBoard.adapters.web.board-spec
  (:require
    [skillBoard.adapters.web.board :as board]
    [speclj.core :refer :all]))

(describe "board"
  (it "starts empty"
    (should= {:snapshot nil :poll? false} (board/empty-board)))

  (it "keeps the latest snapshot"
    (let [stored (-> (board/empty-board)
                     (board/store-snapshot {:n 1})
                     (board/store-snapshot {:n 2}))]
      (should= {:n 2} (:snapshot stored))))

  (it "remembers one poll until it is taken"
    (let [waiting (board/request-poll (board/empty-board))
          taken (board/take-poll waiting)]
      (should= true (:poll? waiting))
      (should= true (:poll? taken))
      (should= false (:poll? (:board taken)))
      (should= false (:poll? (board/take-poll (:board taken))))))

  (it "stores a snapshot without clearing a waiting poll"
    (let [stored (-> (board/empty-board)
                     board/request-poll
                     (board/store-snapshot {:n 1}))]
      (should= true (:poll? stored))
      (should= {:n 1} (:snapshot stored)))))
