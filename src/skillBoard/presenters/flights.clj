(ns skillBoard.presenters.flights
  (:require
    [clojure.math :as math]
    [clojure.string :as str]
    [skillBoard.atoms :as atoms]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.flight-schedule-pro :as fsp]
    [skillBoard.navigation :as nav]
    [skillBoard.presenters.screen]
    [skillBoard.presenters.utils :as utils]
    [skillBoard.radar-cape :as radar-cape]
    [skillBoard.time-util :as time-util]))

(defn format-name [[first-name last-name]]
  (let [first-name (if (nil? first-name) "" first-name)
        last-name (if (nil? last-name) "" last-name)
        padded-first (str (str/trim first-name) "     ")
        padded-last (str (str/trim last-name) "     ")]
    (cond
      (and (utils/blank? first-name) (utils/blank? last-name)) "     "
      (utils/blank? last-name) (str/upper-case (subs padded-first 0 5))
      (utils/blank? first-name) (str/upper-case (subs padded-last 0 5))
      :else (str/upper-case (str (subs padded-last 0 3) "." (first padded-first))))))

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

        generate-remark
        (fn []
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
             [position-remark color]
             (cond
               (and nearby? on-ground? (< ground-speed 2)) ["RAMP" :green]
               (and nearby? on-ground? (<= min-taxi ground-speed max-taxi)) ["TAXI" :green]
               (and (< low altitude pattern-low) flying-speed?) ["LOW " :white]
               (and nearby? (< pattern-low altitude pattern-high) flying-speed?) ["PATN" :white]
               (< distance 6) ["NEAR" :white]
               :else [(find-location lat lon altitude config/geofences) :white])

             rogue-remark (if rogue? "NO-CO" "     ")
             color (if rogue? :blue color)
             ]
            [(format "%s %s" position-remark rogue-remark) color]))

        alt (cond
              on-ground? "GND"
              (not (contains? res :altitude)) "   "
              (nil? altitude) "---"
              :else (format "%03d" (math/round (/ altitude 100.0))))
        no-brg-alt-gs? (and (nil? bearing)
                            (nil? distance)
                            (nil? altitude)
                            (nil? ground-speed))
        ground-speed (if (nil? ground-speed) "   " (format "%03d" ground-speed))
        bearing (if (nil? bearing) "   " (format "%03d" (math/round bearing)))
        distance (if (nil? distance) "   " (format "%03d" (math/round distance)))
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
        [remark color] (if adsb? (generate-remark)
                                 ["            " :white])
        line (format "%5sZ %-6s %5s %5s %6s %s %s               "
                     (time-util/get-HHmm (time-util/local-to-utc start-time))
                     tail-number
                     (format-name pilot-name)
                     (format-name instructor-name)
                     check-out-time
                     brg-alt-gs
                     remark)]
    {:line (subs line 0 config/cols)
     :color color}))

(defn make-adsb-tail-number-map [adsbs]
  (apply hash-map
         (flatten
           (for [adsb adsbs]
             [(get adsb :reg)
              adsb])))
  )

(defn make-flights-screen [reservations-packet flights-packet]
  (let [short-metar (utils/get-short-metar)
        unpacked-res (fsp/unpack-reservations reservations-packet)
        flights (fsp/unpack-flights flights-packet)
        filtered-reservations (fsp/sort-and-filter-reservations unpacked-res flights)
        adsbs @comm/polled-adsbs
        adsb-map (if @atoms/test?
                   {"N345TS" {:reg "N345TS" :lat 42.5960633 :lon -87.9273236 :altg 3000 :spd 100 :gda "A"}
                    "N378MA" {:reg "N378MA" :lat 42.4221486 :lon -87.8679161 :gda "G"}}
                   (make-adsb-tail-number-map adsbs))

        updated-reservations (radar-cape/update-with-adsb filtered-reservations adsb-map)
        final-reservations (radar-cape/include-unreserved-flights
                             updated-reservations
                             adsb-map)
        report (map format-res final-reservations)
        flight-count (- (:line-count @config/display-info) 2)
        blank-line (apply str (repeat config/cols " "))
        padded-items (concat report
                             (repeat flight-count {:line blank-line :color :white}))
        displayed-items (take flight-count padded-items)
        dropped-items (count (drop flight-count report))
        footer {:color :white
                :line (if (zero? dropped-items)
                        "             "
                        (format "...%2d MORE..." dropped-items))}
        final-screen (concat displayed-items [footer short-metar])
        ]
    final-screen))

(defmethod skillBoard.presenters.screen/make :flights [_]
  (make-flights-screen @comm/polled-reservations @comm/polled-flights))