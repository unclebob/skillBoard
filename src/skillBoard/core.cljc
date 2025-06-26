(ns skillBoard.core
  (:require
    [clojure.string :as str]
    [java-time.api :as time]
    [quil.core :as q]
    [quil.middleware :as m]
    [skillBoard.config :as config]
    [skillBoard.display16 :as display]
    [skillBoard.flight-schedule-pro :as fsp]
    [skillBoard.sources :as sources]
    [skillBoard.text-util :as text]
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

(defn format-res [{:keys [start-time tail-number pilot-name instructor-name co] :as res}]
  (format "%5sZ %-6s %5s %5s %6s"
          (fsp/get-HHmm (fsp/local-to-utc start-time))
          tail-number
          (format-name pilot-name)
          (format-name instructor-name)
          (if (nil? co) "" (str (fsp/get-HHmm (fsp/local-to-utc co)) "Z"))))

(defn format-flight [flight]
  (format "CO: %s, CI: %s, %s"
          (:checked-out-on flight)
          (:checked-in-on flight)
          (:reservation-id flight)))

(defn time-stamp []
  (let [now (fsp/get-HHmm (fsp/local-to-utc (time/local-date-time)))
        span (apply str (repeat 50 \space))
        time-stamp (str span now "Z")]
    time-stamp
    ))

(defn generate-summary []
  (let [metar (sources/get-metar weather/source (:airport @config/config))
        metar-text (:rawOb (first metar))
        short-metar (-> metar-text
                        (str/split #"RMK")
                        first)
        reservations-packet (sources/get-reservations fsp/source)
        unpacked-res (fsp/unpack-reservations reservations-packet)
        tail-numbers (map :tail-number (vals unpacked-res))
        flights-packet (sources/get-flights fsp/source)
        flights (fsp/unpack-flights flights-packet)
        ;flights-summary (map format-flight (vals flights))
        metar-text (text/wrap short-metar 60)
        ;adsbs (radar-cape/get-adsb radar-cape/source tail-numbers)
        filtered-reservations (fsp/sort-and-filter-reservations unpacked-res flights)
        summary-lines (map format-res filtered-reservations)
        ]
    ;(doseq [flight-summary flights-summary]
    ;  (prn flight-summary))
    ;(doseq [adsb adsbs]
    ;  (prn 'adsb adsb))
    (concat metar-text [(time-stamp)] summary-lines)))


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
               :features [:keep-on-top]
               :middleware [m/fun-mode]
               :on-close on-close
               :host "skillBoard"))
