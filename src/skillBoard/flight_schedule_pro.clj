(ns skillBoard.flight-schedule-pro
  (:require
    [clojure.string]
    [clojure.string :as str]
    [java-time.api :as time]
    [skillBoard.time-util :as time-util]))

(defn unpack-reservations [{:keys [items]}]
  (if (empty? items)
    []
    (for [reservation items]
      {:reservation-id (:reservationId reservation)
       :tail-number (:tailNumber (:aircraft reservation))
       :activity-type (:name (:activityType reservation))
       :start-time (time-util/parse-time (:startTime reservation))
       :pilot-name (when-let [pilot (first (:pilots reservation))]
                     [(:firstName pilot)
                      (:lastName pilot)])
       :instructor-name (when-let [instructor (:instructor reservation)]
                          [(:firstName instructor) (:lastName instructor)])
       :reservation-status (:name (:reservationStatus reservation))
       :checked-in-on (when-let [checked-in (:checkedInOn reservation)]
                        (time-util/parse-time checked-in))
       :checked-out-on (when-let [checked-out (:checkedOutOn reservation)]
                         (time-util/parse-time checked-out))})))

(defn unpack-flights [{:keys [items]}]
  (if (empty? items)
    []
    (apply hash-map
           (flatten
             (for [flight items]
               [(:reservationId flight)
                {:reservation-id (:reservationId flight)
                 :checked-out-on (when-let [checked-out-on (:checkedOutOn flight)]
                                   (time-util/parse-time checked-out-on))
                 :checked-in-on (when-let [checked-in-on (:checkedInOn flight)]
                                  (time-util/parse-time checked-in-on))}])))))



(defn remove-superceded-reservations [reservations]
  (let [co-tails (set (map :tail-number (filter #(some? (:co %)) reservations)))]
    (loop [reservations reservations
           co-tails co-tails
           filtered-reservations []]
      (if (empty? reservations)
        filtered-reservations
        (let [res (first reservations)
              tail-number (:tail-number res)]
          (if (co-tails tail-number)
            (if (some? (:co res))
              (recur (rest reservations)
                     (disj co-tails tail-number)
                     (conj filtered-reservations res))
              (recur (rest reservations)
                     co-tails
                     filtered-reservations))
            (recur (rest reservations)
                   co-tails
                   (conj filtered-reservations res))))))))

(defn filter-active-reservations [reservations flights]
  (let [now (time/local-date-time)]
    (for [res reservations
          :let [res-id (:reservation-id res)
                flight (get flights res-id)
                activity (:activity-type res)
                res-checked-in (:checked-in-on res)
                flight-checked-in (:checked-in-on flight)
                ci (or res-checked-in flight-checked-in)
                co (or (:checked-out-on res) (:checked-out-on flight))]
          :when (and (or (str/starts-with? activity "Flight")
                         (= activity "New Customer"))
                     (nil? ci)
                     (time/after? (:start-time res) (time/minus now (time/hours 6)))
                     )]
      (assoc res :co co))))

(defn sort-and-filter-reservations [reservations flights]
  (let [sorted-reservations (sort #(time/before? (:start-time %1) (:start-time %2)) reservations)
        active-reservations (filter-active-reservations sorted-reservations flights)]
    (remove-superceded-reservations active-reservations)))
