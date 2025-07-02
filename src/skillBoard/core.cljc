(ns skillBoard.core
  (:require
    [quil.core :as q]
    [quil.middleware :as m]
    [skillBoard.config :as config]
    [skillBoard.display16 :as display]
    [skillBoard.presenter :as presenter]
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

(def backing-rect {:left 4
                   :right 4
                   :top 4
                   :bottom 8})

(defn draw-char [{:keys [sf-font font-width font-height]}
                 c cx cy]
  (q/fill 255 255 255)
  (q/no-stroke)
  (q/rect (+ cx (:left backing-rect))
          (+ cy (:top backing-rect))
          (- font-width (:right backing-rect))
          (- font-height (:bottom backing-rect)))
  (q/fill 0 0 0)
  (q/text-font sf-font)
  (q/text-align :left :top)
  (q/text (str c) cx cy))

(defn draw-split-flap [{:keys [lines font-width font-height] :as state}]
  (let [draw-line (fn [line y]
                    (loop [x 0
                           cs line]
                      (if (empty? cs)
                        nil
                        (let [c (first cs)
                              next-x (+ x font-width 6)]
                          (draw-char state c x y)
                          (recur next-x (rest cs))))))]
    (q/background 50)
    (loop [lines lines
           y 0]
      (if (empty? lines)
        nil
        (let [line (first lines)]
          (draw-line line y)
          (recur (rest lines) (+ y font-height 10)))))
    ))

(defn draw-16-seg [state]
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

(defn draw-state [state]
  ;(draw-16-seg state)
  (draw-split-flap state)
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
