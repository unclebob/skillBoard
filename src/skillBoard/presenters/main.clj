(ns skillBoard.presenters.main
  (:require
    [quil.core :as q]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.presenters.airports :as airports]
    [skillBoard.presenters.flights :as flights]
    [skillBoard.presenters.utils :as utils]
    [skillBoard.presenters.weather :as weather]))

(def screen-type (atom nil))
(def screen-duration (atom 0))
(def screen-start-time (atom 0))

(defn make-screen []
  (let [time (utils/get-now)
        current-screen-seconds (quot (- time @screen-start-time) 1000)]
    (when (or (q/mouse-pressed?) (> current-screen-seconds @screen-duration))
      (reset! screen-type (:screen (first @config/screens)))
      (reset! screen-duration (:duration (first @config/screens)))
      (reset! screen-start-time time)
      (swap! config/screens rest))
    (condp = @screen-type
      :flights (flights/format-flight-screen @comm/polled-reservations @comm/polled-flights)
      :taf (weather/make-taf-screen)
      :flight-category (airports/make-flight-category-screen)
      :no-such-screen)
    ))