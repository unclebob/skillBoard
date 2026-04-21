(ns skillBoard.presenters.wind-map
  (:require
    [clojure.data.json :as json]
    [clojure.math :as math]
    [quil.core :as q]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.wind-data :as wind-data]))

(def particles (atom []))
(def particle-field-size (atom nil))
(def state-outlines-cache (atom nil))
(def static-map-layer-cache (atom {:key nil :layer nil}))
(def animation-seconds-per-frame 390)
(def particle-segment-min-length 5)
(def particle-segment-max-length 34)
(def particle-segment-pixels-per-knot 1.2)
(def particle-fade-in-ms 500)
(def particle-fade-out-ms 500)
(def particle-min-life-ms 2000)
(def particle-max-life-ms 3000)

(def state-labels
  {"Wisconsin" "WI"
   "Illinois" "IL"
   "Indiana" "IN"
   "Michigan" "MI"
   "Iowa" "IA"
   "Missouri" "MO"
   "Ohio" "OH"})

(def nearby-state-names (set (keys state-labels)))

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

(defn- lon-lat->lat-lon [[lon lat]]
  [lat lon])

(defn- polygon-rings [{:keys [type coordinates]}]
  (case type
    "Polygon" coordinates
    "MultiPolygon" (mapcat identity coordinates)
    []))

(defn- outline-bounds [rings]
  (let [points (apply concat rings)
        lats (map first points)
        lons (map second points)]
    {:top (apply max lats)
     :bottom (apply min lats)
     :left (apply min lons)
     :right (apply max lons)}))

(defn- feature->outline [{:keys [properties geometry]}]
  (let [state-name (:name properties)
        rings (mapv #(mapv lon-lat->lat-lon %) (polygon-rings geometry))]
    {:name (state-labels state-name)
     :rings rings
     :bounds (outline-bounds rings)}))

(defn load-state-outlines []
  (let [features (:features (json/read-str (slurp "resources/us-states.geojson") :key-fn keyword))]
    (->> features
         (filter #(nearby-state-names (get-in % [:properties :name])))
         (mapv feature->outline))))

(defn state-outlines []
  (or @state-outlines-cache
      (reset! state-outlines-cache (load-state-outlines))))

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

(defn particle-opacity [now {:keys [born-at life-ms]}]
  (let [elapsed (- now born-at)
        life-ms (or life-ms particle-min-life-ms)
        fade-out-start (- life-ms particle-fade-out-ms)]
    (cond
      (< elapsed 0) 0.0
      (< elapsed particle-fade-in-ms) (/ (double elapsed) particle-fade-in-ms)
      (< elapsed fade-out-start) 1.0
      (< elapsed life-ms) (/ (double (- life-ms elapsed)) particle-fade-out-ms)
      :else 0.0)))

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

(defn wind-color [speed]
  (cond
    (< speed 5) [155 210 255 205]
    (< speed 15) [165 255 190 220]
    (< speed 25) [255 242 125 235]
    :else [255 135 135 245]))

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

(defn make-wind-map-screen []
  [{:line "SURFACE WINDS" :color config/info-color}
   {:line "OPEN-METEO 10M WIND FIELD" :color config/info-color}])

(defmethod screen/make :wind-map [_]
  (make-wind-map-screen))

(defmethod screen/header-text :wind-map [_]
  "SURFACE WINDS")

(defmethod screen/display-column-headers :wind-map [_ _flap-width _header-font _label-font-size]
  nil)

(defn- map-label-font []
  (or (:annotation-font @config/display-info)
      (:header-font @config/display-info)))

(defn- airport-label-font []
  (or (:header-font @config/display-info)
      (:annotation-font @config/display-info)))

(defn flight-category-color [flt-cat]
  (case flt-cat
    "VFR" config/vfr-color
    "MVFR" config/mvfr-color
    "IFR" config/ifr-color
    "LIFR" config/lifr-color
    config/info-color))

(defn color-rgb [color]
  (case color
    :green [0 255 0]
    :blue [70 150 255]
    :red [255 60 60]
    :magenta [255 0 255]
    :yellow [255 235 90]
    :white [255 255 255]
    [255 255 255]))

(defn draw-airport! [bounds width height]
  (let [[lat lon] config/airport-lat-lon
        [x y] (project-point bounds width height lat lon)]
    (q/fill 255 255 255)
    (q/ellipse x y 10 10)
    (when-let [font (airport-label-font)]
      (q/text-font font))
    (q/text-align :left :top)
    (q/text-size 18)
    (q/text config/airport (+ x 8) (+ y 8))))

(defn flight-category-airport-markers []
  (let [nearby-metars @comm/polled-nearby-metars
        fallback-metars @comm/polled-metars
        airspace-classes @comm/polled-airspace-classes
        metars (if (seq nearby-metars) nearby-metars fallback-metars)
        markers (map (fn [{:keys [lat lon fltCat icaoId]}]
                       {:airport icaoId
                        :lat lat
                        :lon lon
                        :color (flight-category-color fltCat)
                        :airspace-class (get airspace-classes icaoId)})
                     (sort-by :icaoId (vals metars)))
        home-marker (when-not (some #(= config/airport (:airport %)) markers)
                      (if-let [{:keys [lat lon fltCat icaoId]} (get fallback-metars config/airport)]
                        {:airport (or icaoId config/airport)
                         :lat lat
                         :lon lon
                         :color (flight-category-color fltCat)
                         :airspace-class (get airspace-classes (or icaoId config/airport))}
                        {:airport config/airport
                         :lat (first config/airport-lat-lon)
                         :lon (second config/airport-lat-lon)
                         :color config/info-color}))]
    (cond-> (vec markers)
      home-marker (conj home-marker))))

(defn label-airport? [{:keys [airspace-class]}]
  (#{"B" "C" "D"} airspace-class))

(defn draw-flight-category-airport! [bounds width height {:keys [airport lat lon color] :as marker}]
  (when (and lat lon)
    (let [[x y] (project-point bounds width height lat lon)
          [r g b] (color-rgb color)]
      (q/stroke 10 15 22 210)
      (q/stroke-weight 2)
      (q/fill r g b)
      (q/ellipse x y 11 11)
      (when (label-airport? marker)
        (when-let [font (map-label-font)]
          (q/text-font font))
        (q/fill r g b)
        (q/text-align :left :center)
        (q/text-size 12)
        (q/text airport (+ x 8) y)))))

(defn draw-flight-category-airports! [bounds width height]
  (doseq [marker (flight-category-airport-markers)]
    (draw-flight-category-airport! bounds width height marker)))

(defn marker-layer-key [{:keys [airport lat lon color airspace-class]}]
  [airport lat lon color airspace-class])

(defn static-map-layer-key [bounds width height grid markers]
  [width
   height
   bounds
   (:source grid)
   (:radius-nm grid)
   (mapv marker-layer-key markers)])

(defn- current-static-map-layer [width height]
  (let [{:keys [layer]} @static-map-layer-cache
        size-match? (and (some? layer)
                         (= (.-width layer) width)
                         (= (.-height layer) height))]
    {:layer (if size-match? layer (q/create-graphics width height))
     :size-match? size-match?}))

(defn- bounds-intersect? [a b]
  (and (<= (:bottom a) (:top b))
       (>= (:top a) (:bottom b))
       (<= (:left a) (:right b))
       (>= (:right a) (:left b))))

(defn- ring-center [ring]
  (let [lat (/ (reduce + (map first ring)) (count ring))
        lon (/ (reduce + (map second ring)) (count ring))]
    [lat lon]))

(defn draw-state-outline! [bounds width height {:keys [name rings] :as outline}]
  (when (bounds-intersect? bounds (:bounds outline))
    (q/no-fill)
    (q/stroke 95 115 130 145)
    (q/stroke-weight 1)
    (doseq [ring rings]
      (q/begin-shape)
      (doseq [[lat lon] ring
              :let [[x y] (project-point bounds width height lat lon)]]
        (q/vertex x y))
      (q/end-shape))
    (let [[label-lat label-lon] (ring-center (first rings))
          [x y] (project-point bounds width height label-lat label-lon)]
      (when-let [font (map-label-font)]
        (q/text-font font))
      (q/fill 130 150 165 150)
      (q/text-align :center :center)
      (q/text-size 12)
      (q/text name x y))))

(defn draw-state-outlines! [bounds width height]
  (doseq [outline (state-outlines)]
    (draw-state-outline! bounds width height outline)))

(defn- layer-text-font! [layer font]
  (when font
    (.textFont layer font)))

(defn- draw-layer-flight-category-airport! [layer bounds width height {:keys [airport lat lon color] :as marker}]
  (when (and lat lon)
    (let [[x y] (project-point bounds width height lat lon)
          [r g b] (color-rgb color)]
      (.stroke layer 10 15 22 210)
      (.strokeWeight layer 2)
      (.fill layer r g b)
      (.ellipse layer (float x) (float y) 11 11)
      (when (label-airport? marker)
        (layer-text-font! layer (map-label-font))
        (.fill layer r g b)
        (.textAlign layer processing.core.PConstants/LEFT processing.core.PConstants/CENTER)
        (.textSize layer 12)
        (.text layer (str airport) (float (+ x 8)) (float y))))))

(defn- draw-layer-flight-category-airports! [layer bounds width height markers]
  (doseq [marker markers]
    (draw-layer-flight-category-airport! layer bounds width height marker)))

(defn- draw-layer-state-outline! [layer bounds width height {:keys [name rings] :as outline}]
  (when (bounds-intersect? bounds (:bounds outline))
    (.noFill layer)
    (.stroke layer 95 115 130 145)
    (.strokeWeight layer 1)
    (doseq [ring rings]
      (.beginShape layer)
      (doseq [[lat lon] ring
              :let [[x y] (project-point bounds width height lat lon)]]
        (.vertex layer (float x) (float y)))
      (.endShape layer))
    (let [[label-lat label-lon] (ring-center (first rings))
          [x y] (project-point bounds width height label-lat label-lon)]
      (layer-text-font! layer (map-label-font))
      (.fill layer 130 150 165 150)
      (.textAlign layer processing.core.PConstants/CENTER processing.core.PConstants/CENTER)
      (.textSize layer 12)
      (.text layer (str name) (float x) (float y)))))

(defn- draw-layer-state-outlines! [layer bounds width height]
  (doseq [outline (state-outlines)]
    (draw-layer-state-outline! layer bounds width height outline)))

(defn- draw-layer-source-label! [layer grid height]
  (.fill layer 255 255 255)
  (layer-text-font! layer (map-label-font))
  (.textAlign layer processing.core.PConstants/LEFT processing.core.PConstants/BOTTOM)
  (.textSize layer 13)
  (.text layer
         (str "Source: " (name (:source grid)) "  Radius: " (:radius-nm grid) " NM")
         (float 20)
         (float (- height 20))))

(defn draw-source-label! [grid height]
  (q/fill 255 255 255)
  (when-let [font (map-label-font)]
    (q/text-font font))
  (q/text-align :left :bottom)
  (q/text-size 13)
  (q/text (str "Source: " (name (:source grid)) "  Radius: " (:radius-nm grid) " NM") 20 (- height 20)))

(defn- render-static-map-layer! [layer bounds width height grid _markers]
  (q/with-graphics layer
    (q/background 10 15 22)
    (draw-state-outlines! bounds width height)
    (draw-flight-category-airports! bounds width height)
    (draw-source-label! grid height)))

(defn static-map-layer [bounds width height grid markers]
  (let [layer-key (static-map-layer-key bounds width height grid markers)
        {cached-key :key} @static-map-layer-cache
        {:keys [layer size-match?]} (current-static-map-layer width height)]
    (when (or (not size-match?) (not= cached-key layer-key))
      (render-static-map-layer! layer bounds width height grid markers))
    (reset! static-map-layer-cache {:key layer-key :layer layer})
    layer))

(defn particle-segment-length [speed]
  (min particle-segment-max-length
       (max particle-segment-min-length
            (* speed particle-segment-pixels-per-knot))))

(defn particle-segment-end [{:keys [x y u v speed]}]
  (let [speed (or speed (wind-speed {:u (or u 0) :v (or v 0)}))
        length (if (pos? speed) (particle-segment-length speed) 0)
        unit-x (if (pos? speed) (/ (or u 0) speed) 0)
        unit-y (if (pos? speed) (/ (or v 0) speed) 0)]
    [(+ x (* unit-x length))
     (- y (* unit-y length))]))

(defn draw-particle! [particle]
  (let [[r g b a] (wind-color (:speed particle 0))
        a (* a (:opacity particle 1.0))
        [x2 y2] (particle-segment-end particle)]
    (q/stroke-weight 2)
    (q/stroke r g b a)
    (q/line (:x particle) (:y particle) x2 y2)))

(defn draw-wind-map! []
  (let [grid (wind-data/current-grid)
        bounds (wind-data/radius-bounds (:center grid) (:radius-nm grid))
        width (q/width)
        height (q/height)
        now (System/currentTimeMillis)
        markers (flight-category-airport-markers)
        layer (static-map-layer bounds width height grid markers)
        _ (ensure-particles! bounds grid width height now)
        updated (mapv #(step-particle bounds grid width height now %) @particles)]
    (reset! particles updated)
    (q/image layer 0 0)
    (doseq [particle updated]
      (draw-particle! particle))))

(defmethod screen/draw-body :wind-map [_ _state]
  (draw-wind-map!)
  true)
