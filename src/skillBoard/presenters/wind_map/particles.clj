(ns skillBoard.presenters.wind-map.particles
  (:require
    [clojure.math :as math]
    [quil.core :as q]
    [skillBoard.config :as config]
    [skillBoard.wind-data :as wind-data]))

(def particles (atom []))
(def particle-field-size (atom nil))
(def animation-seconds-per-frame 390)
(def particle-segment-min-length 5)
(def particle-segment-max-length 34)
(def particle-segment-pixels-per-knot 1.2)
(def particle-fade-in-ms 500)
(def particle-fade-out-ms 500)
(def particle-min-life-ms 2000)
(def particle-max-life-ms 3000)

(defn- fractional-part [n]
  (- n (Math/floor n)))

(defn particle-coordinate [index size salt]
  (* size (fractional-part (* (inc index) salt))))

(defn project-point [{:keys [top bottom left right]} width height lat lon]
  (let [x (* width (/ (- lon left) (- right left)))
        y (* height (/ (- top lat) (- top bottom)))]
    [x y]))

(defn unproject-point [{:keys [top bottom left right]} width height x y]
  (let [lon (+ left (* (/ x width) (- right left)))
        lat (- top (* (/ y height) (- top bottom)))]
    [lat lon]))

(defn nearest-wind [grid lat lon]
  (let [points (:points grid)]
    (if (empty? points)
      {:u 0 :v 0}
      (apply min-key
             (fn [{point-lat :lat point-lon :lon}]
               (+ (math/pow (- point-lat lat) 2)
                  (math/pow (- point-lon lon) 2)))
             points))))

(defn- wind-distance [lat lon point]
  (max 0.25 (wind-data/nm-distance [lat lon] [(:lat point) (:lon point)])))

(defn interpolated-wind [grid lat lon]
  (let [points (:points grid)]
    (if (empty? points)
      {:u 0 :v 0}
      (let [nearby (take 6 (sort-by #(wind-distance lat lon %) points))
            weighted (map (fn [{:keys [u v] :as point}]
                            (let [distance (wind-distance lat lon point)
                                  weight (/ 1.0 (* distance distance))]
                              {:u (* u weight)
                               :v (* v weight)
                               :weight weight}))
                          nearby)
            total-weight (reduce + (map :weight weighted))]
        {:u (/ (reduce + (map :u weighted)) total-weight)
         :v (/ (reduce + (map :v weighted)) total-weight)}))))

(defn wind-speed [{:keys [u v]}]
  (Math/sqrt (+ (* u u) (* v v))))

(defn- wind-pixel-delta
  [{:keys [top bottom left right]} width height {:keys [u v]}]
  (let [center-lat (/ (+ top bottom) 2.0)
        horizontal-nm (* 60.0 (Math/cos (Math/toRadians center-lat)) (- right left))
        vertical-nm (* 60.0 (- top bottom))
        hours-per-frame (/ animation-seconds-per-frame 3600.0)
        px-per-nm-x (/ width horizontal-nm)
        px-per-nm-y (/ height vertical-nm)]
    [(* u hours-per-frame px-per-nm-x)
     (* v hours-per-frame px-per-nm-y)]))

(defn- visible-particle-opacity [elapsed life-ms]
  (let [fade-out-start (- life-ms particle-fade-out-ms)]
    (cond
      (< elapsed particle-fade-in-ms) (/ (double elapsed) particle-fade-in-ms)
      (< elapsed fade-out-start) 1.0
      :else (/ (double (- life-ms elapsed)) particle-fade-out-ms))))

(defn particle-opacity [now {:keys [born-at life-ms] :or {life-ms particle-min-life-ms}}]
  (let [elapsed (- now born-at)]
    (if (or (< elapsed 0)
            (>= elapsed life-ms))
      0.0
      (visible-particle-opacity elapsed life-ms))))

(defn particle-dead? [now particle]
  (>= (- now (:born-at particle)) (:life-ms particle particle-min-life-ms)))

(defn random-particle [bounds grid width height seed now]
  (let [x (rand width)
        y (rand height)
        life-ms (+ particle-min-life-ms (rand (- particle-max-life-ms particle-min-life-ms)))]
    {:x x
     :y y
     :seed seed
     :born-at now
     :life-ms life-ms
     :age 0
     :opacity 0.0}))

(defn initial-particle [bounds grid width height seed now]
  (let [initial-age-ms (rand 1000)
        particle (random-particle bounds grid width height seed (- now initial-age-ms))]
    (assoc particle
      :age (long initial-age-ms)
      :opacity (particle-opacity now particle))))

(defn make-particles [count bounds grid width height now]
  (vec (for [i (range count)]
         (initial-particle bounds grid width height i now))))

(defn- reset-particles! [bounds grid width height now]
  (reset! particles (make-particles config/wind-map-particle-count bounds grid width height now))
  (reset! particle-field-size [width height])
  nil)

(defn ensure-particles! [bounds grid width height now]
  (let [field-size [width height]]
    (when (or (empty? @particles)
              (not= field-size @particle-field-size))
      (reset-particles! bounds grid width height now))))

(def wind-color-thresholds
  [[5 [155 210 255 205]]
   [15 [165 255 190 220]]
   [25 [255 242 125 235]]])

(def strong-wind-color [255 135 135 245])

(defn wind-color [speed]
  (or (some (fn [[threshold color]]
              (when (< speed threshold) color))
            wind-color-thresholds)
      strong-wind-color))

(defn- out-of-bounds? [width height x y]
  (or (< x 0)
      (> x width)
      (< y 0)
      (> y height)))

(defn step-particle [bounds grid width height now particle]
  (if (particle-dead? now particle)
    (random-particle bounds grid width height (:seed particle 0) now)
    (let [[lat lon] (unproject-point bounds width height (:x particle) (:y particle))
          {:keys [u v] :as wind} (interpolated-wind grid lat lon)
          [dx dy] (wind-pixel-delta bounds width height {:u u :v v})
          x (+ (:x particle) dx)
          y (- (:y particle) dy)
          age (inc (:age particle 0))]
      (if (out-of-bounds? width height x y)
        (random-particle bounds grid width height (:seed particle 0) now)
        (assoc particle
          :x x
          :y y
          :u u
          :v v
          :speed (wind-speed wind)
          :opacity (particle-opacity now particle)
          :age age)))))

(defn particle-segment-length [speed]
  (min particle-segment-max-length
       (max particle-segment-min-length
            (* speed particle-segment-pixels-per-knot))))

(defn particle-segment-end [{:keys [x y u v speed] :or {u 0 v 0}}]
  (let [speed (if (some? speed) speed (wind-speed {:u u :v v}))]
    (if (pos? speed)
      (let [length (particle-segment-length speed)]
        [(+ x (* (/ u speed) length))
         (- y (* (/ v speed) length))])
      [x y])))

(defn draw-particle! [particle]
  (let [[r g b a] (wind-color (:speed particle 0))
        a (* a (:opacity particle 1.0))
        [x2 y2] (particle-segment-end particle)]
    (q/stroke-weight 2)
    (q/stroke r g b a)
    (q/line (:x particle) (:y particle) x2 y2)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T15:37:29.192711-05:00", :module-hash "974971153", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "605353130"} {:id "def/particles", :kind "def", :line 8, :end-line 8, :hash "-805035099"} {:id "def/particle-field-size", :kind "def", :line 9, :end-line 9, :hash "-1679469677"} {:id "def/animation-seconds-per-frame", :kind "def", :line 10, :end-line 10, :hash "636313673"} {:id "def/particle-segment-min-length", :kind "def", :line 11, :end-line 11, :hash "671707306"} {:id "def/particle-segment-max-length", :kind "def", :line 12, :end-line 12, :hash "-1062122780"} {:id "def/particle-segment-pixels-per-knot", :kind "def", :line 13, :end-line 13, :hash "602699003"} {:id "def/particle-fade-in-ms", :kind "def", :line 14, :end-line 14, :hash "1336373797"} {:id "def/particle-fade-out-ms", :kind "def", :line 15, :end-line 15, :hash "1556980209"} {:id "def/particle-min-life-ms", :kind "def", :line 16, :end-line 16, :hash "609523084"} {:id "def/particle-max-life-ms", :kind "def", :line 17, :end-line 17, :hash "670977351"} {:id "defn-/fractional-part", :kind "defn-", :line 19, :end-line 20, :hash "720073750"} {:id "defn/particle-coordinate", :kind "defn", :line 22, :end-line 23, :hash "-337514268"} {:id "defn/project-point", :kind "defn", :line 25, :end-line 28, :hash "-1445133276"} {:id "defn/unproject-point", :kind "defn", :line 30, :end-line 33, :hash "-1531146905"} {:id "defn/nearest-wind", :kind "defn", :line 35, :end-line 43, :hash "-1823228980"} {:id "defn-/wind-distance", :kind "defn-", :line 45, :end-line 46, :hash "-895157327"} {:id "defn/interpolated-wind", :kind "defn", :line 48, :end-line 62, :hash "1971759617"} {:id "defn/wind-speed", :kind "defn", :line 64, :end-line 65, :hash "-963648133"} {:id "defn-/wind-pixel-delta", :kind "defn-", :line 67, :end-line 76, :hash "-1769679956"} {:id "defn-/visible-particle-opacity", :kind "defn-", :line 78, :end-line 83, :hash "-23369782"} {:id "defn/particle-opacity", :kind "defn", :line 85, :end-line 90, :hash "-1712976452"} {:id "defn/particle-dead?", :kind "defn", :line 92, :end-line 93, :hash "-139136810"} {:id "defn/random-particle", :kind "defn", :line 95, :end-line 105, :hash "-45960960"} {:id "defn/initial-particle", :kind "defn", :line 107, :end-line 112, :hash "1303165161"} {:id "defn/make-particles", :kind "defn", :line 114, :end-line 116, :hash "-675656271"} {:id "defn-/reset-particles!", :kind "defn-", :line 118, :end-line 121, :hash "-1046136234"} {:id "defn/ensure-particles!", :kind "defn", :line 123, :end-line 127, :hash "1971030647"} {:id "def/wind-color-thresholds", :kind "def", :line 129, :end-line 132, :hash "593332343"} {:id "def/strong-wind-color", :kind "def", :line 134, :end-line 134, :hash "-918366031"} {:id "defn/wind-color", :kind "defn", :line 136, :end-line 140, :hash "300582635"} {:id "defn-/out-of-bounds?", :kind "defn-", :line 142, :end-line 146, :hash "1290138187"} {:id "defn/step-particle", :kind "defn", :line 148, :end-line 166, :hash "769620711"} {:id "defn/particle-segment-length", :kind "defn", :line 168, :end-line 171, :hash "1511418725"} {:id "defn/particle-segment-end", :kind "defn", :line 173, :end-line 179, :hash "218024992"} {:id "defn/draw-particle!", :kind "defn", :line 181, :end-line 187, :hash "689326613"}]}
;; clj-mutate-manifest-end
