(ns skillBoard.presenters.wind-map
  (:require
    [quil.core :as q]
    [skillBoard.config :as config]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.presenters.wind-map.geo :as geo]
    [skillBoard.presenters.wind-map.layer :as map-layer]
    [skillBoard.presenters.wind-map.markers :as markers]
    [skillBoard.presenters.wind-map.overlays :as overlays]
    [skillBoard.presenters.wind-map.particles :as particles]
    [skillBoard.wind-data :as wind-data]))

(defn make-wind-map-screen []
  [{:line "SURFACE WINDS" :color config/info-color}
   {:line "OPEN-METEO 10M WIND FIELD" :color config/info-color}])

(defmethod screen/make :wind-map [_]
  (make-wind-map-screen))

(defmethod screen/header-text :wind-map [_]
  "SURFACE WINDS")

(defmethod screen/display-column-headers :wind-map [_ _flap-width _header-font _label-font-size]
  nil)

(defn draw-wind-map! []
  (let [grid (wind-data/current-grid)
        width (q/width)
        height (q/height)
        base-bounds (wind-data/radius-bounds (:center grid) (:radius-nm grid))
        bounds (geo/fit-bounds-to-screen base-bounds width height)
        now (System/currentTimeMillis)
        _ (map-layer/refresh-static-map-layer-on-screen-entry!)
        airport-markers (markers/cached-flight-category-airport-markers now)
        layer (map-layer/static-map-layer bounds width height grid airport-markers)
        _ (particles/ensure-particles! bounds grid width height now)
        frame (particles/particle-frame bounds grid width height)
        updated (mapv #(particles/step-particle-with-frame frame now %) @particles/particles)]
    (reset! particles/particles updated)
    (q/image layer 0 0)
    (overlays/draw-stale-wind-data-warning! now grid width height)
    (particles/draw-particles! updated)))

(defmethod screen/draw-body :wind-map [_ _state]
  (draw-wind-map!)
  true)
