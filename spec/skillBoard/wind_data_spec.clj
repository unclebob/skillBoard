(ns skillBoard.wind-data-spec
  (:require
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.core-utils :as core-utils]
    [skillBoard.wind-data :as wind-data]
    [speclj.core :refer :all]))

(describe "wind data"
  (it "builds a 200 NM bounding box around an airport"
    (let [bounds (wind-data/radius-bounds [42.0 -87.0] 120)]
      (should= 44.0 (:top bounds))
      (should= 40.0 (:bottom bounds))
      (should (< (:left bounds) -87.0))
      (should (> (:right bounds) -87.0))))

  (it "samples points inside the configured wind radius"
    (let [points (wind-data/sample-points [42.0 -87.0] 120 5)]
      (should (< 0 (count points)))
      (should (every? #(<= (wind-data/nm-distance [42.0 -87.0] [(:lat %) (:lon %)]) 120)
                      points))))

  (it "uses a denser default sample grid for smoother wind animation"
    (should (< 50 (count (wind-data/sample-points [42.0 -87.0] 120)))))

  (it "builds Open-Meteo query parameters for sampled points"
    (let [params (wind-data/open-meteo-query-params [{:lat 42.123 :lon -87.456}
                                                     {:lat 41.987 :lon -88.111}])]
      (should= "42.12,41.99" (:latitude params))
      (should= "-87.46,-88.11" (:longitude params))
      (should= "wind_speed_10m,wind_direction_10m" (:hourly params))
      (should= "kn" (:wind_speed_unit params))
      (should= 1 (:forecast_hours params))))

  (it "converts meteorological speed and direction to u/v components"
    (let [{:keys [u v]} (wind-data/wind-components 10 90)]
      (should (< (Math/abs (- -10.0 u)) 0.001))
      (should (< (Math/abs v) 0.001))))

  (it "converts an Open-Meteo response into wind grid points"
    (let [points [{:lat 42.0 :lon -87.0}
                  {:lat 43.0 :lon -88.0}]
          response [{:hourly {:wind_speed_10m [10.0]
                              :wind_direction_10m [180.0]}}
                    {:hourly {:wind_speed_10m [5.0]
                              :wind_direction_10m [270.0]}}]
          grid (wind-data/open-meteo-response->grid [42.0 -87.0] 200 points response)]
      (should= :open-meteo-gfs-hrrr (:source grid))
      (should (integer? (:generated-at-ms grid)))
      (should= 2 (count (:points grid)))
      (let [{:keys [lat lon u v]} (first (:points grid))]
        (should= 42.0 lat)
        (should= -87.0 lon)
        (should (< (Math/abs u) 0.001))
        (should (< (Math/abs (- 10.0 v)) 0.001)))))

  (it "polls Open-Meteo immediately, then no more than once every two hours"
    (let [polls (atom [])]
      (with-redefs [wind-data/last-open-meteo-poll-ms (atom nil)
                    wind-data/refresh-wind-grid! (fn []
                                                   (swap! polls conj :poll)
                                                   {:source :open-meteo-gfs})]
        (should= {:source :open-meteo-gfs} (wind-data/refresh-wind-grid-if-due! 1000))
        (should-be nil? (wind-data/refresh-wind-grid-if-due! (+ 1000 7199000)))
        (should= {:source :open-meteo-gfs} (wind-data/refresh-wind-grid-if-due! (+ 1000 7200000)))
        (should= [:poll :poll] @polls))))

  (it "marks Open-Meteo healthy after successful refresh without changing aviation weather errors"
    (with-redefs [wind-data/polled-wind-grid (atom nil)
                  comm/weather-com-errors (atom 4)
                  comm/open-meteo-ok? (atom false)
                  wind-data/fetch-open-meteo-grid (fn [] {:source :open-meteo-gfs})]
      (should= {:source :open-meteo-gfs} (wind-data/refresh-wind-grid!))
      (should= true @comm/open-meteo-ok?)
      (should= 4 @comm/weather-com-errors)))

  (it "marks Open-Meteo unhealthy after refresh failures without changing aviation weather errors"
    (with-redefs [wind-data/polled-wind-grid (atom :last-good-grid)
                  comm/weather-com-errors (atom 3)
                  comm/open-meteo-ok? (atom true)
                  core-utils/log (fn [& _])
                  wind-data/fetch-open-meteo-grid (fn [] (throw (ex-info "rate limited" {})))]
      (should= :last-good-grid (wind-data/refresh-wind-grid!))
      (should= false @comm/open-meteo-ok?)
      (should= 3 @comm/weather-com-errors)))

  (it "creates a synthetic fallback grid"
    (let [grid (wind-data/synthetic-grid)]
      (should= :synthetic (:source grid))
      (should (integer? (:generated-at-ms grid)))
      (should= config/wind-map-radius-nm (:radius-nm grid))
      (should-not-be empty? (:points grid))))

  (it "uses a coherent fallback wind field instead of orbiting around the center"
    (let [points (:points (wind-data/synthetic-grid))]
      (should (every? pos? (map :u points)))
      (should (every? pos? (map :v points))))))
