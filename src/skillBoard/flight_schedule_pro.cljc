(ns skillBoard.flight-schedule-pro
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.string :as string]
    [clojure.string :as str]
    [java-time.api :as time]
    [skillBoard.config :as config]
    [skillBoard.sources :as sources]
    ))

(def epoch-str "1753-01-01T00:00:00")
(def epoch (time/local-date-time epoch-str))

(defn local-to-utc [local-time]
  (-> local-time
      (time/zoned-date-time (time/local-date) (time/zone-id (:time-zone @config/config)))
      (.withZoneSameInstant (time/zone-id "UTC"))
      (time/local-time)))

(defn parse-time [time-str]
  (cond
    (or (empty? time-str) (string/starts-with? time-str epoch-str))
    nil

    (= (count time-str) 22)
    (time/local-date-time "yyyy-MM-dd'T'HH:mm:ss.SS" time-str)

    :else
    (time/local-date-time "yyyy-MM-dd'T'HH:mm:ss" time-str)))

(defn format-time [time]
  (time/format "yyyy-MM-dd'T'HH:mm:ss.SS" time))

(defn get-HHmm [time]
  (time/format "HH:mm" time))

(defn unpack-reservations [{:keys [items]}]
  (if (empty? items)
    []
    (for [reservation items]
      {:reservation-id (:reservationId reservation)
       :tail-number (:tailNumber (:aircraft reservation))
       :activity-type (:name (:activityType reservation))
       :start-time (parse-time (:startTime reservation))
       :pilot-name (when-let [pilot (first (:pilots reservation))]
                     [(:firstName pilot)
                      (:lastName pilot)])
       :instructor-name (when-let [instructor (:instructor reservation)]
                          [(:firstName instructor) (:lastName instructor)])
       :reservation-status (:name (:reservationStatus reservation))
       :checked-in-on (when-let [checked-in (:checkedInOn reservation)]
                        (parse-time checked-in))
       :checked-out-on (when-let [checked-out (:checkedOutOn reservation)]
                         (parse-time checked-out))})))

(defn unpack-flights [{:keys [items]}]
  (if (empty? items)
    []
    (apply hash-map
           (flatten
             (for [flight items]
               [(:reservationId flight)
                {:reservation-id (:reservationId flight)
                 :checked-out-on (when-let [checked-out-on (:checkedOutOn flight)]
                                   (parse-time checked-out-on))
                 :checked-in-on (when-let [checked-in-on (:checkedInOn flight)]
                                  (parse-time checked-in-on))}])))))


(defn get-reservations []
  (try
    (let [operator-id (:fsp-operator-id @config/config)
          fsp-key (:fsp-key @config/config)
          today (time/local-date)
          tomorrow (time/plus today (time/days 1))
          yesterday (time/minus today (time/days 1))
          start-time (time/format "yyyy-MM-dd" yesterday)
          end-time (time/format "yyyy-MM-dd" tomorrow)
          url (str "https://usc-api.flightschedulepro.com/scheduling/v1.0/operators/" operator-id
                   "/reservations"
                   "?startTime=gte:" start-time
                   "&endTime=lt:" end-time
                   "&limit=200")
          response (http/get url {:headers {"x-subscription-key" fsp-key}
                                  :socket-timeout 2000
                                  :connection-timeout 2000})]
      (if (= (:status response) 200)
        (json/read-str (:body response) :key-fn keyword)
        (throw (ex-info "Failed to fetch reservations" {:status (:status response)}))))
    (catch Exception e
      (prn (str "Error fetching reservations: " (.getMessage e))))))

(defn get-flights []
  (try
    (let [operator-id (:fsp-operator-id @config/config)
          fsp-key (:fsp-key @config/config)
          today (time/local-date)
          tomorrow (time/plus today (time/days 1))
          start-time (time/format "yyyy-MM-dd" today)
          end-time (time/format "yyyy-MM-dd" tomorrow)
          url (str "https://usc-api.flightschedulepro.com/reports/v1.0/operators/" operator-id
                   "/flights" "?flightDate=gte:" start-time
                   "&flightDateRangeEndDate=lt:" end-time
                   "&limit=200"
                   )
          response (http/get url {:headers {"x-subscription-key" fsp-key}
                                  :socket-timeout 2000
                                  :connection-timeout 2000})]
      (if (= (:status response) 200)
        (json/read-str (:body response) :key-fn keyword)
        (throw (ex-info "Failed to fetch flights" {:status (:status response)}))))
    (catch Exception e
      (prn (str "Error fetching flights: " (.getMessage e))))))

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

(defn get-aircraft
  []
  (try
    (let [operator-id (:fsp-operator-id @config/config)
          fsp-key (:fsp-key @config/config)
          url (str "https://usc-api.flightschedulepro.com/core/v1.0/operators/" operator-id "/aircraft")
          response (http/get url {:headers {"x-subscription-key" fsp-key}
                                  :socket-timeout 2000
                                  :connection-timeout 2000})]
      (if (= (:status response) 200)
        (let [response (json/read-str (:body response) :key-fn keyword)
              aircraft (filter #(= "Active" (get-in % [:status :name])) (:items response))
              tail-numbers (map #(get % :tailNumber) aircraft)]
          tail-numbers)
        (throw (ex-info "Failed to fetch aircraft" {:status (:status response)}))))
    (catch Exception e
      (prn (str "Error fetching aircraft: " (.getMessage e))))))

(def source {:type :fsp})

(defmethod sources/get-reservations :fsp [_source]
  (get-reservations))

(defmethod sources/get-flights :fsp [_source]
  (get-flights))

(defmethod sources/get-aircraft :fsp [_source]
  (get-aircraft))