(ns skillBoard.weather
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [skillBoard.sources :as sources]
    [clojure.string :as str]
    ))

(defn get-metar
  [icao]
  (try
    (let [url (str "https://aviationweather.gov/api/data/metar?ids=" (str/upper-case icao) "&format=json")
          response (http/get url {:accept :text :with-credentials? false})]
      (if (= (:status response) 200)
        #?(:clj
           (json/read-str (:body response) :key-fn keyword)
           :cljs
           (js->clj (js/JSON.parse (:body response)) :keywordize-keys true)
           )
        (throw (ex-info "Failed to fetch METAR" {:status (:status response)}))))
    (catch Exception e
      (str "Error fetching METAR: " (.getMessage e)))))

(def source {:type :aviation-weather})

(defmethod sources/get-metar :aviation-weather[_source icao]
  (get-metar icao))