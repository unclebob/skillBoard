(ns skillBoard.core
  (:require
    [quil.core :as q]
    [quil.middleware :as m]
    [skillBoard.adapters.heartbeat :as heartbeat]
    [skillBoard.adapters.split-flap :as split-flap]
    [skillBoard.adapters.web.process :as web-process]
    [skillBoard.adapters.web.publish :as web-publish]
    [skillBoard.adapters.text-util :as text]
    [skillBoard.core.polling :as polling]
    [skillBoard.foundation.atoms :as atoms]
    [skillBoard.foundation.config :as config]
    [skillBoard.foundation.core-utils :as core-utils]
    [skillBoard.presenters.airports]
    [skillBoard.presenters.flights]
    [skillBoard.presenters.main :as presenter]
    [skillBoard.presenters.traffic]
    [skillBoard.presenters.weather]
    [skillBoard.presenters.wind-map]))

(defn- column-metrics [screen-width]
  (let [useful-width (- screen-width 10)
        char-width (/ useful-width config/cols)
        sf-char-gap (* char-width config/sf-char-gap)]
    {:font-width (- char-width sf-char-gap)
     :sf-char-gap sf-char-gap}))

(defn- screen-margins [screen-height]
  {:top-margin (* screen-height config/header-height-fraction)
   :label-height (* screen-height config/label-height-fraction)})

(defn- text-line-count [screen-height top-margin label-height font-height]
  (let [text-area-height (- screen-height top-margin label-height)
        line-height (* font-height (+ 1 config/sf-line-gap))]
    (quot text-area-height line-height)))

(defn- fitted-font-sizes [header-font clock-font top-margin label-height]
  {:header-font-size (text/find-font-size-for-height header-font (* 0.7 top-margin))
   :label-font-size (text/find-font-size-for-height header-font (* 0.8 label-height))
   :clock-font-size (text/find-font-size-for-height clock-font (* 0.5 top-margin))})

(defn- initial-sketch-state
  [{:keys [sf-font sf-font-size clock-font font-width font-height line-count
           header-font header-font-size label-font-size clock-font-size
           annotation-font departure-icon]}
   snapshot summary flappers now]
  {:time now
   :snapshot snapshot
   :lines summary
   :flappers flappers
   :sf-font sf-font
   :sf-font-size sf-font-size
   :clock-font clock-font
   :font-width font-width
   :font-height font-height
   :line-count line-count
   :header-font header-font
   :header-font-size header-font-size
   :label-font-size label-font-size
   :clock-font-size clock-font-size
   :annotation-font annotation-font
   :departure-icon departure-icon})

(defn- launch-options [args]
  (let [args (set args)]
    {:args args
     :window? (some? (args "-w"))
     :test? (some? (args "-t"))
     :no-heartbeat? (some? (args "--no-heartbeat"))
     :log-stdout? (nil? (args "--no-stdout"))}))

(defn- install-launch-options! [{:keys [test? log-stdout? no-heartbeat?]}]
  (reset! atoms/test? test?)
  (reset! atoms/log-stdout? log-stdout?)
  (reset! atoms/no-heartbeat? no-heartbeat?))

(defn load-display-info []
  (let [screen-width (q/width)
        screen-height (q/height)
        {:keys [font-width sf-char-gap]} (column-metrics screen-width)
        {:keys [top-margin label-height]} (screen-margins screen-height)
        sf-font-size (text/find-font-size-for-width (:sf-font @config/display-info) font-width)]
    (swap! config/display-info
           assoc
           :size [screen-width screen-height]
           :top-margin top-margin
           :label-height label-height
           :sf-font-size sf-font-size
           :sf-char-gap sf-char-gap)))

(defn load-fonts []
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

(defn setup []
  (core-utils/log :status "Setup...")
  (load-fonts)
  (config/load-config)
  (load-display-info)
  (polling/start-polling)
  (let [{:keys [sf-font-size sf-font header-font annotation-font clock-font
                size top-margin label-height]} @config/display-info
        _ (q/text-font sf-font)
        _ (q/text-size sf-font-size)
        font-width (q/text-width "X")
        font-height (+ (q/text-ascent) (q/text-descent))
        lines-count (text-line-count (second size) top-margin label-height font-height)
        font-sizes (fitted-font-sizes header-font clock-font top-margin label-height)
        _ (swap! config/display-info assoc
                 :font-width font-width
                 :font-height font-height
                 :line-count lines-count)
        snapshot (polling/board-snapshot)
        summary (presenter/make-screen snapshot)
        flappers (split-flap/make-flappers summary [])
        now (System/currentTimeMillis)]
    (q/frame-rate config/frame-rate)
    (q/background 255)
    (initial-sketch-state
      (merge {:sf-font sf-font
              :sf-font-size sf-font-size
              :clock-font clock-font
              :font-width font-width
              :font-height font-height
              :line-count lines-count
              :header-font header-font
              :annotation-font annotation-font
              :departure-icon (q/load-image "resources/flightlogo.png")}
             font-sizes)
      snapshot summary flappers now)))

(defn update-state [state]
  (try
    (split-flap/do-update (assoc state :snapshot (web-publish/remember! (polling/board-snapshot))))
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
  (q/exit)
  (web-process/stop!)
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
  (let [{:keys [args window?] :as options} (launch-options args)]
    (install-launch-options! options)
    (core-utils/log :status (str "args: " args ", window? " window? ", test? " @atoms/test?))
    (web-process/start!)
    (web-publish/start!)
    (core-utils/log :status (str "web board http://127.0.0.1:" web-process/port))
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
