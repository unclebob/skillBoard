(ns skillBoard.presenter
  (:require
    [clojure.string :as str]
    [skillBoard.config :as config]
    [skillBoard.config :as config]
    [skillBoard.flight-schedule-pro :as fsp]
    [skillBoard.navigation :as nav]
    [skillBoard.radar-cape :as radar-cape]
    [skillBoard.sources :as sources]
    [skillBoard.time-util :as time-util]
    [skillBoard.weather :as weather]
    ))

(def test? (atom false))

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
  (let [[tower-lat tower-lon] config/airport-lat-lon
        [lat lon] lat-lon
        {:keys [distance bearing]} (if (nil? lat) {} (nav/dist-and-bearing tower-lat tower-lon lat lon))
        tail-number (if (or (nil? tail-number)
                            (str/blank? tail-number)
                            (= tail-number "NULL"))
                      "------"
                      tail-number)

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

                             rogue-remark (if rogue? "NO-CO" "     ")
                             ]
                            (format "%s %s" position-remark rogue-remark)))

        alt (cond
              on-ground? "GND"
              (not (contains? res :altitude)) "   "
              (nil? altitude) "---"
              :else (format "%03d" (Math/round (/ altitude 100.0))))
        no-brg-alt-gs? (and (nil? bearing)
                            (nil? distance)
                            (nil? altitude)
                            (nil? ground-speed))
        ground-speed (if (nil? ground-speed) "   " (format "%03d" ground-speed))
        bearing (if (nil? bearing) "   " (format "%03d" (Math/round bearing)))
        distance (if (nil? distance) "   " (format "%03d" (Math/round distance)))
        check-out-time (if (nil? co)
                         "      "
                         (str (time-util/get-HHmm (time-util/local-to-utc co)) "Z"))
        brg-alt-gs (if no-brg-alt-gs?
                     "                 "
                     (format "%3s%s%s/%s/%s"
                             config/bearing-center
                             bearing
                             distance
                             alt
                             ground-speed))
        line (format "%5sZ %-6s %5s %5s %6s %s %s               "
                     (time-util/get-HHmm (time-util/local-to-utc start-time))
                     tail-number
                     (format-name pilot-name)
                     (format-name instructor-name)
                     check-out-time
                     brg-alt-gs
                     (if adsb? (generate-remark) "            "))]
    (subs line 0 config/cols)))

(defn make-short-metar []
  (let [metar (sources/get-metar weather/source config/airport)
        metar-text (:rawOb (first metar))
        short-metar (if (nil? metar-text)
                      "NO-METAR"
                      (-> metar-text
                          (str/split #"RMK")
                          first
                          (subs 6)))]
    (if (> (count short-metar) config/cols)
      (subs short-metar 0 config/cols)
      short-metar)))

(defn split-taf [raw-taf]
  (let [taf-name (subs raw-taf 0 8)
        tafs (subs raw-taf 9)
        taf-items (str/split tafs #"(?=FM|BECMG)")]
    (concat [taf-name] taf-items)))

(defn make-taf-screen []
  (let [taf (sources/get-taf weather/source config/taf-airport)
        short-metar (make-short-metar)
        raw-taf (:rawTAF (first taf))
        tafs (split-taf raw-taf)]
    (concat tafs ["" "" "" short-metar])))

(defn make-flight-screen []
  (let [active-aircraft (sources/get-aircraft fsp/source)
        short-metar (make-short-metar)
        reservations-packet (sources/get-reservations fsp/source)
        unpacked-res (fsp/unpack-reservations reservations-packet)
        flights-packet (sources/get-flights fsp/source)
        flights (fsp/unpack-flights flights-packet)
        filtered-reservations (fsp/sort-and-filter-reservations unpacked-res flights)
        adsbs (if @test?
                {"N345TS" {:reg "N345TS" :lat 42.5960633 :lon -87.9273236 :altg 3000 :spd 100 :gda "A"}
                 "N378MA" {:reg "N378MA" :lat 42.4221486 :lon -87.8679161 :gda "G"}}
                (radar-cape/get-adsb radar-cape/source active-aircraft))

        updated-reservations (radar-cape/update-with-adsb filtered-reservations adsbs)
        final-reservations (radar-cape/include-unreserved-flights
                             updated-reservations
                             adsbs)
        report (map format-res final-reservations)
        flights (:flights @config/display-info)
        padded-items (concat report (repeat flights (apply str (repeat config/cols " "))))
        displayed-items (take flights padded-items)
        dropped-items (count (drop flights report))
        footer (if (zero? dropped-items)
                 "             "
                 (format "...%2d MORE..." dropped-items))
        final-screen (concat displayed-items [footer short-metar])
        ]
    final-screen))

(def screen-type (atom :flights))

(defn make-screen []
  (let [time (System/currentTimeMillis)
        seconds (mod (quot time 1000) 60)]
    (if (< seconds 30)
      (reset! screen-type :flights)
      (reset! screen-type :taf))
    (condp = @screen-type
      :flights (make-flight-screen)
      :taf (make-taf-screen))))

