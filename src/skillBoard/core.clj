(ns skillBoard.core
  (:require
    [quil.core :as q]
    [quil.middleware :as m]
    [skillBoard.atoms :as atoms]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.core-utils :as core-utils]
    [skillBoard.heartbeat :as heartbeat]
    [skillBoard.presenters.airports]
    [skillBoard.presenters.flights]
    [skillBoard.presenters.main :as presenter]
    [skillBoard.presenters.traffic]
    [skillBoard.presenters.weather]
    [skillBoard.presenters.wind-map]
    [skillBoard.split-flap :as split-flap]
    [skillBoard.text-util :as text]
    [skillBoard.wind-data :as wind-data]))

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
     :header-font-size header-font-size
     :label-font-size label-font-size
     :clock-font-size clock-font-size
     :annotation-font annotation-font
     :departure-icon (q/load-image "resources/flightlogo.png")}))

(defn update-state [state]
  (try
    (split-flap/do-update state)
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-07T11:29:50.343739-05:00", :module-hash "-2067677778", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 18, :hash "1802448842"} {:id "defn/load-display-info", :kind "defn", :line 20, :end-line 35, :hash "160311322"} {:id "defn-/load-fonts", :kind "defn-", :line 37, :end-line 48, :hash "-812031420"} {:id "defn/poll", :kind "defn", :line 50, :end-line 65, :hash "-271833523"} {:id "defn/poll-due?", :kind "defn", :line 67, :end-line 70, :hash "410359893"} {:id "defn/update-clock-pulse!", :kind "defn", :line 72, :end-line 73, :hash "-610959273"} {:id "defn/run-due-poll!", :kind "defn", :line 75, :end-line 79, :hash "1110707382"} {:id "defn/initialize-polling!", :kind "defn", :line 81, :end-line 83, :hash "-1928480721"} {:id "defn/run-polling-step!", :kind "defn", :line 85, :end-line 88, :hash "-1686392983"} {:id "defn/start-polling", :kind "defn", :line 90, :end-line 97, :hash "1314719583"} {:id "defn/setup", :kind "defn", :line 99, :end-line 140, :hash "1513703913"} {:id "defn/update-state", :kind "defn", :line 142, :end-line 147, :hash "260310114"} {:id "defn/draw-state", :kind "defn", :line 149, :end-line 154, :hash "-681475998"} {:id "defn/on-close", :kind "defn", :line 156, :end-line 161, :hash "-668574530"} {:id "defn/key-released", :kind "defn", :line 163, :end-line 168, :hash "-2145750210"} {:id "form/15/declare", :kind "declare", :line 170, :end-line 170, :hash "955651229"} {:id "defn/log-application-start!", :kind "defn", :line 172, :end-line 174, :hash "-885538958"} {:id "defn/-main", :kind "defn", :line 176, :end-line 194, :hash "-1206295817"}]}
;; clj-mutate-manifest-end
