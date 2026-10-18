(ns skillBoard.adapters.web.wind
  (:require
    [skillBoard.foundation.config :as config]
    [skillBoard.presenters.utils :as utils]
    [skillBoard.presenters.wind-map.aircraft :as aircraft]
    [skillBoard.presenters.wind-map.draw :as draw]
    [skillBoard.presenters.wind-map.geo :as geo]
    [skillBoard.presenters.wind-map.markers :as markers]
    [skillBoard.presenters.wind-map.overlays :as overlays]
    [skillBoard.presenters.wind-map.particles :as particles]))

(def outline-rgb [95 115 130 145])
(def circle-rgb [255 255 255 210])
(def marker-stroke-rgb [10 15 22 210])
(def background-rgb [10 15 22])

(defn- xy [bounds width height lat lon]
  (vec (geo/project-point bounds width height lat lon)))

(defn- ring-center [ring]
  [(/ (reduce + (map first ring)) (count ring))
   (/ (reduce + (map second ring)) (count ring))])

(defn- project-ring [bounds width height ring]
  (mapv (fn [[lat lon]] (xy bounds width height lat lon)) ring))

(defn- state-view [bounds width height {:keys [name rings]}]
  (let [drawn (mapv #(project-ring bounds width height %) rings)
        [lat lon] (ring-center (first rings))]
    {:name name
     :rings drawn
     :label (xy bounds width height lat lon)}))

(defn- marker-view [bounds width height {:keys [lat lon color airport] :as marker}]
  (when (and lat lon)
    (let [[x y] (geo/project-point bounds width height lat lon)]
      {:x x
       :y y
       :rgb (draw/color-rgb color)
       :label (when (markers/label-airport? marker) airport)})))

(defn- fleet-view [bounds width height aircraft]
  (let [[x y] (geo/project-point bounds width height (:lat aircraft) (:lon aircraft))]
    {:triangle (mapv vec (aircraft/triangle-vertices x y (:trk aircraft)))
     :label (aircraft/data-block aircraft)
     :x (+ x aircraft/triangle-length)
     :y y}))

(defn- ceiling-cells [bounds width height observations]
  (let [cell-w (/ width overlays/ceiling-overlay-cols)
        cell-h (/ height overlays/ceiling-overlay-rows)]
    (vec
      (for [col (range overlays/ceiling-overlay-cols)
            row (range overlays/ceiling-overlay-rows)
            :let [color (overlays/ceiling-overlay-cell-color
                          bounds width height observations cell-w cell-h col row)]
            :when color]
        {:x (* col cell-w)
         :y (* row cell-h)
         :w cell-w
         :h cell-h
         :rgb color}))))

(defn- field-view [{:keys [width height cols rows cells]}]
  {:width width
   :height height
   :cols cols
   :rows rows
   :cells (mapv (fn [row]
                  (mapv (fn [{:keys [u v]}] [u v]) row))
                cells)})

(defn- motion []
  {:count config/wind-map-particle-count
   :min-life particles/particle-min-life-ms
   :max-life particles/particle-max-life-ms
   :fade-in particles/particle-fade-in-ms
   :fade-out particles/particle-fade-out-ms
   :segment-min particles/particle-segment-min-length
   :segment-max particles/particle-segment-max-length
   :segment-per-knot particles/particle-segment-pixels-per-knot
   :segment-base particles/particle-segment-base-screen-size
   :frame-ms (max 1 (quot 1000 config/frame-rate))})

(defn- circle-view [bounds width height grid]
  (let [points (overlays/range-circle-points grid)
        [lat lon] (overlays/range-circle-lat-lon (:center grid) (:radius-nm grid) 0)
        [x y] (geo/project-point bounds width height lat lon)]
    {:points (mapv (fn [[plat plon]] (xy bounds width height plat plon)) points)
     :label {:text (overlays/range-circle-label-text (:radius-nm grid))
             :x x
             :y (+ y (overlays/range-circle-label-offset width height))
             :size (overlays/range-circle-label-font-size width height)}}))

(defn- scale-bands [geometry bands label-fn]
  (mapv (fn [{:keys [top bottom color] :as band}]
          {:x (:x geometry)
           :y top
           :w (:width geometry)
           :h (- bottom top)
           :rgb color
           :label (label-fn band)
           :label-x (:label-x geometry)
           :label-y (/ (+ top bottom) 2.0)})
        bands))

(defn- scales [width height]
  (let [wind (overlays/wind-speed-scale-geometry width height)
        ceiling (overlays/ceiling-scale-geometry width height)]
    {:wind {:bands (scale-bands wind
                                (overlays/wind-speed-scale-bands wind)
                                overlays/wind-speed-scale-band-label)
            :title {:text "wind" :x (:title-x wind) :y (- (:y wind) 4.0)}}
     :ceiling {:bands (scale-bands ceiling
                                   (overlays/ceiling-scale-bands ceiling)
                                   (fn [{:keys [low high]}] (str low "-" high)))
               :labels (mapv (fn [{:keys [label y]}]
                               {:text label
                                :x (:label-x ceiling)
                                :y y})
                             (overlays/ceiling-scale-labels ceiling))
               :title {:text "ceil" :x (:title-x ceiling) :y (:y ceiling)}}}))

(defn- metar-view [width height short-metar]
  (let [{:keys [line color]} (overlays/current-airport-metar-label short-metar)
        geometry (overlays/split-flap-metar-geometry width height line)]
    {:rgb (draw/color-rgb color)
     :line (:line geometry)
     :x (:x geometry)
     :y (:y geometry)
     :flap-width (:flap-width geometry)
     :flap-height (:flap-height geometry)
     :font-size (:sf-font-size geometry)
     :pad-x (:backing-rect-top-left-x geometry)
     :pad-y (:backing-rect-top-left-y geometry)
     :pad-w (:backing-rect-width geometry)
     :pad-h (:backing-rect-height geometry)}))

(defn- stale-view [now grid width height short-metar]
  (when (overlays/stale-wind-data? now grid)
    (let [geometry (overlays/stale-wind-data-warning-geometry width height short-metar)]
      {:text overlays/stale-wind-data-message
       :x (:x geometry)
       :y (:y geometry)
       :size (:font-size geometry)})))

(defn scene
  ([snapshot width height]
   (scene snapshot width height (System/currentTimeMillis)))
  ([snapshot width height now]
   (let [grid (:wind-grid snapshot)]
     (when (and grid (:center grid) (:radius-nm grid) (pos? width) (pos? height))
       (let [bounds (geo/fit-bounds-to-screen
                      (geo/radius-bounds (:center grid) (:radius-nm grid))
                      width height)
             airport-markers (markers/flight-category-airport-markers
                               (:nearby-metars snapshot)
                               (:metars snapshot)
                               (:airspace-classes snapshot))
             observations (overlays/ceiling-observations airport-markers)
             frame (particles/particle-frame bounds grid width height)
             short-metar (utils/get-short-metar (:metars snapshot))
             fleet (aircraft/positioned-aircraft (aircraft/fleet-adsbs snapshot))]
         {:width width
          :height height
          :background background-rgb
          :outline outline-rgb
          :circle-rgb circle-rgb
          :marker-stroke marker-stroke-rgb
          :dx (:dx-per-knot frame)
          :dy (:dy-per-knot frame)
          :field (field-view (:wind-field frame))
          :motion (motion)
          :colors {:thresholds draw/wind-color-thresholds
                   :strong draw/strong-wind-color}
          :states (mapv #(state-view bounds width height %) (geo/state-outlines))
          :ceilings (if (seq observations)
                      (ceiling-cells bounds width height observations)
                      [])
          :circle (circle-view bounds width height grid)
          :markers (vec (keep #(marker-view bounds width height %) airport-markers))
          :fleet (mapv #(fleet-view bounds width height %) fleet)
          :source {:text (overlays/source-label-text grid)
                   :x 20
                   :y (overlays/source-label-y width height)
                   :size (draw/source-label-font-size width height)}
          :stale (stale-view now grid width height short-metar)
          :metar (metar-view width height short-metar)
          :scales (scales width height)})))))
