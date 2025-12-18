(ns skillBoard.core
  (:require
    [quil.core :as q]
    [quil.middleware :as m]
    [skillBoard.atoms :as atoms]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.core-utils :as core-utils]
    [skillBoard.presenters.airports]
    [skillBoard.presenters.flights]
    [skillBoard.presenters.main :as presenter]
    [skillBoard.presenters.traffic]
    [skillBoard.presenters.weather]
    [skillBoard.split-flap :as split-flap]
    [skillBoard.text-util :as text]))

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
        clock-font (q/create-font "DSEG7 Modern" 32)]
    (swap! config/display-info assoc
           :sf-font sf-font
           :header-font header-font
           :annotation-font annotation-font
           :clock-font clock-font)))

(defn poll []
  (try
    (comm/get-aircraft)
    (comm/get-adsb-by-tail-numbers @comm/polled-aircraft)
    (comm/get-flights)
    (comm/get-reservations)
    (comm/get-metars config/all-metars)
    (comm/get-metar-history config/airport)
    (comm/get-tafs config/all-metars)
    (comm/get-nearby-adsb)
    (catch Exception e
      (core-utils/log e))))

(defn start-polling []
  (poll)
  (future
    (loop []
      (let [now (System/currentTimeMillis)
            seconds-since-last-poll (quot (- now @atoms/poll-time) 1000)]
        (when (or @atoms/poll-key
                  (>= seconds-since-last-poll config/seconds-between-internet-polls))
          (future (poll))
          (reset! atoms/poll-key false)
          (reset! atoms/poll-time now))
        (reset! atoms/clock-pulse (< 500 (mod now 1000)))
        (Thread/sleep 100)
        (recur))
      )
    ))

(defn setup []
  (core-utils/log "Setup...")
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
        text-area-height (- (second size) top-margin label-height)
        line-height (* font-height (+ 1 config/sf-line-gap))
        lines-count (quot text-area-height line-height)
        _ (swap! config/display-info assoc
                 :font-width font-width
                 :font-height font-height
                 :line-count lines-count)
        summary (presenter/make-screen)
        flappers (split-flap/make-flappers summary [])
        now (System/currentTimeMillis)]
    (q/frame-rate config/frame-rate)
    (q/background 255)
    {:time now
     :lines summary
     :flappers flappers
     :sf-font sf-font
     :sf-font-size sf-font-size
     :clock-font clock-font
     :font-width font-width
     :font-height font-height
     :line-count lines-count
     :header-font header-font
     :annotation-font annotation-font
     :departure-icon (q/load-image "resources/flightlogo.png")}))

(defn update-state [state]
  (try
    (split-flap/do-update state)
    (catch Exception e
      (core-utils/log e)
      state)))

(defn draw-state [state]
  (try
    (let [now (System/currentTimeMillis)]
      (split-flap/draw state)
      (when @atoms/test?
        (swap! atoms/draw-time-accumulator + (- (System/currentTimeMillis) now))
        (when (>= (- now @atoms/draw-time-start) 1000)
          (prn "Draw time:" @atoms/draw-time-accumulator "ms")
          (reset! atoms/draw-time-start now)
          (reset! atoms/draw-time-accumulator 0))))
    (catch Exception e
      (core-utils/log e)
      state)))

(defn on-close [_]
  (q/no-loop)
  (q/exit)                                                  ; Exit the sketch
  (println "Skill Board closed.")
  (System/exit 0))

(defn key-released [state event]
  (condp = (:key event)
    :p (reset! atoms/poll-key true)
    :space (reset! atoms/change-screen? true)
    nil)
  state)

(declare skillBoard)

(defn -main [& args]
  (core-utils/log "skillBoard has begun.")
  (let [args (set args)
        window? (some? (args "-w"))
        _ (reset! atoms/test? (some? (args "-t")))
        _ (core-utils/log (str "args: " args ", window? " window? ", test? " @atoms/test?))
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
