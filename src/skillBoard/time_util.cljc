(ns skillBoard.time-util
  (:require
    [clojure.string :as string]
    [clojure.string]
    [java-time.api :as time]
    [skillBoard.config :as config]
    ))

(def epoch-str "1753-01-01T00:00:00")
(def epoch (time/local-date-time epoch-str))

(defn local-to-utc [local-time]
  (-> local-time
      (time/zoned-date-time (time/local-date) (time/zone-id config/time-zone))
      (.withZoneSameInstant (time/zone-id "UTC"))
      (time/local-time)))

(defn parse-time [time-str]
  (cond
    (or (empty? time-str) (string/starts-with? time-str epoch-str))
    nil

    (= (count time-str) 22)
    (time/local-date-time "yyyy-MM-dd'T'HH:mm:ss.SS" time-str)

    :else
    (time/local-date-time "yyyy-MM-dd'T'HH:mm:ss" time-str)))

(defn format-time [time]
  (time/format "yyyy-MM-dd'T'HH:mm:ss.SS" time))

(defn get-HHmm [time]
  (time/format "HH:mm" time))