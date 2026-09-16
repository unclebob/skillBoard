(ns skillBoard.presenters.wind-map.particles
  (:require
    [clojure.math :as math]
    [quil.core :as q]
    [skillBoard.config :as config]
    [skillBoard.presenters.wind-map.draw :as draw]
    [skillBoard.presenters.wind-map.geo :as geo]
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
(def particle-motion-base-screen-size 400)
(def particle-motion-speed-scale (/ 1.0 6.0))
(def particle-fade-in-ms 500)
(def particle-fade-out-ms 500)
(def particle-min-life-ms 2000)
(def particle-max-life-ms 3000)

(declare particle-segment-end)

(defn- fractional-part [n]
  (- n (Math/floor n)))

(defn particle-coordinate [index size salt]
  (* size (fractional-part (* (inc index) salt))))

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

(defn particle-motion-screen-scale [width height]
  (/ (double (min width height)) particle-motion-base-screen-size))

(defn- wind-pixel-factors
  [{:keys [top bottom left right]} width height]
  (let [center-lat (/ (+ top bottom) 2.0)
        horizontal-nm (* 60.0 (Math/cos (Math/toRadians center-lat)) (- right left))
        vertical-nm (* 60.0 (- top bottom))
        hours-per-frame (/ animation-seconds-per-frame 3600.0)
        px-per-nm-x (/ width horizontal-nm)
        px-per-nm-y (/ height vertical-nm)
        screen-scale (particle-motion-screen-scale width height)]
    {:dx-per-knot (* hours-per-frame px-per-nm-x screen-scale particle-motion-speed-scale)
     :dy-per-knot (* hours-per-frame px-per-nm-y screen-scale particle-motion-speed-scale)}))

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

(defn- grid-coordinate [size divisions index]
  (* size (/ index (max 1 (dec divisions)))))

(defn- wind-field-cell [bounds grid width height col row]
  (let [x (grid-coordinate width wind-field-cols col)
        y (grid-coordinate height wind-field-rows row)
        [lat lon] (geo/unproject-point bounds width height x y)]
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

(def calm-wind {:u 0 :v 0})

(defn- sample-grid-position [width height cols rows x y]
  (let [grid-x (* (/ (clamp 0 width x) width) (dec cols))
        grid-y (* (/ (clamp 0 height y) height) (dec rows))]
    {:col0 (long (Math/floor grid-x))
     :row0 (long (Math/floor grid-y))
     :col-fraction (- grid-x (Math/floor grid-x))
     :row-fraction (- grid-y (Math/floor grid-y))}))

(defn- interpolate-wind-corners [field {:keys [col0 row0 col-fraction row-fraction]}]
  (let [col1 (min (dec (:cols field)) (inc col0))
        row1 (min (dec (:rows field)) (inc row0))
        top-left (wind-cell field col0 row0)
        top-right (wind-cell field col1 row0)
        bottom-left (wind-cell field col0 row1)
        bottom-right (wind-cell field col1 row1)
        top-u (interpolate (:u top-left) (:u top-right) col-fraction)
        bottom-u (interpolate (:u bottom-left) (:u bottom-right) col-fraction)
        top-v (interpolate (:v top-left) (:v top-right) col-fraction)
        bottom-v (interpolate (:v bottom-left) (:v bottom-right) col-fraction)]
    {:u (interpolate top-u bottom-u row-fraction)
     :v (interpolate top-v bottom-v row-fraction)}))

(defn- wind-field-sampleable? [{:keys [cols rows] :as field}]
  (and field (pos? cols) (pos? rows)))

(defn sample-wind-field [{:keys [width height cols rows] :as field} x y]
  (if (wind-field-sampleable? field)
    (interpolate-wind-corners field (sample-grid-position width height cols rows x y))
    calm-wind))

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

(defn particle-drawing-values [width height particle]
  (let [[r g b base-a] (draw/wind-color (:speed particle 0))
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

(defn- replacement-particle [{:keys [bounds grid width height]} now particle]
  (random-particle bounds grid width height (:seed particle 0) now))

(defn- moved-particle [{:keys [width height wind-field] :as frame} now particle]
  (let [{:keys [u v] :as wind} (sample-wind-field wind-field (:x particle) (:y particle))
        [dx dy] (wind-pixel-delta {:u u :v v} frame)]
    (assoc particle
      :x (+ (:x particle) dx)
      :y (- (:y particle) dy)
      :u u
      :v v
      :speed (wind-speed wind)
      :opacity (particle-opacity now particle)
      :age (inc (:age particle 0)))))

(defn- replacement-needed? [frame now particle]
  (or (particle-dead? now particle)
      (let [{:keys [x y]} (moved-particle frame now particle)]
        (out-of-bounds? (:width frame) (:height frame) x y))))

(defn step-particle-with-frame [{:keys [width height] :as frame} now particle]
  (particle-drawing-values
    width
    height
    (if (replacement-needed? frame now particle)
      (replacement-particle frame now particle)
      (moved-particle frame now particle))))

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

(defn- particle-speed [{:keys [u v speed] :or {u 0 v 0}}]
  (or speed (wind-speed {:u u :v v})))

(defn- moving-segment-end [width height {:keys [x y u v] :or {u 0 v 0}} speed]
  (let [length (particle-segment-length width height speed)]
    [(+ x (* (/ u speed) length))
     (- y (* (/ v speed) length))]))

(defn particle-segment-end
  ([particle]
   (particle-segment-end 600 400 particle))
  ([width height {:keys [x y] :as particle}]
   (let [speed (particle-speed particle)]
     (if (pos? speed)
       (moving-segment-end width height particle speed)
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
