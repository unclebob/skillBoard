(ns skillBoard.weather
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [skillBoard.sources :as sources]
    ))

(def com-errors (atom 0))

(defn get-metar
  [icao]
  (try
    (let [url (str "https://aviationweather.gov/api/data/metar?ids=" (str/upper-case icao) "&format=json")
          response (http/get url {:accept :text :with-credentials? false})]
      (if (= (:status response) 200)
        (do
          (reset! com-errors 0)
          (json/read-str (:body response) :key-fn keyword))
        (throw (ex-info "Failed to fetch METAR" {:status (:status response)}))))
    (catch Exception e
      (prn "Error fetching METAR: " (.getMessage e))
      (swap! com-errors inc)
      nil)))

(def source {:type :aviation-weather})

(defmethod sources/get-metar :aviation-weather [_source icao]
  (get-metar icao))