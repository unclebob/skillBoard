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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T10:29:13.900329-05:00", :module-hash "-1449328550", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "1087877226"} {:id "def/epoch-str", :kind "def", :line 9, :end-line 9, :hash "392333759"} {:id "def/epoch", :kind "def", :line 10, :end-line 10, :hash "145390513"} {:id "defn/local-to-utc", :kind "defn", :line 12, :end-line 16, :hash "354383233"} {:id "defn/parse-time", :kind "defn", :line 18, :end-line 27, :hash "378198687"} {:id "defn/format-time", :kind "defn", :line 29, :end-line 30, :hash "1981102482"} {:id "defn/get-HHmm", :kind "defn", :line 32, :end-line 33, :hash "-1838982699"}]}
;; clj-mutate-manifest-end
