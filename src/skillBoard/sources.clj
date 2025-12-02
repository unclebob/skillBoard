(ns skillBoard.sources)

(defmulti get-reservations (fn [source] (:type source)))
(defmulti get-flights (fn [source] (:type source)))
(defmulti get-metar (fn [source _icao] (:type source)))
(defmulti get-taf (fn [source _icao] (:type source)))
(defmulti get-adsb-by-tail-numbers (fn [source _tail-numbers] (:type source)))
(defmulti get-aircraft (fn [source] (:type source)))
