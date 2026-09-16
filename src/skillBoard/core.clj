(ns skillBoard.core
  (:require
    [quil.core :as q]
    [quil.middleware :as m]
    [skillBoard.foundation.atoms :as atoms]
    [skillBoard.gateways.comm-utils :as comm]
    [skillBoard.foundation.config :as config]
    [skillBoard.foundation.core-utils :as core-utils]
    [skillBoard.adapters.heartbeat :as heartbeat]
    [skillBoard.presenters.airports]
    [skillBoard.presenters.flights]
    [skillBoard.presenters.main :as presenter]
    [skillBoard.presenters.traffic]
    [skillBoard.presenters.weather]
    [skillBoard.presenters.wind-map]
    [skillBoard.adapters.split-flap :as split-flap]
    [skillBoard.adapters.text-util :as text]
    [skillBoard.gateways.wind-data :as wind-data]))

(defn load-display-info []
  (let [screen-width (q/width)
        useful-width (- screen-width 10)
        screen-height (q/height)
        char-width (/ useful-width config/cols)
        sf-char-gap (* char-width config/sf-char-gap)
        font-width (- char-width sf-char-gap)
        sf-font-size (text/find-font-size-for-width (:sf-font @config/display-info) font-width)]
    (swap! config/display-info
           assoc
           :size [screen-width screen-height]
           :top-margin (* screen-height config/header-height-fraction)
           :label-height (* screen-height config/label-height-fraction)
           :sf-font-size sf-font-size
           :sf-char-gap sf-char-gap
           )))

(defn- load-fonts []
  (let [sf-font (q/create-font "Skyfont" 32)
        header-font (q/create-font "Bahnschrift" 50)
        annotation-font (q/create-font "Times New Roman" 9)
        metar-font (q/create-font "Courier New" 18)
        clock-font (q/create-font "DSEG7 Modern" 32)]
    (swap! config/display-info assoc
           :sf-font sf-font
           :header-font header-font
           :annotation-font annotation-font
           :metar-font metar-font
           :clock-font clock-font)))

(defn board-snapshot []
  {:reservations @comm/polled-reservations
   :flights @comm/polled-flights
   :adsbs @comm/polled-adsbs
   :metars @comm/polled-metars
   :nearby-metars @comm/polled-nearby-metars
   :airspace-classes @comm/polled-airspace-classes
   :metar-history @comm/polled-metar-history
   :tafs @comm/polled-tafs
   :nearby-adsbs @comm/polled-nearby-adsbs
   :aircraft @comm/polled-aircraft
   :wind-grid (wind-data/current-grid)
   :test? @atoms/test?
   :com-errors {:reservations @comm/reservation-com-errors
                :adsb @comm/adsb-com-errors
                :weather @comm/weather-com-errors
                :open-meteo-ok? @comm/open-meteo-ok?}})

(defn poll []
  (try
    (comm/get-aircraft)
    (comm/get-adsb-by-tail-numbers @comm/polled-aircraft)
    (comm/get-flights)
    (comm/get-reservations)
    (comm/get-metars config/flight-category-airports)
    (comm/get-nearby-metars)
    (comm/get-airspace-classes)
    (comm/get-metar-history config/airport)
    (comm/get-tafs config/taf-airport)
    (comm/get-nearby-adsb)
    (wind-data/refresh-wind-grid-if-due!)
    (reset! atoms/log-traffic? true)
    (catch Exception e
      (core-utils/log :error e))))

(defn poll-due? [now]
  (let [seconds-since-last-poll (quot (- now @atoms/poll-time) 1000)]
    (or @atoms/poll-key
        (>= seconds-since-last-poll config/seconds-between-internet-polls))))

(defn update-clock-pulse! [now]
  (reset! atoms/clock-pulse (< 500 (mod now 1000))))

(defn run-due-poll! [now]
  (when (poll-due? now)
    (future (poll))
    (reset! atoms/poll-key false)
    (reset! atoms/poll-time now)))

(defn initialize-polling! []
  (poll)
  (heartbeat/start!))

(defn run-polling-step! [now]
  (run-due-poll! now)
  (heartbeat/run-due! now)
  (update-clock-pulse! now))

(defn start-polling []
  (initialize-polling!)
  (future
    (loop []
      (let [now (System/currentTimeMillis)]
        (run-polling-step! now)
        (Thread/sleep 100)
        (recur)))))

(defn setup []
  (core-utils/log :status "Setup...")
  (load-fonts)
  (config/load-config)
  (load-display-info)
  (start-polling)
  (let [{:keys [sf-font-size sf-font header-font annotation-font clock-font
                size top-margin label-height]} @config/display-info
        _ (q/text-font sf-font)
        _ (q/text-size sf-font-size)
        font-width (q/text-width "X")
        font-height (+ (q/text-ascent) (q/text-descent))
        header-font-size (text/find-font-size-for-height header-font (* 0.7 top-margin))
        label-font-size (text/find-font-size-for-height header-font (* 0.8 label-height))
        clock-font-size (text/find-font-size-for-height clock-font (* 0.5 top-margin))
        text-area-height (- (second size) top-margin label-height)
        line-height (* font-height (+ 1 config/sf-line-gap))
        lines-count (quot text-area-height line-height)
        _ (swap! config/display-info assoc
                 :font-width font-width
                 :font-height font-height
                 :line-count lines-count)
        snapshot (board-snapshot)
        summary (presenter/make-screen snapshot)
        flappers (split-flap/make-flappers summary [])
        now (System/currentTimeMillis)]
    (q/frame-rate config/frame-rate)
    (q/background 255)
    {:time now
     :snapshot snapshot
     :lines summary
     :flappers flappers
     :sf-font sf-font
     :sf-font-size sf-font-size
     :clock-font clock-font
     :font-width font-width
     :font-height font-height
     :line-count lines-count
     :header-font header-font
     :header-font-size header-font-size
     :label-font-size label-font-size
     :clock-font-size clock-font-size
     :annotation-font annotation-font
     :departure-icon (q/load-image "resources/flightlogo.png")}))

(defn update-state [state]
  (try
    (split-flap/do-update (assoc state :snapshot (board-snapshot)))
    (catch Exception e
      (core-utils/log :error e)
      state)))

(defn draw-state [state]
  (try
    (split-flap/draw state)
    (catch Exception e
      (core-utils/log :error e)
      state)))

(defn on-close [_]
  (q/no-loop)
  (q/exit)                                                  ; Exit the sketch
  (heartbeat/stop!)
  (core-utils/log :status "Skill Board closed.")
  (System/exit 0))

(defn key-released [state event]
  (condp = (:key event)
    :p (reset! atoms/poll-key true)
    :space (reset! atoms/change-screen? true)
    nil)
  state)

(declare skillBoard)

(defn log-application-start! []
  (core-utils/log-event :status :application-start
                        (str "skillBoard v" config/version " has begun.")))

(defn -main [& args]
  (log-application-start!)
  (let [args (set args)
        window? (some? (args "-w"))
        _ (reset! atoms/test? (some? (args "-t")))
        _ (reset! atoms/log-stdout? (nil? (args "--no-stdout")))
        _ (core-utils/log :status (str "args: " args ", window? " window? ", test? " @atoms/test?))
        ]
    (q/defsketch skillBoard
                 :title "Skill Board"
                 :size :fullscreen
                 :setup setup
                 :update update-state
                 :draw draw-state
                 :features (if window? [] [:present])
                 :middleware [m/fun-mode]
                 :on-close on-close
                 :key-released key-released
                 :host "skillBoard")))
