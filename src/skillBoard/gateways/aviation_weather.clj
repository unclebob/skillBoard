(ns skillBoard.gateways.aviation-weather
  (:require
    [clj-http.client :as http]
    [clojure.string :as str]
    [skillBoard.foundation.config :as config]
    [skillBoard.foundation.core-utils :as core-utils]
    [skillBoard.gateways.comm-utils :as comm]))

(def polled-metars (atom {}))
(def polled-nearby-metars (atom {}))
(def polled-metar-history (atom {}))
(def polled-tafs (atom {}))
(def weather-com-errors (atom 0))

(def nearby-metar-cache-url "https://aviationweather.gov/data/cache/metars.cache.csv.gz")

(defn icao-query [icao]
  (let [icao-str (if (sequential? icao)
                   (str/join "," (map str/upper-case icao))
                   (str/upper-case icao))]
    icao-str))

(defn keyed-weather-response [response]
  (if (sequential? response)
    (into {} (map (fn [m] [(:icaoId m) m]) response))
    {(:icaoId response) response}))

(defn get-keyed-aviation-weather [kind polled-atom icao]
  (let [url (str "https://aviationweather.gov/api/data/" kind
                 "?ids=" (icao-query icao) "&format=json")
        args {:accept :text :with-credentials? false}
        response (comm/get-json url args polled-atom weather-com-errors (str/upper-case kind))
        keyed (keyed-weather-response response)]
    (reset! polled-atom keyed)
    keyed))

(defn get-metars [icao]
  (get-keyed-aviation-weather "metar" polled-metars icao))

(defn- blank->nil [s]
  (when-not (or (str/blank? s) (= "null" s))
    s))

(defn- parse-double-field [s]
  (when-let [value (blank->nil s)]
    (Double/parseDouble value)))

(defn- parse-int-field [s]
  (when-let [value (blank->nil s)]
    (Integer/parseInt value)))

(def cloud-layer-fields
  [[:sky_cover :cloud_base_ft_agl]
   [:sky_cover_2 :cloud_base_ft_agl_2]
   [:sky_cover_3 :cloud_base_ft_agl_3]
   [:sky_cover_4 :cloud_base_ft_agl_4]])

(defn- cloud-layer [record [cover-key base-key]]
  (when-let [cover (blank->nil (cover-key record))]
    (cond-> {:cover cover}
      (parse-int-field (base-key record)) (assoc :base (parse-int-field (base-key record))))))

(defn metar-cache-clouds [record]
  (let [clouds (keep #(cloud-layer record %) cloud-layer-fields)]
    (cond-> (vec clouds)
      (parse-int-field (:vert_vis_ft record))
      (conj {:cover "VV" :base (parse-int-field (:vert_vis_ft record))}))))

(defn metar-cache-record->metar [record]
  (when-let [station-id (blank->nil (:station_id record))]
    (when-let [lat (parse-double-field (:latitude record))]
      (when-let [lon (parse-double-field (:longitude record))]
        {:icaoId station-id
         :lat lat
         :lon lon
         :fltCat (blank->nil (:flight_category record))
         :clouds (metar-cache-clouds record)
         :rawOb (:raw_text record)}))))

(defn- distance-nm [[center-lat center-lon] {:keys [lat lon]}]
  (let [lat-nm (* 60.0 (- lat center-lat))
        lon-nm (* 60.0 (Math/cos (Math/toRadians center-lat)) (- lon center-lon))]
    (Math/sqrt (+ (* lat-nm lat-nm) (* lon-nm lon-nm)))))

(defn nearby-metars [metars center radius-nm]
  (->> metars
       (filter #(<= (distance-nm center %) radius-nm))
       (sort-by :icaoId)))

(defn get-nearby-metars []
  (try
    (let [{:keys [status body]} (http/get nearby-metar-cache-url
                                          {:accept :octet-stream
                                           :as :byte-array
                                           :socket-timeout 5000
                                           :connection-timeout 5000})]
      (if (= 200 status)
        (let [all-metars (keep metar-cache-record->metar
                               (comm/csv->maps (comm/gzip-bytes->string body)))
              metars (nearby-metars all-metars config/airport-lat-lon config/wind-map-radius-nm)
              metar-dict (into {} (map (fn [m] [(:icaoId m) m]) metars))]
          (reset! polled-nearby-metars metar-dict)
          (reset! weather-com-errors 0)
          metar-dict)
        (throw (ex-info "Failed to fetch nearby METAR cache" {:status status}))))
    (catch Exception e
      (core-utils/log-event :error :communication-issue
                            (str "Error fetching nearby METAR cache: " (.getMessage e)))
      (swap! weather-com-errors inc)
      @polled-nearby-metars)))

(defn get-metar-history [icao]
  (let [icao-str (str/upper-case icao)
        url (str "https://aviationweather.gov/api/data/metar?ids=" icao-str "&format=json&hours=4")
        args {:accept :text :with-credentials? false}]
    (comm/get-json url args polled-metar-history weather-com-errors "METAR")))

(defn get-tafs [icao]
  (get-keyed-aviation-weather "taf" polled-tafs icao))
