(ns skillBoard.radar-cape
  (:require
      [clj-http.client :as http]
      [clojure.data.json :as json]
      [skillBoard.sources :as sources]
      [clojure.string :as str]
      ))

(defn get-adsb-raw
  [tail-numbers]
  (try
    (let [tails (map #(str "icao=" %) tail-numbers)
          tails (str/join \& (set tails))
          _ (prn 'tails tails)
          url (str "http://10.10.30.90/aircraftlist.json?" tails)
          response (http/get url {:accept :text :with-credentials? false})]
      (if (= (:status response) 200)
        #?(:clj
           (json/read-str (:body response) :key-fn keyword)
           :cljs
           (js->clj (js/JSON.parse (:body response)) :keywordize-keys true)
           )
        (throw (ex-info "Failed to fetch ADSB" {:status (:status response)}))))
    (catch Exception e
      (str "Error fetching ADSB: " (.getMessage e)))))

(defn get-adsb [source tail-numbers]
  (let [raw (sources/get-adsb-raw source tail-numbers)]
    (apply hash-map
               (flatten
                 (for [adsb raw]
                   [(get adsb :reg)
                    adsb]))))
  )

(def source {:type :radar-cape })

(defmethod sources/get-adsb-raw :radar-cape [_source tail-numbers]
  (get-adsb-raw tail-numbers))
