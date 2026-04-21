(ns skillBoard.flight-schedule-pro
  (:require
    [clojure.string]
    [clojure.string :as str]
    [java-time.api :as time]
    [skillBoard.core-utils :as core-utils]
    [skillBoard.time-util :as time-util]))

(defn- log-nil [field reservation]
  (let [res-id (:reservationId reservation)
        tail (:tailNumber (:aircraft reservation))
        start (:startTime reservation)]
    (core-utils/log :error
      (str "Reservation has nil " field
           " [id=" res-id " tail=" tail " start=" start "]"))))

(defn unpack-reservations [{:keys [items]}]
  (if (empty? items)
    []
    (for [reservation items]
      (let [res-id (:reservationId reservation)
            _ (when (nil? res-id) (log-nil "reservationId" reservation))
            activity (:name (:activityType reservation))
            _ (when (nil? activity) (log-nil "activityType" reservation))
            start-time (time-util/parse-time (:startTime reservation))
            _ (when (nil? start-time) (log-nil "startTime" reservation))]
        {:reservation-id res-id
         :tail-number (:tailNumber (:aircraft reservation))
         :activity-type activity
         :start-time start-time
         :pilot-name (when-let [pilot (first (:pilots reservation))]
                       [(:firstName pilot)
                        (:lastName pilot)])
         :instructor-name (when-let [instructor (:instructor reservation)]
                            [(:firstName instructor) (:lastName instructor)])
         :checked-in-on (when-let [checked-in (:checkedInOn reservation)]
                          (time-util/parse-time checked-in))
         :checked-out-on (when-let [checked-out (:checkedOutOn reservation)]
                           (time-util/parse-time checked-out))}))))

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T10:36:55.002058-05:00", :module-hash "-915679060", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-921036285"} {:id "defn-/log-nil", :kind "defn-", :line 9, :end-line 15, :hash "954393681"} {:id "defn/unpack-reservations", :kind "defn", :line 17, :end-line 39, :hash "479340997"} {:id "defn/unpack-flights", :kind "defn", :line 41, :end-line 52, :hash "2141357073"} {:id "defn/remove-superceded-reservations", :kind "defn", :line 56, :end-line 75, :hash "588118744"} {:id "defn/filter-active-reservations", :kind "defn", :line 77, :end-line 92, :hash "-34988166"} {:id "defn/sort-and-filter-reservations", :kind "defn", :line 94, :end-line 97, :hash "-1375307845"}]}
;; clj-mutate-manifest-end
