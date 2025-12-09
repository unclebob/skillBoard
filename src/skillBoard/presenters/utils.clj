(ns skillBoard.presenters.utils
  (:require
    [clojure.string :as str]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.navigation :as nav]))

(defn blank? [s] (empty? (str/trim s)))

(defn flight-category [vis ceiling]
  (cond
    (and (>= vis 10.0) (>= ceiling 3000)) :green   ; VFR
    (and (>= vis 3.0) (>= ceiling 1000)) :blue    ; MVFR
    (and (>= vis 1.0) (>= ceiling 500)) :red     ; IFR
    :else :magenta)) ; LIFR

(defn by-distance [metar1 metar2]
  (let [lat1 (:lat metar1)
        lon1 (:lon metar1)
        lat2 (:lat metar2)
        lon2 (:lon metar2)
        [airport-lat airport-lon] config/airport-lat-lon
        dist1 (:distance (nav/dist-and-bearing airport-lat airport-lon lat1 lon1))
        dist2 (:distance (nav/dist-and-bearing airport-lat airport-lon lat2 lon2))]
    (< dist1 dist2)))

(defn shorten-metar [metar]
  (let [metar-text (:rawOb metar)
        short-metar (if (nil? metar-text)
                      "NO-METAR"
                      (-> metar-text
                          (str/split #"RMK")
                          first
                          (subs 6)))
        final-metar (if (> (count short-metar) config/cols)
                      (subs short-metar 0 config/cols)
                      short-metar)
        fltCat (:fltCat metar)
        color (case fltCat
                "VFR" :green
                "MVFR" :blue
                "IFR" :red
                "LIFR" :magenta
                :white)]
    {:line (str/trim final-metar)
     :color color}))

(defn get-short-metar
  ([]
   (get-short-metar config/airport))

  ([airport]
   (let [metar (get @comm/polled-metars airport)]
     (shorten-metar metar))))

(defn get-now []
  (System/currentTimeMillis))
