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
  (q/background 255)
  (q/no-fill)
  (q/stroke 0)
  (let [{:keys [margin width height] :as display} (display/build-context 400)
        [w h] (:box display)]
    (q/rect 0 0 w h)
    (q/stroke 128 128 128)
    (q/rect margin margin (- w margin margin) (- h margin margin))
    (q/line (* 0.5 width) 0 (* 0.5 width) height)
    (q/line 0 (* 0.5 height) width (* 0.5 height))
    (q/fill 200 200 255 150)
    (q/stroke 0 0 255)
    (display/draw display)))

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
