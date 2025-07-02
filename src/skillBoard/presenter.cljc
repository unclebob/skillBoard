(ns skillBoard.presenter
  (:require
    [clojure.string :as str]
    [java-time.api :as time]
    [skillBoard.config :as config]
    [skillBoard.flight-schedule-pro :as fsp]
    [skillBoard.navigation :as nav]
    [skillBoard.radar-cape :as radar-cape]
    [skillBoard.sources :as sources]
    [skillBoard.weather :as weather]
    ))

(defn format-name [[first-name last-name]]
  (if (nil? first-name)
    "     "
    (str/upper-case (str (subs last-name 0 3) "." (first first-name)))))

(defn format-res [{:keys [start-time tail-number pilot-name instructor-name co
                          altitude track ground-speed lat-lon rogue?] :as res}]
  (let [[tower-lat tower-lon] (:tower-lat-lon @config/config)
        [lat lon] lat-lon
        {:keys [distance bearing]} (if (nil? lat) {} (nav/dist-and-bearing tower-lat tower-lon lat lon))]
    (format "%5sZ %-6s %5s %5s %6s %2s %5s %3s %3s %s         "
            (fsp/get-HHmm (fsp/local-to-utc start-time))
            tail-number
            (format-name pilot-name)
            (format-name instructor-name)
            (if (nil? co) "" (str (fsp/get-HHmm (fsp/local-to-utc co)) "Z"))
            (cond
              (not (contains? res :altitude)) "  "
              (nil? altitude) "--"
              :else (format "%03d" (Math/round (/ altitude 100.0))))
            (if (nil? distance) "   " (format "%3dNM" (Math/round distance)))
            (if (nil? bearing) "   " (format "%03d" (Math/round bearing)))
            (if (nil? ground-speed) "   " (format "%3d" ground-speed))
            (if rogue? "ROGUE" ""))))

(defn header []
  (let [now (fsp/get-HHmm (fsp/local-to-utc (time/local-date-time)))
        span " TIME   TAIL     CREW       OUT  ALT DIS   BRG  GS   "
        time-stamp (str span now "Z")]
    time-stamp
    ))

(defn generate-summary []
  (let [active-aircraft (sources/get-aircraft fsp/source)
        metar (sources/get-metar weather/source (:airport @config/config))
        metar-text (:rawOb (first metar))
        short-metar (if (nil? metar-text) "NO-METAR"
                                          (-> metar-text
                                              (str/split #"RMK")
                                              first))
        reservations-packet (sources/get-reservations fsp/source)
        unpacked-res (fsp/unpack-reservations reservations-packet)
        flights-packet (sources/get-flights fsp/source)
        flights (fsp/unpack-flights flights-packet)
        filtered-reservations (fsp/sort-and-filter-reservations unpacked-res flights)
        adsbs (radar-cape/get-adsb radar-cape/source active-aircraft)
        adsbs {"N345TS" {:reg "N345TS" :lat 38.0 :lon -76.0 :alt 10000 :spd 150 :trk 90}}
        updated-reservations (radar-cape/update-with-adsb filtered-reservations adsbs)
        final-reservations (radar-cape/include-unreserved-flights
                             updated-reservations
                             adsbs)
        summary-lines (map format-res final-reservations)
        report (concat [(header)] summary-lines)
        displayed-items (take 20 report)
        dropped-items (count (drop 20 report))
        footer (if (zero? dropped-items) "" (format "...%d MORE..." dropped-items))
        final-display (concat displayed-items [footer short-metar])
        ]
    final-display))

(defn add-remaining-flappers [flappers remainder col row type]
  (if (empty? remainder)
    flappers
    (recur (conj flappers {:at [col row]
                           (if (= type :old) :to :from) \space
                           (if (= type :old ) :from :to) (first remainder)})
           (rest remainder)
           (inc col) row type))
  )

(defn make-flappers-for-line [new-line old-line row flappers]
  (loop [new-line new-line
         old-line old-line
         col 0
         flappers flappers]
    (cond
      (empty? new-line) (add-remaining-flappers flappers old-line col row :old)
      (empty? old-line) (add-remaining-flappers flappers new-line col row :new)

      :else
      (let [char-new (first new-line)
            char-old (first old-line)]
        (if (= char-new char-old)
          (recur (rest new-line) (rest old-line) (inc col) flappers)
          (recur (rest new-line) (rest old-line) (inc col)
                 (conj flappers {:at [col row] :from char-old :to char-new})))))))

(defn make-flappers [new-report old-report]
  (loop [new-report new-report
         old-report old-report
         row 0
         flappers []]
    (cond
      (and (empty? new-report) (empty? old-report))  flappers
      :else
      (recur (rest new-report)
             (rest old-report)
             (inc row)
             (make-flappers-for-line (first new-report)
                                     (first old-report)
                                     row
                                     flappers)))))