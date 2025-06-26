(ns skillBoard.navigation
  (:require [clojure.math :as math]))

(defn dist-and-bearing
  "Calculate distance in nautical miles and bearing between two lat-long points."
  [tower-lat tower-lon aircraft-lat aircraft-lon]
  (let [R 3440.1 ;; Earth's radius in nautical miles
        to-rad #(/ (* % Math/PI) 180)
        lat1 (to-rad tower-lat)
        lon1 (to-rad tower-lon)
        lat2 (to-rad aircraft-lat)
        lon2 (to-rad aircraft-lon)
        dlat (- lat2 lat1)
        dlon (- lon2 lon1)
        ;; Haversine formula for distance
        a (+ (* (Math/sin (/ dlat 2)) (Math/sin (/ dlat 2)))
             (* (Math/cos lat1) (Math/cos lat2)
                (Math/sin (/ dlon 2)) (Math/sin (/ dlon 2))))
        c (* 2 (Math/atan2 (Math/sqrt a) (Math/sqrt (- 1 a))))
        distance (* R c)
        ;; Bearing calculation
        y (* (Math/sin dlon) (Math/cos lat2))
        x (- (* (Math/cos lat1) (Math/sin lat2))
             (* (Math/sin lat1) (Math/cos lat2) (Math/cos dlon)))
        bearing-raw (Math/atan2 y x)
        bearing (mod (+ (* (/ bearing-raw Math/PI) 180) 360) 360)]
    {:distance distance
     :bearing bearing}))

;; Example usage:
;; (haversine 40.7128 -74.0060 38.9072 -77.0369)  ; Example: NYC to DC