(ns skillBoard.navigation)

(defn dist-and-bearing
  "Calculate distance in nautical miles and bearing between two lat-long points."
  [lat1 lon1 lat2 lon2]
  (if (or (nil? lat1) (nil? lon1) (nil? lat2) (nil? lon2))
    {:distance nil :bearing nil}
    (let [R 3440.1                                          ;; Earth's radius in nautical miles
          to-rad #(/ (* % Math/PI) 180)
          lat1 (to-rad lat1)
          lon1 (to-rad lon1)
          lat2 (to-rad lat2)
          lon2 (to-rad lon2)
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
       :bearing bearing})))

  ;; Example usage:
  ;; (haversine 40.7128 -74.0060 38.9072 -77.0369)  ; Example: NYC to DC

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T10:34:58.090833-05:00", :module-hash "1324684070", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "13784615"} {:id "defn/dist-and-bearing", :kind "defn", :line 3, :end-line 29, :hash "-1262143304"}]}
;; clj-mutate-manifest-end
