(ns skillBoard.core
  (:require [quil.core :as q]
            [quil.middleware :as m]))

(defn setup []
  (q/frame-rate 30)
  (q/color-mode :hsb)
  {:color 0
   :angle 0
   :distance 150
   :size 100})

(defn update-state [{:keys [color angle distance size]}]
  {:color (mod (+ color  0.7) 255)
   :angle (+ angle 0.1)
   :distance (if (< distance -150) 150 (- distance 1))
   :size (if (< size -100) 100 (- size 1))})

(defn draw-state [{:keys [color angle distance size]}]
  (q/background 240)
  (q/fill color 255 255)
  (let [size (Math/abs size)
        distance (Math/abs distance)
        x (* distance (q/cos angle))
        y (* distance (q/sin angle))]
    (q/with-translation [(/ (q/width) 2)
                         (/ (q/height) 2)]
      (q/ellipse x y size size))))


(q/defsketch skillBoard
  :title "You spin my circle right round"
  :size [500 500]
  :setup setup
  :update update-state
  :draw draw-state
  :features [:keep-on-top]
  :middleware [m/fun-mode]
  :host "skillBoard")

(defn -main [& args]
  (println "skillBoard has begun."))
