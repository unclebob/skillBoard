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

(defn bounds-aspect-ratio-nm [{:keys [top bottom left right]}]
  (let [center-lat (/ (+ top bottom) 2.0)
        width-nm (* 60.0 (Math/cos (Math/toRadians center-lat)) (- right left))
        height-nm (* 60.0 (- top bottom))]
    (if (zero? height-nm)
      1.0
      (/ width-nm height-nm))))

(defn- screen-aspect-ratio [width height]
  (if (zero? height) 1.0 (/ (double width) height)))

(defn- widen-bounds [{:keys [top bottom left right]} screen-aspect]
  (let [center-lat (/ (+ top bottom) 2.0)
        center-lon (/ (+ left right) 2.0)
        lat-span (- top bottom)
        target-lon-span (* lat-span (/ screen-aspect (Math/cos (Math/toRadians center-lat))))
        lon-half-span (/ target-lon-span 2.0)]
    {:top top
     :bottom bottom
     :left (- center-lon lon-half-span)
     :right (+ center-lon lon-half-span)}))

(defn- heighten-bounds [{:keys [top bottom left right]} screen-aspect]
  (let [center-lat (/ (+ top bottom) 2.0)
        center-lon (/ (+ left right) 2.0)
        lon-span (- right left)
        target-lat-span (* lon-span (/ (Math/cos (Math/toRadians center-lat)) screen-aspect))
        lat-half-span (/ target-lat-span 2.0)]
    {:top (+ center-lat lat-half-span)
     :bottom (- center-lat lat-half-span)
     :left left
     :right right}))

(defn fit-bounds-to-screen [bounds width height]
  (let [screen-aspect (screen-aspect-ratio width height)
        bounds-aspect (bounds-aspect-ratio-nm bounds)
        fit-bounds ({1 widen-bounds
                     0 (fn [bounds _screen-aspect] bounds)
                     -1 heighten-bounds}
                    (compare screen-aspect bounds-aspect))]
    (if (pos? screen-aspect)
      (fit-bounds bounds screen-aspect)
      bounds)))

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-09T09:20:19.851393-05:00", :module-hash "-42512529", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "605353130"} {:id "def/particles", :kind "def", :line 8, :end-line 8, :hash "-805035099"} {:id "def/particle-field-size", :kind "def", :line 9, :end-line 9, :hash "-1679469677"} {:id "def/wind-field-cache", :kind "def", :line 10, :end-line 10, :hash "1826981578"} {:id "def/animation-seconds-per-frame", :kind "def", :line 11, :end-line 11, :hash "636313673"} {:id "def/wind-field-cols", :kind "def", :line 12, :end-line 12, :hash "-1232038188"} {:id "def/wind-field-rows", :kind "def", :line 13, :end-line 13, :hash "-1058793850"} {:id "def/particle-segment-min-length", :kind "def", :line 14, :end-line 14, :hash "671707306"} {:id "def/particle-segment-max-length", :kind "def", :line 15, :end-line 15, :hash "-1062122780"} {:id "def/particle-segment-pixels-per-knot", :kind "def", :line 16, :end-line 16, :hash "-515624116"} {:id "def/particle-segment-base-screen-size", :kind "def", :line 17, :end-line 17, :hash "-2128861892"} {:id "def/particle-motion-base-screen-size", :kind "def", :line 18, :end-line 18, :hash "1814956898"} {:id "def/particle-motion-speed-scale", :kind "def", :line 19, :end-line 19, :hash "-1927671676"} {:id "def/particle-fade-in-ms", :kind "def", :line 20, :end-line 20, :hash "1336373797"} {:id "def/particle-fade-out-ms", :kind "def", :line 21, :end-line 21, :hash "1556980209"} {:id "def/particle-min-life-ms", :kind "def", :line 22, :end-line 22, :hash "609523084"} {:id "def/particle-max-life-ms", :kind "def", :line 23, :end-line 23, :hash "670977351"} {:id "form/17/declare", :kind "declare", :line 25, :end-line 25, :hash "1820794180"} {:id "defn-/fractional-part", :kind "defn-", :line 27, :end-line 28, :hash "720073750"} {:id "defn/particle-coordinate", :kind "defn", :line 30, :end-line 31, :hash "-337514268"} {:id "defn/bounds-aspect-ratio-nm", :kind "defn", :line 33, :end-line 39, :hash "2076392437"} {:id "defn/fit-bounds-to-screen", :kind "defn", :line 41, :end-line 64, :hash "-1618914508"} {:id "defn/project-point", :kind "defn", :line 66, :end-line 69, :hash "-1445133276"} {:id "defn/unproject-point", :kind "defn", :line 71, :end-line 74, :hash "-1531146905"} {:id "defn/nearest-wind", :kind "defn", :line 76, :end-line 84, :hash "-1823228980"} {:id "defn-/wind-distance", :kind "defn-", :line 86, :end-line 87, :hash "-895157327"} {:id "defn/interpolated-wind", :kind "defn", :line 89, :end-line 103, :hash "1971759617"} {:id "defn/wind-speed", :kind "defn", :line 105, :end-line 106, :hash "-963648133"} {:id "defn/particle-motion-screen-scale", :kind "defn", :line 108, :end-line 109, :hash "73021701"} {:id "defn-/wind-pixel-factors", :kind "defn-", :line 111, :end-line 121, :hash "164388188"} {:id "defn-/wind-pixel-delta", :kind "defn-", :line 123, :end-line 128, :hash "692223622"} {:id "defn/wind-field-key", :kind "defn", :line 130, :end-line 137, :hash "-486797723"} {:id "defn-/wind-field-cell", :kind "defn-", :line 139, :end-line 147, :hash "1067059015"} {:id "defn/make-wind-field", :kind "defn", :line 149, :end-line 158, :hash "1616713997"} {:id "defn/current-wind-field", :kind "defn", :line 160, :end-line 167, :hash "227094913"} {:id "defn-/clamp", :kind "defn-", :line 169, :end-line 170, :hash "-870597765"} {:id "defn-/wind-cell", :kind "defn-", :line 172, :end-line 173, :hash "1227267460"} {:id "defn-/interpolate", :kind "defn-", :line 175, :end-line 176, :hash "1024992655"} {:id "defn/sample-wind-field", :kind "defn", :line 178, :end-line 198, :hash "-1535150109"} {:id "defn/particle-frame", :kind "defn", :line 200, :end-line 206, :hash "-112530792"} {:id "defn-/visible-particle-opacity", :kind "defn-", :line 208, :end-line 213, :hash "-23369782"} {:id "defn/particle-opacity", :kind "defn", :line 215, :end-line 220, :hash "-1712976452"} {:id "defn/particle-dead?", :kind "defn", :line 222, :end-line 223, :hash "-139136810"} {:id "defn/random-particle", :kind "defn", :line 225, :end-line 235, :hash "-45960960"} {:id "defn/initial-particle", :kind "defn", :line 237, :end-line 242, :hash "1303165161"} {:id "defn/make-particles", :kind "defn", :line 244, :end-line 246, :hash "-675656271"} {:id "defn-/reset-particles!", :kind "defn-", :line 248, :end-line 251, :hash "-1046136234"} {:id "defn/ensure-particles!", :kind "defn", :line 253, :end-line 257, :hash "1971030647"} {:id "def/wind-color-thresholds", :kind "def", :line 259, :end-line 265, :hash "-1446270570"} {:id "def/strong-wind-color", :kind "def", :line 267, :end-line 267, :hash "895141912"} {:id "defn/wind-color", :kind "defn", :line 269, :end-line 273, :hash "300582635"} {:id "defn/particle-drawing-values", :kind "defn", :line 275, :end-line 282, :hash "-1219187261"} {:id "defn-/out-of-bounds?", :kind "defn-", :line 284, :end-line 288, :hash "1290138187"} {:id "defn/step-particle-with-frame", :kind "defn", :line 290, :end-line 310, :hash "1288716974"} {:id "defn/step-particle", :kind "defn", :line 312, :end-line 313, :hash "2092335168"} {:id "defn/particle-segment-screen-scale", :kind "defn", :line 315, :end-line 316, :hash "-468896462"} {:id "defn/particle-segment-length", :kind "defn", :line 318, :end-line 325, :hash "653487717"} {:id "defn/particle-segment-end", :kind "defn", :line 327, :end-line 336, :hash "1132813491"} {:id "defn/draw-particle-line!", :kind "defn", :line 338, :end-line 339, :hash "1499373047"} {:id "defn/draw-particle!", :kind "defn", :line 341, :end-line 351, :hash "659787476"} {:id "defn/draw-particles!", :kind "defn", :line 353, :end-line 358, :hash "866443721"}]}
;; clj-mutate-manifest-end
