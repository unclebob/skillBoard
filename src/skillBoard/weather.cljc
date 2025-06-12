(ns skillBoard.weather
  (:require
    #?(:clj [clojure.data.json :as json])
    #?(:clj [clj-http.client :as http]
       :cljs [cljs-http.client :as http])
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
    (catch #?(:clj Exception :cljs js/Error) e
      (str "Error fetching METAR: " (#?(:clj .getMessage :cljs .-message) e)))))