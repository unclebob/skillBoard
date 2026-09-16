(ns skillBoard.presenters.wind-map.markers
  (:require
    [quil.core :as q]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.presenters.wind-map.draw :as draw]
    [skillBoard.presenters.wind-map.geo :as geo]))

(def airport-marker-cache (atom {:time 0 :markers nil}))
(def airport-marker-cache-ms 1000)

(def ceiling-covers #{"BKN" "OVC" "VV"})

(defn metar-ceiling-ft-agl [{:keys [clouds]}]
  (when (seq clouds)
    (let [ceilings (keep (fn [{:keys [cover base]}]
                           (when (and (ceiling-covers cover) base)
                             base))
                         clouds)]
      (or (when (seq ceilings) (apply min ceilings))
          draw/ceiling-overlay-max-ft))))

(defn draw-airport! [bounds width height]
  (let [[lat lon] config/airport-lat-lon
        [x y] (geo/project-point bounds width height lat lon)]
    (q/fill 255 255 255)
    (q/ellipse x y 10 10)
    (when-let [font (draw/airport-label-font)]
      (q/text-font font))
    (q/text-align :left :top)
    (q/text-size 18)
    (q/text config/airport (+ x 8) (+ y 8))))

(defn flight-category-airport-markers []
  (let [nearby-metars @comm/polled-nearby-metars
        fallback-metars @comm/polled-metars
        airspace-classes @comm/polled-airspace-classes
        metars (if (seq nearby-metars) nearby-metars fallback-metars)
        markers (map (fn [{:keys [lat lon fltCat icaoId] :as metar}]
                       {:airport icaoId
                        :lat lat
                        :lon lon
                        :color (draw/flight-category-color fltCat)
                        :ceiling-ft-agl (metar-ceiling-ft-agl metar)
                        :airspace-class (get airspace-classes icaoId)})
                     (sort-by :icaoId (vals metars)))
        home-marker (when-not (some #(= config/airport (:airport %)) markers)
                      (if-let [{:keys [lat lon fltCat icaoId] :as metar} (get fallback-metars config/airport)]
                        {:airport (or icaoId config/airport)
                         :lat lat
                         :lon lon
                         :color (draw/flight-category-color fltCat)
                         :ceiling-ft-agl (metar-ceiling-ft-agl metar)
                         :airspace-class (get airspace-classes (or icaoId config/airport))}
                        {:airport config/airport
                         :lat (first config/airport-lat-lon)
                         :lon (second config/airport-lat-lon)
                         :color config/info-color}))]
    (cond-> (vec markers)
      home-marker (conj home-marker))))

(defn cached-flight-category-airport-markers [now]
  (let [{:keys [time markers]} @airport-marker-cache]
    (if (and markers
             (< (- now time) airport-marker-cache-ms))
      markers
      (let [markers (flight-category-airport-markers)]
        (reset! airport-marker-cache {:time now :markers markers})
        markers))))

(defn label-airport? [{:keys [airspace-class]}]
  (#{"B" "C" "D"} airspace-class))

(defn- draw-flight-category-dot! [x y color]
  (let [[r g b] (draw/color-rgb color)]
    (q/stroke 10 15 22 210)
    (q/stroke-weight 2)
    (q/fill r g b)
    (q/ellipse x y 11 11)))

(defn- draw-flight-category-label! [x y airport color]
  (let [[r g b] (draw/color-rgb color)]
    (draw/q-text-font! (draw/map-label-font))
    (q/fill r g b)
    (q/text-align :left :center)
    (q/text-size 12)
    (q/text airport (+ x 8) y)))

(defn- marker-screen-point [bounds width height {:keys [lat lon]}]
  (when (every? some? [lat lon])
    (geo/project-point bounds width height lat lon)))

(defn- draw-flight-category-label-if-needed! [x y {:keys [airport color] :as marker}]
  (when (label-airport? marker)
    (draw-flight-category-label! x y airport color)))

(defn draw-flight-category-airport! [bounds width height marker]
  (when-let [[x y] (marker-screen-point bounds width height marker)]
    (let [{:keys [color]} marker]
      (draw-flight-category-dot! x y color)
      (draw-flight-category-label-if-needed! x y marker))))

(defn draw-flight-category-airports! [bounds width height]
  (doseq [marker (flight-category-airport-markers)]
    (draw-flight-category-airport! bounds width height marker)))

(defn marker-layer-key [{:keys [airport lat lon color ceiling-ft-agl airspace-class]}]
  [airport lat lon color ceiling-ft-agl airspace-class])

(defn- draw-layer-flight-category-dot! [layer x y color]
  (let [[r g b] (draw/color-rgb color)]
    (.stroke layer 10 15 22 210)
    (.strokeWeight layer 2)
    (.fill layer r g b)
    (.ellipse layer (float x) (float y) 11 11)))

(defn- draw-layer-flight-category-label! [layer x y airport color]
  (let [[r g b] (draw/color-rgb color)]
    (draw/layer-text-font! layer (draw/map-label-font))
    (.fill layer r g b)
    (.textAlign layer processing.core.PConstants/LEFT processing.core.PConstants/CENTER)
    (.textSize layer 12)
    (.text layer (str airport) (float (+ x 8)) (float y))))

(defn- draw-layer-flight-category-label-if-needed! [layer x y {:keys [airport color] :as marker}]
  (when (label-airport? marker)
    (draw-layer-flight-category-label! layer x y airport color)))

(defn draw-layer-flight-category-airport! [layer bounds width height marker]
  (when-let [[x y] (marker-screen-point bounds width height marker)]
    (draw-layer-flight-category-dot! layer x y (:color marker))
    (draw-layer-flight-category-label-if-needed! layer x y marker)))

(defn draw-layer-flight-category-airports! [layer bounds width height markers]
  (doseq [marker markers]
    (draw-layer-flight-category-airport! layer bounds width height marker)))
