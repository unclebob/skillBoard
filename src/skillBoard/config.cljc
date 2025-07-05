(ns skillBoard.config)

(def config (atom nil))

(defn load-config []
  (reset! config (read-string (slurp "private/config"))))

(def cols 59)
(def flights 19)

