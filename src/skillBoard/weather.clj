(ns skillBoard.weather
  (:require
    [cheshire.core :as json]
    [clj-http.client :as http]
    [clojure.string :as str]
    ))

(defn get-metar
  [icao]
  (try
    (let [url (str "https://aviationweather.gov/api/data/metar?ids=" (str/upper-case icao) "&format=json")
          response (http/get url {:accept :text})]
      (if (= (:status response) 200)
        (json/parse-string (:body response) true)
        (throw (ex-info "Failed to fetch METAR" {:status (:status response)}))))
    (catch Exception e
      (str "Error fetching METAR: " (.getMessage e)))))