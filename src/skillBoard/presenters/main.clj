(ns skillBoard.presenters.main
  (:require
    [skillBoard.atoms :as atoms]
    [skillBoard.config :as config]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.presenters.utils :as utils]))

(def screen-type (atom (:screen (first @config/screens))))
(def screen-duration (atom 0))
(def screen-start-time (atom 0))

(defn make-screen []
  (let [time (utils/get-now)
        current-screen-seconds (quot (- time @screen-start-time) 1000)]
    (when (or @atoms/change-screen? (> current-screen-seconds @screen-duration))
      (reset! screen-type (:screen (first @config/screens)))
      (reset! screen-duration (:duration (first @config/screens)))
      (reset! screen-start-time time)
      (reset! atoms/change-screen? false)
      (swap! config/screens rest))
    (screen/make @screen-type)
    ))