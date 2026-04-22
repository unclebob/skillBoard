(ns skillBoard.wind-data
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [java-time.api :as time]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.core-utils :as core-utils]))

(def polled-wind-grid (atom nil))
(def last-open-meteo-poll-ms (atom nil))
(def open-meteo-poll-interval-ms (* 120 60 1000))
(def open-meteo-url "https://api.open-meteo.com/v1/gfs")

(defn current-time-ms []
  (System/currentTimeMillis))

(defn radius-bounds [[lat lon] radius-nm]
  (let [lat-delta (/ radius-nm 60.0)
        lon-delta (/ radius-nm (* 60.0 (Math/cos (Math/toRadians lat))))]
    {:top (+ lat lat-delta)
     :bottom (- lat lat-delta)
     :left (- lon lon-delta)
     :right (+ lon lon-delta)}))

(defn- fmt2 [n]
  (format "%.2f" (double n)))

(defn nm-distance [[lat lon] [other-lat other-lon]]
  (let [lat-nm (* 60.0 (- other-lat lat))
        lon-nm (* 60.0 (Math/cos (Math/toRadians lat)) (- other-lon lon))]
    (Math/sqrt (+ (* lat-nm lat-nm) (* lon-nm lon-nm)))))

(defn sample-points
  ([center radius-nm]
   (sample-points center radius-nm 11))
  ([center radius-nm steps]
   (let [{:keys [top bottom left right]} (radius-bounds center radius-nm)
         lat-step (/ (- top bottom) (dec steps))
         lon-step (/ (- right left) (dec steps))]
     (vec
       (for [lat (map #(+ bottom (* % lat-step)) (range steps))
             lon (map #(+ left (* % lon-step)) (range steps))
             :when (<= (nm-distance center [lat lon]) radius-nm)]
         {:lat lat :lon lon})))))

(defn open-meteo-query-params [points]
  {:latitude (str/join "," (map (comp fmt2 :lat) points))
   :longitude (str/join "," (map (comp fmt2 :lon) points))
   :hourly "wind_speed_10m,wind_direction_10m"
   :wind_speed_unit "kn"
   :forecast_hours 1
   :cell_selection "nearest"})

(defn wind-components [speed-knots direction-degrees]
  (let [radians (Math/toRadians (double direction-degrees))
        speed (double speed-knots)]
    {:u (* -1.0 speed (Math/sin radians))
     :v (* -1.0 speed (Math/cos radians))}))

(defn- first-value [values]
  (first (remove nil? values)))

(defn- open-meteo-point [point response]
  (let [hourly (:hourly response)
        speed (first-value (:wind_speed_10m hourly))
        direction (first-value (:wind_direction_10m hourly))]
    (when (and speed direction)
      (merge point (wind-components speed direction)))))

(defn open-meteo-response->grid [center radius-nm points response]
  (let [responses (if (sequential? response) response [response])
        wind-points (keep (fn [[point location-response]]
                            (open-meteo-point point location-response))
                          (map vector points responses))]
    {:source :open-meteo-gfs-hrrr
     :generated-at (str (time/local-date-time))
     :generated-at-ms (current-time-ms)
     :center center
     :radius-nm radius-nm
     :points (vec wind-points)}))

(defn fetch-open-meteo-grid []
  (let [center config/airport-lat-lon
        radius-nm config/wind-map-radius-nm
        points (sample-points center radius-nm)
        response (http/get open-meteo-url
                           {:accept :json
                            :as :text
                            :query-params (open-meteo-query-params points)
                            :socket-timeout 5000
                            :connection-timeout 5000})
        body (json/read-str (:body response) :key-fn keyword)]
    (when (= 200 (:status response))
      (open-meteo-response->grid center radius-nm points body))))

(defn refresh-wind-grid! []
  (try
    (when-let [grid (fetch-open-meteo-grid)]
      (reset! polled-wind-grid grid)
      (reset! comm/open-meteo-ok? true)
      grid)
    (catch Exception e
      (core-utils/log :error (str "Error fetching wind data: " (.getMessage e)))
      (reset! comm/open-meteo-ok? false)
      @polled-wind-grid)))

(defn open-meteo-poll-due? [now]
  (or (nil? @last-open-meteo-poll-ms)
      (>= (- now @last-open-meteo-poll-ms) open-meteo-poll-interval-ms)))

(defn refresh-wind-grid-if-due!
  ([]
   (refresh-wind-grid-if-due! (System/currentTimeMillis)))
  ([now]
   (when (open-meteo-poll-due? now)
     (reset! last-open-meteo-poll-ms now)
     (refresh-wind-grid!))))

(defn synthetic-grid []
  (let [[center-lat center-lon] config/airport-lat-lon
        bounds (radius-bounds config/airport-lat-lon config/wind-map-radius-nm)
        lats (range (:bottom bounds) (:top bounds) 0.35)
        lons (range (:left bounds) (:right bounds) 0.35)
        points (for [lat lats
                     lon lons
                     :let [dy (- lat center-lat)
                           dx (- lon center-lon)
                           u (+ 10 (* 0.8 dx))
                           v (+ 4 (* 0.5 dy))]]
                 {:lat lat :lon lon :u u :v v})]
    {:source :synthetic
     :generated-at (str (time/local-date-time))
     :generated-at-ms (current-time-ms)
     :center config/airport-lat-lon
     :radius-nm config/wind-map-radius-nm
     :points (vec points)}))

(defn current-grid []
  (or @polled-wind-grid
      (reset! polled-wind-grid (synthetic-grid))))
