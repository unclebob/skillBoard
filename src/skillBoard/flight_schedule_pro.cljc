(ns skillBoard.flight-schedule-pro
  (:require
      #?(:clj [clojure.data.json :as json])
      #?(:clj [clj-http.client :as http]
         :cljs [cljs-http.client :as http])
      [clojure.string :as str]
      [java-time.api :as time]
      ))

(def fsp-key (slurp "private/fsp-key"))

(defn get-aircraft
  [operator-id]
  (try
    (let [url (str "https://usc-api.flightschedulepro.com/core/v1.0/operators/" operator-id "/aircraft")
          response (http/get url {:headers {"x-subscription-key" fsp-key}})]
      (if (= (:status response) 200)
        #?(:clj
           (json/read-str (:body response) :key-fn keyword)
           :cljs
           (js->clj (js/JSON.parse (:body response)) :keywordize-keys true)
           )
        (throw (ex-info "Failed to fetch aircraft" {:status (:status response)}))))
    (catch #?(:clj Exception :cljs js/Error) e
      (str "Error fetching aircraft: " (#?(:clj .getMessage :cljs .-message) e)))))

(defn get-reservations
  [operator-id]
  (try
    (let [today (time/local-date)
          tomorrow (time/plus today (time/days 1))
          start-time (time/format "yyyy-MM-dd" today)
          end-time (time/format "yyyy-MM-dd" tomorrow)
          url (str "https://usc-api.flightschedulepro.com/scheduling/v1.0/operators/" operator-id
                   "/reservations?startTime=gte:" start-time "&endTime=lt:" end-time)
          response (http/get url {:headers {"x-subscription-key" fsp-key}})]
      (if (= (:status response) 200)
        #?(:clj
           (json/read-str (:body response) :key-fn keyword)
           :cljs
           (js->clj (js/JSON.parse (:body response)) :keywordize-keys true)
           )
        (throw (ex-info "Failed to fetch reservations" {:status (:status response)}))))
    (catch #?(:clj Exception :cljs js/Error) e
      (str "Error fetching reservations: " (#?(:clj .getMessage :cljs .-message) e)))))

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
                   )
          _ (prn 'url url)
          response (http/get url {:headers {"x-subscription-key" fsp-key}})]
      (if (= (:status response) 200)
        #?(:clj
           (json/read-str (:body response) :key-fn keyword)
           :cljs
           (js->clj (js/JSON.parse (:body response)) :keywordize-keys true)
           )
        (throw (ex-info "Failed to fetch flights" {:status (:status response)}))))
    (catch #?(:clj Exception :cljs js/Error) e
      (str "Error fetching flights: " (#?(:clj .getMessage :cljs .-message) e)))))