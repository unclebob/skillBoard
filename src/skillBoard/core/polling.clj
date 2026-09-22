(ns skillBoard.core.polling
  (:require
    [skillBoard.adapters.heartbeat :as heartbeat]
    [skillBoard.foundation.atoms :as atoms]
    [skillBoard.foundation.config :as config]
    [skillBoard.foundation.core-utils :as core-utils]
    [skillBoard.gateways.airspace :as airspace]
    [skillBoard.gateways.aviation-weather :as weather]
    [skillBoard.gateways.flight-schedule-pro :as fsp]
    [skillBoard.gateways.radar-cape :as radar]
    [skillBoard.gateways.wind-data :as wind-data]))

(defn board-snapshot []
  {:reservations @fsp/polled-reservations
   :flights @fsp/polled-flights
   :adsbs @radar/polled-adsbs
   :metars @weather/polled-metars
   :nearby-metars @weather/polled-nearby-metars
   :airspace-classes @airspace/polled-airspace-classes
   :metar-history @weather/polled-metar-history
   :tafs @weather/polled-tafs
   :nearby-adsbs @radar/polled-nearby-adsbs
   :aircraft @fsp/polled-aircraft
   :wind-grid (wind-data/current-grid)
   :test? @atoms/test?
   :com-errors {:reservations @fsp/reservation-com-errors
                :adsb @radar/adsb-com-errors
                :weather (+ @weather/weather-com-errors
                            @airspace/airspace-com-errors)
                :open-meteo-ok? @wind-data/open-meteo-ok?}})

(defn poll []
  (try
    (fsp/get-aircraft)
    (radar/get-adsb-by-tail-numbers @fsp/polled-aircraft)
    (fsp/get-flights)
    (fsp/get-reservations)
    (weather/get-metars config/flight-category-airports)
    (weather/get-nearby-metars)
    (airspace/get-airspace-classes)
    (weather/get-metar-history config/airport)
    (weather/get-tafs config/taf-airport)
    (radar/get-nearby-adsb)
    (wind-data/refresh-wind-grid-if-due!)
    (reset! atoms/log-traffic? true)
    (catch Exception e
      (core-utils/log :error e))))

(defn poll-due? [now]
  (let [seconds-since-last-poll (quot (- now @atoms/poll-time) 1000)]
    (or @atoms/poll-key
        (>= seconds-since-last-poll config/seconds-between-internet-polls))))

(defn update-clock-pulse! [now]
  (reset! atoms/clock-pulse (< 500 (mod now 1000))))

(defn run-due-poll! [now]
  (when (poll-due? now)
    (future (poll))
    (reset! atoms/poll-key false)
    (reset! atoms/poll-time now)))

(defn initialize-polling! []
  (poll)
  (heartbeat/start!))

(defn run-polling-step! [now]
  (run-due-poll! now)
  (heartbeat/run-due! now)
  (update-clock-pulse! now))

(defn start-polling []
  (initialize-polling!)
  (future
    (loop []
      (let [now (System/currentTimeMillis)]
        (run-polling-step! now)
        (Thread/sleep 100)
        (recur)))))
