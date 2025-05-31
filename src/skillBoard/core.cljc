(ns skillBoard.core
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [skillBoard.display16 :as display]
            ))

(defn update-state [state]
  state)

(defn setup []
  (q/frame-rate 30)
  (q/background 255))

(defn draw-state [state]
  (q/background 0 0 0)
  (q/no-fill)
  (q/stroke 0)
  (let [display (display/build-character-display 35)]
    (display/draw-line display "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789./+-:")
    ))

(def size
  #?(
     :cljs
     [(max (- (.-scrollWidth (.-body js/document)) 20) 900)
      (max (- (.-innerHeight js/window) 25) 700)]
     :clj
     [(- (q/screen-width) 10) (- (q/screen-height) 40)]))

(q/defsketch skillBoard
             :title "Skill Board"
             :size size
             :setup setup
             :update update-state
             :draw draw-state
             :features [:keep-on-top]
             :middleware [m/fun-mode]
             :host "skillBoard")


(defn -main [& args]
  (println "skillBoard has begun."))
