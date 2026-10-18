(ns skillBoard.adapters.web.board)

(def port 12005)

(defn empty-board []
  {:snapshot nil
   :poll? false})

(defn store-snapshot [board snapshot]
  (assoc board :snapshot snapshot))

(defn request-poll [board]
  (assoc board :poll? true))

(defn take-poll [board]
  {:board (assoc board :poll? false)
   :poll? (true? (:poll? board))})
