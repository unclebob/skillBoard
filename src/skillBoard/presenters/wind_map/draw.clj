(ns skillBoard.presenters.wind-map.draw
  (:require
    [quil.core :as q]
    [skillBoard.foundation.config :as config]))

(def ceiling-overlay-max-ft 10000)

(defn map-label-font []
  (or (:header-font @config/display-info)
      (:annotation-font @config/display-info)))

(defn airport-label-font []
  (or (:header-font @config/display-info)
      (:annotation-font @config/display-info)))

(defn metar-label-font []
  (or (:metar-font @config/display-info)
      (:annotation-font @config/display-info)
      (:header-font @config/display-info)))

(defn q-text-font! [font]
  (when font
    (q/text-font font)))

(defn layer-text-font! [layer font]
  (when font
    (.textFont layer font)))

(def flight-category-colors
  {"VFR" config/vfr-color
   "MVFR" config/mvfr-color
   "IFR" config/ifr-color
   "LIFR" config/lifr-color})

(defn flight-category-color [flt-cat]
  (get flight-category-colors flt-cat config/info-color))

(def color-rgb-values
  {:green [0 255 0]
   :blue [70 150 255]
   :red [255 60 60]
   :magenta [255 0 255]
   :yellow [255 235 90]
   :white [255 255 255]})

(def default-color-rgb [255 255 255])

(defn color-rgb [color]
  (get color-rgb-values color default-color-rgb))

(def wind-color-thresholds
  [[5 [155 210 255 205]]
   [10 [125 235 255 215]]
   [15 [165 255 190 225]]
   [20 [255 242 125 235]]
   [25 [255 185 100 240]]
   [30 [255 135 135 245]]])

(def strong-wind-color [255 80 120 250])

(defn- below-wind-threshold? [speed [threshold _color]]
  (< speed threshold))

(defn wind-color [speed]
  (second (or (first (filter #(below-wind-threshold? speed %) wind-color-thresholds))
              [nil strong-wind-color])))

(def ceiling-overlay-cell-alpha 42)

(defn ceiling-band-upper-ft [ceiling-ft-agl]
  (max 500 (* 500 (Math/ceil (/ ceiling-ft-agl 500.0)))))

(def ceiling-color-stops
  [{:ceiling 0 :color [255 50 50]}
   {:ceiling 1000 :color [255 125 50]}
   {:ceiling 3000 :color [255 230 80]}
   {:ceiling 5000 :color [85 220 105]}
   {:ceiling 8000 :color [70 210 255]}
   {:ceiling ceiling-overlay-max-ft :color [70 120 255]}])

(defn- interpolate-channel [a b ratio]
  (int (+ a (* (- b a) ratio))))

(defn- color-between-stops [{low-ceiling :ceiling low-color :color}
                            {high-ceiling :ceiling high-color :color}
                            ceiling]
  (let [ratio (if (= low-ceiling high-ceiling)
                0.0
                (/ (- ceiling low-ceiling) (- high-ceiling low-ceiling)))]
    (mapv interpolate-channel low-color high-color (repeat ratio))))

(defn- ceiling-overlay-visible? [ceiling-ft-agl]
  (boolean (some-> ceiling-ft-agl (< ceiling-overlay-max-ft))))

(defn- ceiling-overlay-band-color [ceiling-ft-agl]
  (let [band-ceiling (min ceiling-overlay-max-ft (ceiling-band-upper-ft ceiling-ft-agl))
        high-stop (first (filter #(<= band-ceiling (:ceiling %)) (rest ceiling-color-stops)))
        low-stop (last (take-while #(< (:ceiling %) (:ceiling high-stop)) ceiling-color-stops))]
    (conj (color-between-stops low-stop high-stop band-ceiling)
          ceiling-overlay-cell-alpha)))

(defn ceiling-overlay-color [ceiling-ft-agl]
  (when (ceiling-overlay-visible? ceiling-ft-agl)
    (ceiling-overlay-band-color ceiling-ft-agl)))

(defn ceiling-scale-color [ceiling-ft-agl]
  (when-let [[r g b _a] (ceiling-overlay-color ceiling-ft-agl)]
    [r g b 210]))

(defn source-label-font-size [width height]
  (max 9 (int (/ (min width height) 45))))

(defn scale-title-font-size [width height]
  (* 1.1 (source-label-font-size width height)))
