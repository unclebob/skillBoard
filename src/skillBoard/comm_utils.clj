(ns skillBoard.comm-utils
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [java-time.api :as time]
    [skillBoard.config :as config]))

(defn get-json [url args save-atom com-errors error-name]
  (try
    (let [{:keys [status body]} (http/get url args)]
      (if (= status 200)
        (do
          (reset! save-atom (json/read-str body :key-fn keyword))
          (reset! com-errors 0)
          @save-atom)
        (throw (ex-info (str "Failed to fetch " error-name) {:status status}))))
    (catch Exception e
      (prn (str "Error fetching " error-name ": " (.getMessage e)))
      (swap! com-errors inc)
      @save-atom)))

(def polled-reservations (atom {}))
(def reservation-com-errors (atom 0))

(def polled-flights (atom {}))

(defn get-reservations []
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
        args {:headers {"x-subscription-key" fsp-key}
              :socket-timeout 2000
              :connection-timeout 2000}]
    (get-json url args polled-reservations reservation-com-errors "reservations")))

(defn get-flights []
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
        args {:headers {"x-subscription-key" fsp-key}
              :socket-timeout 2000
              :connection-timeout 2000}]
    (get-json url args polled-flights reservation-com-errors "flights")))