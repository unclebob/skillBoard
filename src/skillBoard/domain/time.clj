(ns skillBoard.domain.time
  (:require
    [clojure.string :as string]
    [java-time.api :as time]))

(def epoch-str "1753-01-01T00:00:00")

(defn parse-time [time-str]
  (cond
    (or (empty? time-str) (string/starts-with? time-str epoch-str))
    nil

    (= (count time-str) 22)
    (time/local-date-time "yyyy-MM-dd'T'HH:mm:ss.SS" time-str)

    :else
    (time/local-date-time "yyyy-MM-dd'T'HH:mm:ss" time-str)))
