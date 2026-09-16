(ns skillBoard.presenters.flights
  (:require
    [clojure.math :as math]
    [clojure.string :as str]
    [quil.core :as q]
    [skillBoard.atoms :as atoms]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.flight-schedule-pro :as fsp]
    [skillBoard.navigation :as nav]
    [skillBoard.presenters.screen :as screen]
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



(defn- format-tail-number [tail-number]
  (if (or (nil? tail-number)
          (str/blank? tail-number)
          (= tail-number "NULL"))
    "------"
    tail-number))

(defn- reservation-position [lat-lon]
  (let [[tower-lat tower-lon] config/airport-lat-lon
        [lat lon] lat-lon]
    (when (some? lat)
      (nav/dist-and-bearing tower-lat tower-lon lat lon))))

(defn- format-altitude [res on-ground?]
  (let [altitude (:altitude res)]
    (cond
      on-ground? "GND"
      (not (contains? res :altitude)) "   "
      (nil? altitude) "---"
      :else (format "%03d" (math/round (/ altitude 100.0))))))

(defn- no-position-data? [bearing distance altitude ground-speed]
  (and (nil? bearing)
       (nil? distance)
       (nil? altitude)
       (nil? ground-speed)))

(defn- format-three-digits [value]
  (if (nil? value) "   " (format "%03d" (math/round value))))

(defn- format-check-out-time [co]
  (if (nil? co)
    "      "
    (str (time-util/get-HHmm (time-util/local-to-utc co)) "Z")))

(defn- format-brg-alt-gs [bearing distance altitude ground-speed alt]
  (if (no-position-data? bearing distance altitude ground-speed)
    "                 "
    (format "%3s%s%s/%s/%s"
            config/bearing-center
            (format-three-digits bearing)
            (format-three-digits distance)
            alt
            (if (nil? ground-speed) "   " (format "%03d" ground-speed)))))

(defn- position-remark [{:keys [altitude ground-speed unscheduled? on-ground? lat-lon]} distance]
  (let [[lat lon] lat-lon
        altitude (or altitude 0)
        ground-speed (or ground-speed 0)
        low (+ config/airport-elevation 30)
        on-ground? (or on-ground? (< altitude low))
        remark (utils/generate-position-remark distance altitude ground-speed on-ground? lat lon)
        base-color (if (#{"RAMP" "TAXI"} remark) config/on-ground-color config/scheduled-flight-color)
        unscheduled-remark (if unscheduled? "NO-CO" "     ")
        color (if unscheduled? config/unscheduled-flight-color base-color)]
    [(format "%s %s" remark unscheduled-remark) color]))

(defn- format-remark [res distance]
  (if (:adsb? res)
    (position-remark res distance)
    ["            " config/scheduled-flight-color]))

(defn format-res [{:keys [start-time tail-number pilot-name instructor-name co
                          altitude ground-speed lat-lon on-ground?] :as res}]
  (let [{:keys [distance bearing]} (or (reservation-position lat-lon) {})
        alt (format-altitude res on-ground?)
        check-out-time (format-check-out-time co)
        brg-alt-gs (format-brg-alt-gs bearing distance altitude ground-speed alt)
        [remark color] (format-remark res distance)
        line (format "%5sZ %-6s %5s %5s %6s %s %s               "
                     (time-util/get-HHmm (time-util/local-to-utc start-time))
                     (format-tail-number tail-number)
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
        final-reservations (radar-cape/include-unscheduled-flights
                             updated-reservations
                             adsb-map)
        report (map format-res final-reservations)
        flight-count (- (:line-count @config/display-info) 2)
        blank-line (apply str (repeat config/cols " "))
        padded-items (concat report
                             (repeat flight-count {:line blank-line :color config/info-color}))
        displayed-items (take flight-count padded-items)
        dropped-items (count (drop flight-count report))
        footer {:color config/info-color
                :line (if (zero? dropped-items)
                        "             "
                        (format "...%2d MORE..." dropped-items))}
        final-screen (concat displayed-items [footer short-metar])
        ]
    final-screen))

(defmethod screen/make :flights [_]
  (make-flights-screen @comm/polled-reservations @comm/polled-flights))

(defmethod screen/header-text :flights [_]
  "FLIGHT OPERATIONS")

(defmethod screen/display-column-headers :flights [_ flap-width header-font label-font-size]
  (screen/draw-column-headers flap-width header-font label-font-size
                              [["TIME" 0]
                               ["AIRCRAFT" 7]
                               ["CREW" 14]
                               ["OUT" 26]
                               ["BRG/ALT/GS" 33]
                               ["REMARKS" 51]]))
