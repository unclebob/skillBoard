(ns skillBoard.flight-schedule-pro
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [java-time.api :as time]
    [skillBoard.sources :as sources]
    ))

(def fsp-key (slurp "private/fsp-key"))

(defn unpack-reservations [{:keys [items]}]
  (if (empty? items)
    []
    (apply hash-map
           (flatten
             (for [reservation items]
               [(:reservationId reservation)
                {:reservationId (:reservationId reservation)
                 :tail-number (:tailNumber (:aircraft reservation))
                 :activity-type (:name (:activityType reservation))
                 :start-time (:startTime reservation)
                 :pilot-name (when-let [pilot (first (:pilots reservation))]
                               [(:firstName pilot)
                                (:lastName pilot)])
                 :instructor-name (when-let [instructor (:instructor reservation)]
                                    [(:firstName instructor) (:lastName instructor)])
                 :reservationStatus (:name (:reservationStatus reservation))}]))))
  )

(defn get-reservations
  [operator-id]
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

(defn get-flights
  [operator-id]
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

(def source {:type :fsp})

(defmethod sources/get-reservations :fsp [_source operator-id]
  (get-reservations operator-id))

(defmethod sources/get-flights :fsp [_source operator-id]
  (get-flights operator-id))