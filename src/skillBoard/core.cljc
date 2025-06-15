(ns skillBoard.core
  (:require
    [clojure.set :as set]
    [clojure.string :as str]
    [quil.core :as q]
    [quil.middleware :as m]
    [skillBoard.display16 :as display]
    [skillBoard.flight-schedule-pro :as fsp]
    [skillBoard.sources :as sources]
    [skillBoard.text-util :as text]
    [skillBoard.weather :as weather]
    ))

(defn update-state [state]
  state)

(def logo (atom nil))
(def logo-url "https://static.wixstatic.com/media/e1b9b5_ecc3842ca044483daa055d2546ba22cc~mv2.png/v1/crop/x_0,y_0,w_1297,h_762/fill/w_306,h_180,al_c,q_85,usm_0.66_1.00_0.01,enc_avif,quality_auto/Skill%20Aviation%20logo.png")

(defn format-name [[first-name last-name]]
  (if (nil? first-name)
    "     "
    (str/upper-case (str (subs last-name 0 3) "." (first first-name)))))

(defn format-res [{:keys [start-time tail-number pilot-name instructor-name]}]
  (format "%5s %-6s %5s %5s" (subs start-time 11 16) tail-number
          (format-name pilot-name)
          (format-name instructor-name)))

(defn format-flight [flight]
  (format "CO: %s, CI: %s, %s"
          (:checkedOutOn flight)
          (:checkedInOn flight)
          (:aircraft flight)
          ))

(defn setup []
  (q/frame-rate 30)
  (q/background 255)
  ;(reset! logo (q/load-image "resources/logo.jpg"))
  (reset! logo (q/load-image logo-url))

  #?(:clj
     (let [metar (sources/get-metar weather/source "KUGN")
           metar-text (:rawOb (first metar))
           pre (-> metar-text
                   (str/split #"RMK")
                   first)
           reservations-packet (sources/get-reservations fsp/source)
           unpacked-res (fsp/unpack-reservations reservations-packet)
           flights (sources/get-flights fsp/source)
           flights (:items flights)
           flights-summary (map format-flight flights)
           metar-text (text/wrap metar-text 40)
           res-summary (for [res (vals unpacked-res)
                             :let [activity (:activity-type res)]
                             :when (or (str/starts-with? activity "Flight")
                                       (= activity "New Customer"))]
                         res)
           summary-lines (map format-res res-summary)
           ]
       (doseq [flight-summary flights-summary]
         (prn flight-summary))
       (concat summary-lines metar-text))
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

(defn on-close [_]
  (q/no-loop)
  (q/exit)                                                  ; Exit the sketch
  (println "Skill Board closed.")
  (System/exit 0))




(defn -main [& _args]
  (println "skillBoard has begun.")
  (q/defsketch skillBoard
               :title "Skill Board"
               :size size
               :setup setup
               :update update-state
               :draw draw-state
               :features [:keep-on-top]
               :middleware [m/fun-mode]
               :on-close on-close
               :host "skillBoard"))
