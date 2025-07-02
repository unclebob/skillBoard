(ns skillBoard.core
  (:require
    [quil.core :as q]
    [quil.middleware :as m]
    [skillBoard.config :as config]
    [skillBoard.display16 :as display16]
    [skillBoard.presenter :as presenter]
    [skillBoard.split-flap :as split-flap]
    ))


(defn setup []
  (config/load-config)
  (let [font-width 24
        sf-font (q/create-font "Split-Flap TV" font-width)
        _ (q/text-font sf-font)
        _ (q/text-size font-width)
        font-height (+ (q/text-ascent) (q/text-descent))
        summary (presenter/generate-summary)
        flappers (presenter/make-flappers summary [])
        now (System/currentTimeMillis)]
    (q/frame-rate 0.1)
    (q/background 255)
    {:time now
     :lines summary
     :flappers flappers
     :sf-font sf-font
     :font-width font-width
     :font-height font-height
     }))

(defn update-state [{:keys [time] :as state}]
  (let [now (System/currentTimeMillis)
        since (- now time)]
    (if (> since 30000)
      (let [summary (presenter/generate-summary)]
        (assoc state :time now
                     :lines summary
                     :flappers (presenter/make-flappers summary (:lines state))))
      state)))

(defn draw-state [state]
  ;(display16/draw-16-seg state)
  (split-flap/draw-split-flap state)
  )

(def size [(- (q/screen-width) 10) (- (q/screen-height) 40)])

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
               :size size
               :setup setup
               :update update-state
               :draw draw-state
               :features []
               :middleware [m/fun-mode]
               :on-close on-close
               :host "skillBoard"))
