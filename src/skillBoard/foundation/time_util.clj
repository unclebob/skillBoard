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

(defn- format-clock [time timestamp?]
  (if timestamp?
    (time/format "yyyy-MM-dd'T'HH:mm:ss.SS" time)
    (time/format "HH:mm" time)))

(defn format-time [time]
  (format-clock time true))

(defn get-HHmm [time]
  (format-clock time false))
