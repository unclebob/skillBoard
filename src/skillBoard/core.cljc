(ns skillBoard.core
  (:require
    [quil.core :as q]
    [quil.middleware :as m]
    [skillBoard.config :as config]
    [skillBoard.presenter :as presenter]
    [skillBoard.split-flap :as split-flap]
    ))

(defn load-display-info []
  (let [screen-width (q/width)
        screen-height (q/height)
        char-width (/ screen-width config/cols)
        sf-char-gap (* char-width config/sf-char-gap)
        sf-font-size (/ (- char-width sf-char-gap) config/font-width-per-point)
        ]
    (reset! config/display-info
            {:size [screen-width screen-height]
             :top-margin (* screen-height config/header-height-fraction)
             :label-height (* screen-height config/label-height-fraction)
             :sf-font-size sf-font-size
             :sf-char-gap sf-char-gap
             })))

(defn setup []
  (config/load-config)
  (load-display-info)
  (let [font-size (:sf-font-size @config/display-info)
        sf-font (q/create-font "Split-Flap TV" font-size)
        header-font (q/create-font "Arial Rounded MT Bold" 50)
        _ (q/text-font sf-font)
        _ (q/text-size font-size)
        font-width (q/text-width " ")
        font-height (+ (q/text-ascent) (q/text-descent))
        flights-height (- (second (:size @config/display-info))
                          (:top-margin @config/display-info)
                          (:label-height @config/display-info))
        flight-height (* font-height (+ 1 config/sf-line-gap))
        flights (- (quot flights-height flight-height) 2)
        annotation-font (q/create-font "Times New Roman" 9)
        _ (swap! config/display-info assoc
                     :sf-font sf-font
                     :header-font header-font
                     :annotation-font annotation-font
                     :font-width font-width
                     :font-height font-height
                     :flights flights)
        summary (presenter/generate-summary)
        flappers (split-flap/make-flappers summary [])
        now (System/currentTimeMillis)]
    (q/frame-rate 10)
    (q/background 255)
    {:time now
     :lines summary
     :flappers flappers
     :sf-font sf-font
     :font-width font-width
     :font-height font-height
     :flights flights
     :header-font header-font
     :annotation-font annotation-font
     :departure-icon (q/load-image "resources/DepartureIcon.jpg")}))

(defn update-state [{:keys [time flappers lines] :as state}]
  (let [now (System/currentTimeMillis)
        since (- now time)
        poll? (> since 30000)
        old-summary lines
        summary (if poll?
                  (presenter/generate-summary)
                  old-summary)
        flappers (if poll?
                   (split-flap/make-flappers summary old-summary)
                   (-> flappers
                       split-flap/update-flappers
                       split-flap/update-flappers
                       split-flap/update-flappers)
                   )
        frame-rate (if (empty? flappers) 0.1 30.0)]
    (q/frame-rate frame-rate)
    (assoc state :time (if poll? now time)
                 :lines summary
                 :flappers flappers)))

(defn draw-state [state]
  ;(display16/draw-16-seg state)
  (split-flap/draw-split-flap state)
  )


(defn on-close [_]
  (q/no-loop)
  (q/exit)                                                  ; Exit the sketch
  (println "Skill Board closed.")
  (System/exit 0))

(declare skillBoard)

(defn -main [& args]
  (println "skillBoard has begun.")
  (let [args (set args)
        window? (args "-w")]
    (q/defsketch skillBoard
                 :title "Skill Board"
                 :size #_[1920 1080] :fullscreen
                 :setup setup
                 :update update-state
                 :draw draw-state
                 :features (if window? [] [:present])
                 :middleware [m/fun-mode]
                 :on-close on-close
                 :host "skillBoard")))
