(ns skillBoard.core
  (:require
    [clojure.string :as str]
    [java-time.api :as time]
    [quil.core :as q]
    [quil.middleware :as m]
    [skillBoard.config :as config]
    [skillBoard.display16 :as display]
    [skillBoard.flight-schedule-pro :as fsp]
    [skillBoard.navigation :as nav]
    [skillBoard.radar-cape :as radar-cape]
    [skillBoard.sources :as sources]
    [skillBoard.weather :as weather]
    ))

(defn make-status-item [{:keys [tail-number
                                start-time
                                pilot-name
                                instructor-name
                                checked-out-on]}
                        flight]
  (let [item {:aircraft tail-number
              :time start-time
              :crew {:pilot pilot-name
                     :instructor instructor-name}
              :remarks "Reserved"
              }]
    (cond
      (and (nil? flight) (nil? checked-out-on))
      item

      (and (nil? flight) (some? checked-out-on))
      (assoc item
        :remarks "Checked out"
        :time checked-out-on)
      )
    )
  )


(defn format-name [[first-name last-name]]
  (if (nil? first-name)
    "     "
    (str/upper-case (str (subs last-name 0 3) "." (first first-name)))))

(defn format-res [{:keys [start-time tail-number pilot-name instructor-name co
                          altitude track ground-speed lat-lon rogue?] :as res}]
  (let [[tower-lat tower-lon] (:tower-lat-lon @config/config)
        [lat lon] lat-lon
        {:keys [distance bearing]} (if (nil? lat) {} (nav/dist-and-bearing tower-lat tower-lon lat lon))]
    (format "%5sZ %-6s %5s %5s %6s %2s %5s %3s %3s %s"
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
        span " TIME   TAIL     CREW       OUT   AL DIS   BRG  GS   "
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


(defn setup []
  (q/frame-rate 10)
  (q/background 255)
  (config/load-config)
  {:time (System/currentTimeMillis)
   :lines (generate-summary)}
  )

(defn update-state [{:keys [time] :as state}]
  (let [now (System/currentTimeMillis)
        since (- now time)]
    (if (> since 20000)
      {:time now :lines (generate-summary)}
      state)))

(defn draw-state [state]
  (q/background 0 0 0)
  (q/no-fill)
  (q/stroke 0)
  (let [display (display/build-character-display (/ (q/screen-width) 65))
        height (get-in display [:context :height])]
    (loop [lines (:lines state)
           y 10]
      (if (empty? lines)
        nil
        (let [line (first lines)]
          (q/with-translation
            [0 y]
            (display/draw-line display line))
          (recur (rest lines) (+ y height 10)))))))

(def size
  #?(
     :cljs
     [(max (- (.-scrollWidth (.-body js/document)) 20) 900)
      (max (- (.-innerHeight js/window) 25) 700)]
     :clj
     [(- (q/screen-width) 10) (- (q/screen-height) 40)]))

(defn on-close [_]
  (q/no-loop)
  (q/exit)                                                  ; Exit the sketch
  (println "Skill Board closed.")
  (System/exit 0))




(defn -main [& _args]
  (println "skillBoard has begun.")
  (q/defsketch skillBoard
               :title "Skill Board"
               :size size
               :setup setup
               :update update-state
               :draw draw-state
               :features []
               :middleware [m/fun-mode]
               :on-close on-close
               :host "skillBoard"))
