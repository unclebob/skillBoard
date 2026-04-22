(ns skillBoard.presenters.wind-map.particles
  (:require
    [clojure.math :as math]
    [quil.core :as q]
    [skillBoard.config :as config]
    [skillBoard.wind-data :as wind-data]))

(def particles (atom []))
(def particle-field-size (atom nil))
(def wind-field-cache (atom {:key nil :field nil}))
(def animation-seconds-per-frame 390)
(def wind-field-cols 80)
(def wind-field-rows 50)
(def particle-segment-min-length 5)
(def particle-segment-max-length 34)
(def particle-segment-pixels-per-knot 0.6)
(def particle-segment-base-screen-size 400)
(def particle-fade-in-ms 500)
(def particle-fade-out-ms 500)
(def particle-min-life-ms 2000)
(def particle-max-life-ms 3000)

(declare particle-segment-end)

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

(defn- wind-pixel-factors
  [{:keys [top bottom left right]} width height]
  (let [center-lat (/ (+ top bottom) 2.0)
        horizontal-nm (* 60.0 (Math/cos (Math/toRadians center-lat)) (- right left))
        vertical-nm (* 60.0 (- top bottom))
        hours-per-frame (/ animation-seconds-per-frame 3600.0)
        px-per-nm-x (/ width horizontal-nm)
        px-per-nm-y (/ height vertical-nm)]
    {:dx-per-knot (* hours-per-frame px-per-nm-x)
     :dy-per-knot (* hours-per-frame px-per-nm-y)}))

(defn- wind-pixel-delta
  ([{:keys [u v]} {:keys [dx-per-knot dy-per-knot]}]
   [(* u dx-per-knot)
    (* v dy-per-knot)])
  ([bounds width height wind]
   (wind-pixel-delta wind (wind-pixel-factors bounds width height))))

(defn wind-field-key [bounds grid width height]
  [width
   height
   bounds
   (:source grid)
   (:generated-at grid)
   (:radius-nm grid)
   (count (:points grid))])

(defn- wind-field-cell [bounds grid width height col row]
  (let [x (if (= 1 wind-field-cols)
            0
            (* width (/ col (dec wind-field-cols))))
        y (if (= 1 wind-field-rows)
            0
            (* height (/ row (dec wind-field-rows))))
        [lat lon] (unproject-point bounds width height x y)]
    (interpolated-wind grid lat lon)))

(defn make-wind-field [bounds grid width height]
  {:width width
   :height height
   :cols wind-field-cols
   :rows wind-field-rows
   :cells (mapv (fn [row]
                  (mapv (fn [col]
                          (wind-field-cell bounds grid width height col row))
                        (range wind-field-cols)))
                (range wind-field-rows))})

(defn current-wind-field [bounds grid width height]
  (let [key (wind-field-key bounds grid width height)
        {cached-key :key field :field} @wind-field-cache]
    (if (= key cached-key)
      field
      (let [field (make-wind-field bounds grid width height)]
        (reset! wind-field-cache {:key key :field field})
        field))))

(defn- clamp [min-value max-value value]
  (max min-value (min max-value value)))

(defn- wind-cell [{:keys [cells]} col row]
  (get-in cells [row col] {:u 0 :v 0}))

(defn- interpolate [a b fraction]
  (double (+ a (* (- b a) fraction))))

(defn sample-wind-field [{:keys [width height cols rows] :as field} x y]
  (if (or (nil? field) (zero? cols) (zero? rows))
    {:u 0 :v 0}
    (let [grid-x (* (/ (clamp 0 width x) width) (dec cols))
          grid-y (* (/ (clamp 0 height y) height) (dec rows))
          col0 (long (Math/floor grid-x))
          row0 (long (Math/floor grid-y))
          col1 (min (dec cols) (inc col0))
          row1 (min (dec rows) (inc row0))
          col-fraction (- grid-x col0)
          row-fraction (- grid-y row0)
          top-left (wind-cell field col0 row0)
          top-right (wind-cell field col1 row0)
          bottom-left (wind-cell field col0 row1)
          bottom-right (wind-cell field col1 row1)
          top-u (interpolate (:u top-left) (:u top-right) col-fraction)
          bottom-u (interpolate (:u bottom-left) (:u bottom-right) col-fraction)
          top-v (interpolate (:v top-left) (:v top-right) col-fraction)
          bottom-v (interpolate (:v bottom-left) (:v bottom-right) col-fraction)]
      {:u (interpolate top-u bottom-u row-fraction)
       :v (interpolate top-v bottom-v row-fraction)})))

(defn particle-frame [bounds grid width height]
  (merge {:bounds bounds
          :grid grid
          :width width
          :height height
          :wind-field (current-wind-field bounds grid width height)}
         (wind-pixel-factors bounds width height)))

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

(defn particle-drawing-values [width height particle]
  (let [[r g b base-a] (wind-color (:speed particle 0))
        a (* base-a (:opacity particle 1.0))
        [x2 y2] (particle-segment-end width height particle)]
    (assoc particle
      :x2 x2
      :y2 y2
      :stroke [r g b a])))

(defn- out-of-bounds? [width height x y]
  (or (< x 0)
      (> x width)
      (< y 0)
      (> y height)))

(defn step-particle-with-frame [{:keys [bounds grid width height wind-field] :as frame} now particle]
  (if (particle-dead? now particle)
    (particle-drawing-values width height (random-particle bounds grid width height (:seed particle 0) now))
    (let [{:keys [u v] :as wind} (sample-wind-field wind-field (:x particle) (:y particle))
          [dx dy] (wind-pixel-delta {:u u :v v} frame)
          x (+ (:x particle) dx)
          y (- (:y particle) dy)
          age (inc (:age particle 0))]
      (if (out-of-bounds? width height x y)
        (particle-drawing-values width height (random-particle bounds grid width height (:seed particle 0) now))
        (particle-drawing-values
          width
          height
          (assoc particle
            :x x
            :y y
            :u u
            :v v
            :speed (wind-speed wind)
            :opacity (particle-opacity now particle)
            :age age))))))

(defn step-particle [bounds grid width height now particle]
  (step-particle-with-frame (particle-frame bounds grid width height) now particle))

(defn particle-segment-screen-scale [width height]
  (/ (double (min width height)) particle-segment-base-screen-size))

(defn particle-segment-length
  ([speed]
   (particle-segment-length 600 400 speed))
  ([width height speed]
   (let [screen-scale (particle-segment-screen-scale width height)]
     (min (* particle-segment-max-length screen-scale)
          (max (* particle-segment-min-length screen-scale)
               (* speed particle-segment-pixels-per-knot screen-scale))))))

(defn particle-segment-end
  ([particle]
   (particle-segment-end 600 400 particle))
  ([width height {:keys [x y u v speed] :or {u 0 v 0}}]
   (let [speed (if (some? speed) speed (wind-speed {:u u :v v}))]
     (if (pos? speed)
       (let [length (particle-segment-length width height speed)]
         [(+ x (* (/ u speed) length))
          (- y (* (/ v speed) length))])
       [x y]))))

(defn draw-particle-line! [{:keys [x y x2 y2]}]
  (q/line x y x2 y2))

(defn draw-particle!
  ([particle]
   (draw-particle! (q/width) (q/height) particle))
  ([width height particle]
   (let [{:keys [stroke] :as particle} (if (:stroke particle)
                                         particle
                                         (particle-drawing-values width height particle))
         [r g b a] stroke]
     (q/stroke-weight 2)
     (q/stroke r g b a)
     (draw-particle-line! particle))))

(defn draw-particles! [particles]
  (q/stroke-weight 2)
  (doseq [[[r g b a] group] (group-by :stroke particles)]
    (q/stroke r g b a)
    (doseq [particle group]
      (draw-particle-line! particle))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T15:37:29.192711-05:00", :module-hash "974971153", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "605353130"} {:id "def/particles", :kind "def", :line 8, :end-line 8, :hash "-805035099"} {:id "def/particle-field-size", :kind "def", :line 9, :end-line 9, :hash "-1679469677"} {:id "def/animation-seconds-per-frame", :kind "def", :line 10, :end-line 10, :hash "636313673"} {:id "def/particle-segment-min-length", :kind "def", :line 11, :end-line 11, :hash "671707306"} {:id "def/particle-segment-max-length", :kind "def", :line 12, :end-line 12, :hash "-1062122780"} {:id "def/particle-segment-pixels-per-knot", :kind "def", :line 13, :end-line 13, :hash "602699003"} {:id "def/particle-fade-in-ms", :kind "def", :line 14, :end-line 14, :hash "1336373797"} {:id "def/particle-fade-out-ms", :kind "def", :line 15, :end-line 15, :hash "1556980209"} {:id "def/particle-min-life-ms", :kind "def", :line 16, :end-line 16, :hash "609523084"} {:id "def/particle-max-life-ms", :kind "def", :line 17, :end-line 17, :hash "670977351"} {:id "defn-/fractional-part", :kind "defn-", :line 19, :end-line 20, :hash "720073750"} {:id "defn/particle-coordinate", :kind "defn", :line 22, :end-line 23, :hash "-337514268"} {:id "defn/project-point", :kind "defn", :line 25, :end-line 28, :hash "-1445133276"} {:id "defn/unproject-point", :kind "defn", :line 30, :end-line 33, :hash "-1531146905"} {:id "defn/nearest-wind", :kind "defn", :line 35, :end-line 43, :hash "-1823228980"} {:id "defn-/wind-distance", :kind "defn-", :line 45, :end-line 46, :hash "-895157327"} {:id "defn/interpolated-wind", :kind "defn", :line 48, :end-line 62, :hash "1971759617"} {:id "defn/wind-speed", :kind "defn", :line 64, :end-line 65, :hash "-963648133"} {:id "defn-/wind-pixel-delta", :kind "defn-", :line 67, :end-line 76, :hash "-1769679956"} {:id "defn-/visible-particle-opacity", :kind "defn-", :line 78, :end-line 83, :hash "-23369782"} {:id "defn/particle-opacity", :kind "defn", :line 85, :end-line 90, :hash "-1712976452"} {:id "defn/particle-dead?", :kind "defn", :line 92, :end-line 93, :hash "-139136810"} {:id "defn/random-particle", :kind "defn", :line 95, :end-line 105, :hash "-45960960"} {:id "defn/initial-particle", :kind "defn", :line 107, :end-line 112, :hash "1303165161"} {:id "defn/make-particles", :kind "defn", :line 114, :end-line 116, :hash "-675656271"} {:id "defn-/reset-particles!", :kind "defn-", :line 118, :end-line 121, :hash "-1046136234"} {:id "defn/ensure-particles!", :kind "defn", :line 123, :end-line 127, :hash "1971030647"} {:id "def/wind-color-thresholds", :kind "def", :line 129, :end-line 132, :hash "593332343"} {:id "def/strong-wind-color", :kind "def", :line 134, :end-line 134, :hash "-918366031"} {:id "defn/wind-color", :kind "defn", :line 136, :end-line 140, :hash "300582635"} {:id "defn-/out-of-bounds?", :kind "defn-", :line 142, :end-line 146, :hash "1290138187"} {:id "defn/step-particle", :kind "defn", :line 148, :end-line 166, :hash "769620711"} {:id "defn/particle-segment-length", :kind "defn", :line 168, :end-line 171, :hash "1511418725"} {:id "defn/particle-segment-end", :kind "defn", :line 173, :end-line 179, :hash "218024992"} {:id "defn/draw-particle!", :kind "defn", :line 181, :end-line 187, :hash "689326613"}]}
;; clj-mutate-manifest-end
