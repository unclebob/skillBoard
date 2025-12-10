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

