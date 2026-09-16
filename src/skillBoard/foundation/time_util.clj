(ns skillBoard.foundation.time-util
  (:require
    [java-time.api :as time]
    [skillBoard.foundation.config :as config]))

(def epoch-str "1753-01-01T00:00:00")
(def epoch (time/local-date-time epoch-str))

(defn local-to-utc [local-time]
  (-> local-time
      (time/zoned-date-time (time/local-date) (time/zone-id config/time-zone))
      (.withZoneSameInstant (time/zone-id "UTC"))
      (time/local-time)))

(defn format-time [time]
  (time/format "yyyy-MM-dd'T'HH:mm:ss.SS" time))

(defn get-HHmm [time]
  (time/format "HH:mm" time))
