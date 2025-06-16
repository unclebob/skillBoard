(ns skillBoard.flight-schedule-pro
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.string :as string]
    [java-time.api :as time]
    [skillBoard.sources :as sources]
    ))

(def epoch-str "1753-01-01T00:00:00")
(def epoch (time/local-date-time epoch-str))

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
    (apply hash-map
           (flatten
             (for [reservation items]
               [(:reservationId reservation)
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
                                   (parse-time checked-out))}])))))

(defn unpack-flights [{:keys [items]}]
  (if (empty? items)
    []
    (for [flight items]
      {:reservation-id (:reservationId flight)
       :checked-out-on (when-let [checked-out-on (:checkedOutOn flight)]
                         (parse-time checked-out-on))
       :checked-in-on (when-let [checked-in-on (:checkedInOn flight)]
                        (parse-time checked-in-on))})))


(let [[fsp-key operator-id] (read-string (slurp "private/fsp-key"))]
  (defn get-reservations []
    (try
      (let [today (time/local-date)
            tomorrow (time/plus today (time/days 1))
            yesterday (time/minus today (time/days 1))
            start-time (time/format "yyyy-MM-dd" yesterday)
            end-time (time/format "yyyy-MM-dd" tomorrow)
            url (str "https://usc-api.flightschedulepro.com/scheduling/v1.0/operators/" operator-id
                     "/reservations"
                     "?startTime=gte:" start-time
                     "&endTime=lt:" end-time
                     "&limit=200")
            response (http/get url {:headers {"x-subscription-key" fsp-key}})]
        (if (= (:status response) 200)
          (json/read-str (:body response) :key-fn keyword)
          (throw (ex-info "Failed to fetch reservations" {:status (:status response)}))))
      (catch Exception e
        (str "Error fetching reservations: " (.getMessage e)))))

  (defn get-flights []
    (try
      (let [today (time/local-date)
            tomorrow (time/plus today (time/days 1))
            start-time (time/format "yyyy-MM-dd" today)
            end-time (time/format "yyyy-MM-dd" tomorrow)
            url (str "https://usc-api.flightschedulepro.com/reports/v1.0/operators/" operator-id
                     "/flights" "?flightDate=gte:" start-time
                     "&flightDateRangeEndDate=lt:" end-time
                     "&limit=200"
                     )
            response (http/get url {:headers {"x-subscription-key" fsp-key}})]
        (if (= (:status response) 200)
          #?(:clj
             (json/read-str (:body response) :key-fn keyword)
             :cljs
             (js->clj (js/JSON.parse (:body response)) :keywordize-keys true)
             )
          (throw (ex-info "Failed to fetch flights" {:status (:status response)}))))
      (catch #?(:clj Exception :cljs js/Error) e
        (str "Error fetching flights: " (.getMessage e)))))
  )

(def source {:type :fsp})

(defmethod sources/get-reservations :fsp [_source]
  (get-reservations))

(defmethod sources/get-flights :fsp [_source]
  (get-flights))