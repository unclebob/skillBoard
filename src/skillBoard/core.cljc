(ns skillBoard.core
  (:require [clojure.string :as str]
            [quil.core :as q]
            [quil.middleware :as m]
            [skillBoard.display16 :as display]
            [skillBoard.weather :as weather]
            ))

(defn update-state [state]
  state)

(defn setup []
  (q/frame-rate 30)
  (q/background 255)
  ;(let [metar (weather/get-metar "KUGN")
  ;      rawOb (:rawOb (first metar))
  ;      pre (-> rawOb
  ;              (str/split #"RMK")
  ;              first)]
  ;  pre)
  "HELLO"
  )


(defn draw-state [state]
  (q/background 0 0 0)
  (q/no-fill)
  (q/stroke 0)
  (q/with-translation
    [10 10]
    (let [display (display/build-character-display 35)]
      (display/draw-line display state)))
  )

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
