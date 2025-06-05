(ns skillBoard.core
  (:require [clojure.string :as str]
            [quil.core :as q]
            [quil.middleware :as m]
            [skillBoard.display16 :as display]
            [skillBoard.weather :as weather]
            [skillBoard.text-util :as text]
            ))

(defn update-state [state]
  state)

(def logo (atom nil))
(def logo-url "https://static.wixstatic.com/media/e1b9b5_ecc3842ca044483daa055d2546ba22cc~mv2.png/v1/crop/x_0,y_0,w_1297,h_762/fill/w_306,h_180,al_c,q_85,usm_0.66_1.00_0.01,enc_avif,quality_auto/Skill%20Aviation%20logo.png")

(defn setup []
  (q/frame-rate 30)
  (q/background 255)
  ;(reset! logo (q/load-image "resources/logo.jpg"))
  (reset! logo (q/load-image logo-url))

  #?(:clj
     (let [metar (weather/get-metar "KUGN")
           rawOb (:rawOb (first metar))
           pre (-> rawOb
                   (str/split #"RMK")
                   first)]
       (text/wrap rawOb 40))
       :cljs
       ["HELLO"])
     )


(defn draw-state [state]
  (q/background 0 0 0)
  (q/image @logo 0 0 400 200)
  (q/no-fill)
  (q/stroke 0)
  (let [display (display/build-character-display 30)]
  (loop [lines state
         y 10]
    (if (empty? lines)
      nil
      (let [line (first lines)]
        (q/with-translation
          [400 y]
          (display/draw-line display line))
        (recur (rest lines) (+ y 60)))))))

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
