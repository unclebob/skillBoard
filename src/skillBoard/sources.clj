(ns skillBoard.sources)

(defmulti get-reservations (fn [source] (:type source)))
(defmulti get-flights (fn [source] (:type source)))
(defmulti get-metar (fn [source _icao] (:type source)))