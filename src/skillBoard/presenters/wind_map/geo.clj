(ns skillBoard.presenters.wind-map.geo
  (:require
    [clojure.data.json :as json]
    [quil.core :as q]
    [skillBoard.presenters.wind-map.draw :as draw]))

(def state-outlines-cache (atom nil))

(def state-labels
  {"Wisconsin" "WI"
   "Illinois" "IL"
   "Indiana" "IN"
   "Michigan" "MI"
   "Iowa" "IA"
   "Missouri" "MO"
   "Ohio" "OH"})

(def nearby-state-names (set (keys state-labels)))

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

(defn- lon-lat->lat-lon [[lon lat]]
  [lat lon])

(def polygon-ring-readers
  {"Polygon" identity
   "MultiPolygon" #(mapcat identity %)})

(defn- polygon-rings [{:keys [type coordinates]}]
  (if-let [read-rings (polygon-ring-readers type)]
    (read-rings coordinates)
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
      (draw/q-text-font! (draw/map-label-font))
      (q/fill 130 150 165 150)
      (q/text-align :center :center)
      (q/text-size 12)
      (q/text name x y))))

(defn draw-state-outlines! [bounds width height]
  (doseq [outline (state-outlines)]
    (draw-state-outline! bounds width height outline)))

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
      (draw/layer-text-font! layer (draw/map-label-font))
      (.fill layer 130 150 165 150)
      (.textAlign layer processing.core.PConstants/CENTER processing.core.PConstants/CENTER)
      (.textSize layer 12)
      (.text layer (str name) (float x) (float y)))))

(defn draw-layer-state-outlines! [layer bounds width height]
  (doseq [outline (state-outlines)]
    (draw-layer-state-outline! layer bounds width height outline)))
