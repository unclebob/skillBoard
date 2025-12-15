(ns skillBoard.presenters.utils
  (:require
    [clojure.string :as str]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.navigation :as nav]))

(defn blank? [s] (empty? (str/trim s)))

(defn flight-category-color [vis ceiling]
  (cond
    (and (>= vis 5.0) (>= ceiling 3000)) config/vfr-color
    (and (>= vis 3.0) (>= ceiling 1000)) config/mvfr-color
    (and (>= vis 1.0) (>= ceiling 500)) config/ifr-color
    :else config/lifr-color))

(defn find-location [my-lat my-lon my-alt geofences]
  (loop [fences geofences]
    (if (empty? fences)
      ""
      (let [{:keys [lat lon radius min-alt max-alt name]} (first fences)
            {:keys [distance]} (nav/dist-and-bearing lat lon my-lat my-lon)]
        (if (and (<= distance radius)
                 (<= min-alt my-alt max-alt))
          name
          (recur (rest fences)))))))

(defn by-distance [metar1 metar2]
  (let [lat1 (:lat metar1)
        lon1 (:lon metar1)
        lat2 (:lat metar2)
        lon2 (:lon metar2)
        [airport-lat airport-lon] config/airport-lat-lon
        dist1 (:distance (nav/dist-and-bearing airport-lat airport-lon lat1 lon1))
        dist2 (:distance (nav/dist-and-bearing airport-lat airport-lon lat2 lon2))]
    (if (or (nil? dist1) (nil? dist2))
      false
      (< dist1 dist2))))

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

(defn generate-position-remark [distance alt gs on-ground? lat lon]
  (let [nearby-threshold 2
        near-threshold 6
        low (+ config/airport-elevation 30)
        pattern-low (- config/pattern-altitude 500)
        pattern-high (+ config/pattern-altitude 500)
        geofences config/geofences
        nearby? (< distance nearby-threshold)
        flying-speed? (> gs 50)]
    (cond
      (and nearby? on-ground? (< gs 2)) "RAMP"
      (and nearby? on-ground? (<= 2 gs 25)) "TAXI"
      (and (< low alt pattern-low) flying-speed?) "LOW "
      (and nearby? (< pattern-low alt pattern-high) flying-speed?) "PATN"
      (< distance near-threshold) "NEAR"
      :else (find-location lat lon alt geofences))))
