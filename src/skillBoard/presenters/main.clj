(ns skillBoard.presenters.main
  (:require
    [quil.core :as q]
    [skillBoard.config :as config]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.presenters.utils :as utils]))

(def screen-type (atom (:screen (first @config/screens))))
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
    (screen/make @screen-type)
    ))