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
(def open-meteo-poll-interval-ms (* 60 60 1000))
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
   (sample-points center radius-nm 16))
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
                            :socket-timeout 10000
                            :connection-timeout 10000})
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-09T09:22:33.040433-05:00", :module-hash "-1461224234", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "1552227095"} {:id "def/polled-wind-grid", :kind "def", :line 11, :end-line 11, :hash "531804537"} {:id "def/last-open-meteo-poll-ms", :kind "def", :line 12, :end-line 12, :hash "-1376410817"} {:id "def/open-meteo-poll-interval-ms", :kind "def", :line 13, :end-line 13, :hash "1540844743"} {:id "def/open-meteo-url", :kind "def", :line 14, :end-line 14, :hash "-1606973663"} {:id "defn/current-time-ms", :kind "defn", :line 16, :end-line 17, :hash "1840988419"} {:id "defn/radius-bounds", :kind "defn", :line 19, :end-line 25, :hash "2044523599"} {:id "defn-/fmt2", :kind "defn-", :line 27, :end-line 28, :hash "1158867611"} {:id "defn/nm-distance", :kind "defn", :line 30, :end-line 33, :hash "-1773643636"} {:id "defn/sample-points", :kind "defn", :line 35, :end-line 46, :hash "-1802631041"} {:id "defn/open-meteo-query-params", :kind "defn", :line 48, :end-line 54, :hash "1268617612"} {:id "defn/wind-components", :kind "defn", :line 56, :end-line 60, :hash "538238581"} {:id "defn-/first-value", :kind "defn-", :line 62, :end-line 63, :hash "2119057903"} {:id "defn-/open-meteo-point", :kind "defn-", :line 65, :end-line 70, :hash "-1930320458"} {:id "defn/open-meteo-response->grid", :kind "defn", :line 72, :end-line 82, :hash "2100884497"} {:id "defn/fetch-open-meteo-grid", :kind "defn", :line 84, :end-line 96, :hash "-1156999000"} {:id "defn/refresh-wind-grid!", :kind "defn", :line 98, :end-line 107, :hash "-1110781755"} {:id "defn/open-meteo-poll-due?", :kind "defn", :line 109, :end-line 111, :hash "35050700"} {:id "defn/refresh-wind-grid-if-due!", :kind "defn", :line 113, :end-line 119, :hash "246627682"} {:id "defn/synthetic-grid", :kind "defn", :line 121, :end-line 138, :hash "1979596287"} {:id "defn/current-grid", :kind "defn", :line 140, :end-line 142, :hash "644947167"}]}
;; clj-mutate-manifest-end
