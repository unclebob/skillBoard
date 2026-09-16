(ns skillBoard.presenters.wind-map.layer
  (:require
    [quil.core :as q]
    [skillBoard.foundation.atoms :as atoms]
    [skillBoard.presenters.wind-map.geo :as geo]
    [skillBoard.presenters.wind-map.markers :as markers]
    [skillBoard.presenters.wind-map.overlays :as overlays]))

(def static-map-layer-cache (atom {:key nil :layer nil}))

(defn static-map-layer-key [bounds width height grid markers short-metar]
  [width
   height
   bounds
   (:source grid)
   (:generated-at-ms grid)
   (:radius-nm grid)
   (overlays/current-airport-metar-label short-metar)
   (mapv markers/marker-layer-key markers)])

(defn- layer-size [layer]
  (when layer
    [(.-width layer) (.-height layer)]))

(defn- current-or-new-layer [layer size-match? width height]
  (if size-match?
    layer
    (q/create-graphics width height)))

(defn- current-static-map-layer [width height]
  (let [{:keys [layer]} @static-map-layer-cache
        size-match? (= [width height] (layer-size layer))]
    {:layer (current-or-new-layer layer size-match? width height)
     :size-match? size-match?}))

(defn- static-map-layer-stale? [cached-key layer-key size-match?]
  (not (and size-match? (= cached-key layer-key))))

(defn render-static-map-layer! [layer bounds width height grid markers short-metar]
  (q/with-graphics layer
    (q/background 10 15 22)
    (geo/draw-state-outlines! bounds width height)
    (overlays/draw-layer-ceiling-overlay! layer bounds width height markers)
    (overlays/draw-layer-valid-range-circle! layer bounds width height grid)
    (doseq [marker markers]
      (markers/draw-flight-category-airport! bounds width height marker))
    (overlays/draw-layer-ceiling-scale! layer width height)
    (overlays/draw-layer-wind-speed-scale! layer width height)
    (overlays/draw-source-label! grid width height)
    (overlays/draw-layer-current-airport-metar! layer width height short-metar)))

(defn static-map-layer [bounds width height grid markers short-metar]
  (let [layer-key (static-map-layer-key bounds width height grid markers short-metar)
        {cached-key :key} @static-map-layer-cache
        {:keys [layer size-match?]} (current-static-map-layer width height)]
    (when (static-map-layer-stale? cached-key layer-key size-match?)
      (render-static-map-layer! layer bounds width height grid markers short-metar))
    (reset! static-map-layer-cache {:key layer-key :layer layer})
    layer))

(defn refresh-static-map-layer-on-screen-entry! []
  (when @atoms/screen-changed?
    (reset! static-map-layer-cache {:key nil :layer nil})))
