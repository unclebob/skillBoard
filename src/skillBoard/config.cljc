(ns skillBoard.config)

(def config (atom nil))

(defn load-config []
  (reset! config (read-string (slurp "private/config"))))
