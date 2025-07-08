(ns skillBoard.core
  (:require
    [quil.core :as q]
    [quil.middleware :as m]
    [skillBoard.config :as config]
    [skillBoard.presenter :as presenter]
    [skillBoard.split-flap :as split-flap]
    ))


(defn setup []
  (config/load-config)
  (let [font-width 24
        sf-font (q/create-font "Split-Flap TV" font-width)
        header-font (q/create-font "Arial Rounded MT Bold" 50)
        _ (q/text-font sf-font)
        _ (q/text-size font-width)
        font-height (+ (q/text-ascent) (q/text-descent))
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
     :header-font header-font
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

(defn -main [& _args]
  (println "skillBoard has begun.")
  (q/defsketch skillBoard
               :title "Skill Board"
               :size [1920 1080]
               :setup setup
               :update update-state
               :draw draw-state
               :features []
               :middleware [m/fun-mode]
               :on-close on-close
               :host "skillBoard"))
