(ns skillBoard.weather
  (:require
    [clojure.string :as str]
    [skillBoard.api-utils :as api]
    [skillBoard.sources :as sources]
    ))

(def com-errors (atom 0))
(def previous-metar (atom nil))

(defn get-metar [icao]
  (let [url (str "https://aviationweather.gov/api/data/metar?ids=" (str/upper-case icao) "&format=json")
        args {:accept :text :with-credentials? false}]
    (api/get-json url args previous-metar com-errors "METAR")))

(def previous-taf (atom nil))
(defn get-taf [icao]
  (let [url (str "https://aviationweather.gov/api/data/taf?ids=" (str/upper-case icao) "&format=json")
        args {:accept :text :with-credentials? false}]
    (api/get-json url args previous-taf com-errors "TAF")))

(def source {:type :aviation-weather})

(defmethod sources/get-metar :aviation-weather [_source icao]
  (get-metar icao))

(defmethod sources/get-taf :aviation-weather [_source icao]
  (get-taf icao))