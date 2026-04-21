(ns skillBoard.radar-cape
  (:require
    [clojure.set :as set]
    [java-time.api :as time]
    ))

(defn update-with-adsb [reservations adsbs]
  (let [tails (set (keys adsbs))]
    (loop [tails tails
           reservations reservations
           updated-reservations []]
      (if (empty? reservations)
        updated-reservations
        (let [res (first reservations)
              tail-number (:tail-number res)
              adsb (get adsbs tail-number)]
          (if (and (:co res) (tails tail-number))
            (recur (disj tails tail-number)
                   (rest reservations)
                   (conj updated-reservations
                         (assoc res :adsb? true
                                    :altitude (:altg adsb)
                                    :lat-lon [(:lat adsb) (:lon adsb)]
                                    :ground-speed (:spd adsb)
                                    :on-ground? (or (= "g" (:gda adsb))
                                                    (= "G" (:gda adsb))))))
            (recur tails
                   (rest reservations)
                   (conj updated-reservations res))))))))

(defn get-now [] (time/local-date-time))

(defn include-unscheduled-flights [reservations adsbs]
  (let [tails-with-co (set (map :tail-number (filter #(some? (:co %)) reservations)))
        flying-tails (set (map :reg (vals adsbs)))
        unscheduled-tails (set/difference flying-tails tails-with-co)
        unscheduled-flights (for [tail unscheduled-tails
                                  :let [adsb (get adsbs tail)]
                                  :when (some? adsb)]
                              {
                               :adsb? true
                               :tail-number tail
                               :altitude (:altg adsb)
                               :lat-lon [(:lat adsb) (:lon adsb)]
                               :ground-speed (:spd adsb)
                               :start-time (get-now)
                               :unscheduled? true
                               :on-ground? (or (= "g" (:gda adsb))
                                               (= "G" (:gda adsb)))})
        inclusive-reservations (concat reservations unscheduled-flights)]
    (sort #(time/before? (:start-time %1) (:start-time %2)) inclusive-reservations)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T10:29:37.353131-05:00", :module-hash "-539152502", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "720657920"} {:id "defn/update-with-adsb", :kind "defn", :line 7, :end-line 29, :hash "-1498834537"} {:id "defn/get-now", :kind "defn", :line 31, :end-line 31, :hash "2008214877"} {:id "defn/include-unscheduled-flights", :kind "defn", :line 33, :end-line 51, :hash "-1009314584"}]}
;; clj-mutate-manifest-end
