(ns skillBoard.presenter
  (:require
    [clojure.string :as str]
    [quil.core :as q]
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
        [remark color]  (if adsb? (generate-remark)
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

(defn make-short-metar []
  (let [metar (sources/get-metar weather/source config/airport)
        metar-text (:rawOb (first metar))
        short-metar (if (nil? metar-text)
                      "NO-METAR"
                      (-> metar-text
                          (str/split #"RMK")
                          first
                          (subs 6)))
        final-metar (if (> (count short-metar) config/cols)
                      (subs short-metar 0 config/cols)
                      short-metar)]
    {:line final-metar
     :color :white}))

(defn split-taf [raw-taf]
  (let [[_ taf-name tafs] (re-find #"(TAF (?:COR )?(?:AMD )?\w+)(.*)" raw-taf)
        tafs (str/replace tafs #"PROB30 TEMPO" "PROB30 TEMPX")
        tafs (str/replace tafs #"PROB40 TEMPO" "PROB40 TEMPX")
        taf-items (map str/trim (str/split tafs #"(?=FM|BECMG|PROB[34]0 TEMPX|PROB[34]0 \d|TEMPO)"))
        taf-items (map #(str/replace % "TEMPX" "TEMPO") taf-items)
        taf-lines (concat [taf-name] taf-items)]
    (map (fn [line] {:line line :color :white}) taf-lines)))

(defn make-taf-screen []
  (let [taf-response (sources/get-taf weather/source config/taf-airports)
        short-metar (make-short-metar)
        raw-tafs (map :rawTAF taf-response)
        tafs (flatten (map #(->> % split-taf (take 4)) raw-tafs))
        blank-line {:line "" :color :white}]
    (concat tafs [blank-line short-metar])))

(defn- make-flight-screen []
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

(defn make-flight-category-line [metar]
  (let [{:keys [fltCat icaoId visib cover clouds wspd wgst]} metar
        base (if (= cover "CLR")
               "    "
               (:base (first clouds)))
        fltCat (if (nil? fltCat) "    " fltCat)
        cover (if (nil? cover) "   " cover)
        base (if (nil? base) "     " base)
        wgst (if (nil? wgst) "   " (str "G" wgst))
        ctgy-line (format "%4s %4s %3s %5s %3s %2s%3s" icaoId fltCat cover base visib wspd wgst)]
    {:line ctgy-line :color :white}))

(defn make-flight-category-screen []
  (let [metars (sources/get-metar weather/source config/flight-category-airports)
        fc-lines (map make-flight-category-line metars)]
    fc-lines)
  )

(def screen-type (atom nil))
(def screen-duration (atom 0))
(def screen-start-time (atom 0))

(defn make-screen []
  (let [time (System/currentTimeMillis)
        current-screen-seconds (quot (- time @screen-start-time) 1000)]
    (when (or (q/mouse-pressed?) (> current-screen-seconds @screen-duration))
      (reset! screen-type (:screen (first @config/screens)))
      (reset! screen-duration (:duration (first @config/screens)))
      (reset! screen-start-time time)
      (swap! config/screens rest))
    (condp = @screen-type
      :flights (make-flight-screen)
      :taf (make-taf-screen)
      :flight-category (make-flight-category-screen))
    ))

