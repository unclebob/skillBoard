(ns skillBoard.presenter
  (:require
    [clojure.string :as str]
    [java-time.api :as time]
    [skillBoard.config :as config]
    [skillBoard.config :as config]
    [skillBoard.flight-schedule-pro :as fsp]
    [skillBoard.navigation :as nav]
    [skillBoard.radar-cape :as radar-cape]
    [skillBoard.sources :as sources]
    [skillBoard.time-util :as time-util]
    [skillBoard.weather :as weather]
    ))

(defn format-name [[first-name last-name]]
  (if (nil? first-name)
    "     "
    (str/upper-case (str (subs last-name 0 3) "." (first first-name)))))

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

(defn format-res [{:keys [start-time tail-number pilot-name instructor-name co
                          altitude ground-speed lat-lon rogue? on-ground? adsb?] :as res}]
  (let [[tower-lat tower-lon] config/tower-lat-lon
        [lat lon] lat-lon
        {:keys [distance bearing]} (if (nil? lat) {} (nav/dist-and-bearing tower-lat tower-lon lat lon))
        generate-remark (fn []
                          (let
                            [altitude (or altitude 0)
                             nearby? (< distance 2)
                             ground-speed (or ground-speed 0)
                             low (+ config/airport-elevation 30)
                             on-ground? (or on-ground? (< altitude low))
                             pattern-low (- config/pattern-altitude 500)
                             pattern-high (+ config/pattern-altitude 500)
                             flying-speed? (> ground-speed 50)
                             max-taxi 25
                             min-taxi 2
                             position-remark
                             (cond
                               (and nearby? on-ground? (< ground-speed 2)) "RAMP"
                               (and nearby? on-ground? (<= min-taxi ground-speed max-taxi)) "TAXI"
                               (and (< low altitude pattern-low) flying-speed?) "LOW "
                               (and nearby? (< pattern-low altitude pattern-high) flying-speed?) "PATN"
                               (< distance 6) "NEAR"
                               :else (find-location lat lon altitude config/geofences))

                             rogue-remark (if rogue? "UNRSV" "     ")
                             ]
                            (format "%s %s" position-remark rogue-remark)))

        line (format "%5sZ %-6s %5s %5s %6s %2s %5s %3s %s %s               "
                     (time-util/get-HHmm (time-util/local-to-utc start-time))
                     tail-number
                     (format-name pilot-name)
                     (format-name instructor-name)
                     (if (nil? co) "      " (str (time-util/get-HHmm (time-util/local-to-utc co)) "Z"))
                     (cond
                       on-ground? "GND"
                       (not (contains? res :altitude)) "   "
                       (nil? altitude) "---"
                       :else (format "%03d" (Math/round (/ altitude 100.0))))
                     (if (nil? distance) "     " (format "%3dNM" (Math/round distance)))
                     (if (nil? bearing) "   " (format "%03d" (Math/round bearing)))
                     (if (nil? ground-speed) "   " (format "%3d" ground-speed))
                     (if adsb? (generate-remark) "            "))]
    (subs line 0 config/cols)))

(defn header []
  (let [now (time-util/get-HHmm (time-util/local-to-utc (time/local-date-time)))
        span " TIME   TAIL     CREW       OUT  ALT DIS   BRG  GS   "
        time-stamp (str span now "Z")]
    time-stamp
    ))

(defn generate-summary []
  (let [active-aircraft (sources/get-aircraft fsp/source)
        metar (sources/get-metar weather/source config/airport)
        metar-text (:rawOb (first metar))
        short-metar (if (nil? metar-text) "NO-METAR"
                                          (-> metar-text
                                              (str/split #"RMK")
                                              first))
        short-metar (if (> (count short-metar) config/cols) (subs short-metar 0 config/cols) short-metar)
        reservations-packet (sources/get-reservations fsp/source)
        unpacked-res (fsp/unpack-reservations reservations-packet)
        flights-packet (sources/get-flights fsp/source)
        flights (fsp/unpack-flights flights-packet)
        filtered-reservations (fsp/sort-and-filter-reservations unpacked-res flights)
        adsbs (radar-cape/get-adsb radar-cape/source active-aircraft)
        ;adsbs {"N345TS" {:reg "N345TS" :lat 42.5960633 :lon -87.9273236 :altg 3000 :spd 100 :gda "A"}
        ;       "N378MA" {:reg "N378MA" :lat 42.4221486 :lon -87.8679161 :spd 30 :gda "G"}
        ;       }
        updated-reservations (radar-cape/update-with-adsb filtered-reservations adsbs)
        final-reservations (radar-cape/include-unreserved-flights
                             updated-reservations
                             adsbs)
        report (map format-res final-reservations)
        padded-items (concat report (repeat config/flights (apply str (repeat config/cols " "))))
        displayed-items (take config/flights padded-items)
        dropped-items (count (drop config/flights report))
        footer (if (zero? dropped-items)
                 "             "
                 (format "...%2d MORE..." dropped-items))
        final-display (concat displayed-items [footer short-metar])
        ]
    final-display))



