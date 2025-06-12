(ns skillBoard.sources)

(defmulti get-reservations (fn [source _operator-id] (:type source)))
(defmulti get-flights (fn [source _operator-id] (:type source)))
(defmulti get-metar (fn [source _icao] (:type source)))