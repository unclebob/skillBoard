(ns skillBoard.presenters.wind-map
  (:require
    [clojure.data.json :as json]
    [quil.core :as q]
    [skillBoard.atoms :as atoms]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.core-utils :as core-utils]
    [skillBoard.presenters.utils :as utils]
    [skillBoard.presenters.wind-map.particles :as wind-particles]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.wind-data :as wind-data]))

(def state-outlines-cache (atom nil))
(def static-map-layer-cache (atom {:key nil :layer nil}))
(def airport-marker-cache (atom {:time 0 :markers nil}))
(def airport-marker-cache-ms 1000)
(def stale-wind-data-ms (* 2 60 60 1000))
(def stale-wind-data-message "WIND DATA IS OUT OF DATE")
(def ceiling-overlay-cols 36)
(def ceiling-overlay-rows 24)
(def ceiling-overlay-max-ft 10000)
(def ceiling-overlay-cell-alpha 42)

(def particles wind-particles/particles)
(def particle-field-size wind-particles/particle-field-size)
(def wind-field-cache wind-particles/wind-field-cache)
(def animation-seconds-per-frame wind-particles/animation-seconds-per-frame)
(def wind-field-cols wind-particles/wind-field-cols)
(def wind-field-rows wind-particles/wind-field-rows)
(def particle-segment-min-length wind-particles/particle-segment-min-length)
(def particle-segment-max-length wind-particles/particle-segment-max-length)
(def particle-segment-pixels-per-knot wind-particles/particle-segment-pixels-per-knot)
(def particle-segment-screen-scale wind-particles/particle-segment-screen-scale)
(def particle-motion-screen-scale wind-particles/particle-motion-screen-scale)
(def particle-fade-in-ms wind-particles/particle-fade-in-ms)
(def particle-fade-out-ms wind-particles/particle-fade-out-ms)
(def particle-min-life-ms wind-particles/particle-min-life-ms)
(def particle-max-life-ms wind-particles/particle-max-life-ms)
(def particle-coordinate wind-particles/particle-coordinate)
(def fit-bounds-to-screen wind-particles/fit-bounds-to-screen)
(def project-point wind-particles/project-point)
(def unproject-point wind-particles/unproject-point)
(def nearest-wind wind-particles/nearest-wind)
(def interpolated-wind wind-particles/interpolated-wind)
(def wind-speed wind-particles/wind-speed)
(def particle-opacity wind-particles/particle-opacity)
(def particle-dead? wind-particles/particle-dead?)
(def random-particle wind-particles/random-particle)
(def initial-particle wind-particles/initial-particle)
(def make-particles wind-particles/make-particles)
(def ensure-particles! wind-particles/ensure-particles!)
(def wind-field-key wind-particles/wind-field-key)
(def make-wind-field wind-particles/make-wind-field)
(def current-wind-field wind-particles/current-wind-field)
(def sample-wind-field wind-particles/sample-wind-field)
(def particle-frame wind-particles/particle-frame)
(def wind-color wind-particles/wind-color)
(def step-particle-with-frame wind-particles/step-particle-with-frame)
(def step-particle wind-particles/step-particle)
(def particle-segment-length wind-particles/particle-segment-length)
(def particle-segment-end wind-particles/particle-segment-end)
(def draw-particle! wind-particles/draw-particle!)
(def draw-particles! wind-particles/draw-particles!)

(def state-labels
  {"Wisconsin" "WI"
   "Illinois" "IL"
   "Indiana" "IN"
   "Michigan" "MI"
   "Iowa" "IA"
   "Missouri" "MO"
   "Ohio" "OH"})

(def nearby-state-names (set (keys state-labels)))

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
  (or (:header-font @config/display-info)
      (:annotation-font @config/display-info)))

(defn- airport-label-font []
  (or (:header-font @config/display-info)
      (:annotation-font @config/display-info)))

(defn- metar-label-font []
  (or (:metar-font @config/display-info)
      (:annotation-font @config/display-info)
      (:header-font @config/display-info)))

(defn- q-text-font! [font]
  (when font
    (q/text-font font)))

(def flight-category-colors
  {"VFR" config/vfr-color
   "MVFR" config/mvfr-color
   "IFR" config/ifr-color
   "LIFR" config/lifr-color})

(defn flight-category-color [flt-cat]
  (get flight-category-colors flt-cat config/info-color))

(def color-rgb-values
  {:green [0 255 0]
   :blue [70 150 255]
   :red [255 60 60]
   :magenta [255 0 255]
   :yellow [255 235 90]
   :white [255 255 255]})

(def default-color-rgb [255 255 255])

(defn color-rgb [color]
  (get color-rgb-values color default-color-rgb))

(def ceiling-covers #{"BKN" "OVC" "VV"})

(defn metar-ceiling-ft-agl [{:keys [clouds]}]
  (when (seq clouds)
    (let [ceilings (keep (fn [{:keys [cover base]}]
                           (when (and (ceiling-covers cover) base)
                             base))
                         clouds)]
      (or (when (seq ceilings) (apply min ceilings))
          ceiling-overlay-max-ft))))

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
        markers (map (fn [{:keys [lat lon fltCat icaoId] :as metar}]
                       {:airport icaoId
                        :lat lat
                        :lon lon
                        :color (flight-category-color fltCat)
                        :ceiling-ft-agl (metar-ceiling-ft-agl metar)
                        :airspace-class (get airspace-classes icaoId)})
                     (sort-by :icaoId (vals metars)))
        home-marker (when-not (some #(= config/airport (:airport %)) markers)
                      (if-let [{:keys [lat lon fltCat icaoId] :as metar} (get fallback-metars config/airport)]
                        {:airport (or icaoId config/airport)
                         :lat lat
                         :lon lon
                         :color (flight-category-color fltCat)
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
  (let [[r g b] (color-rgb color)]
    (q/stroke 10 15 22 210)
    (q/stroke-weight 2)
    (q/fill r g b)
    (q/ellipse x y 11 11)))

(defn- draw-flight-category-label! [x y airport color]
  (let [[r g b] (color-rgb color)]
    (q-text-font! (map-label-font))
    (q/fill r g b)
    (q/text-align :left :center)
    (q/text-size 12)
    (q/text airport (+ x 8) y)))

(defn- marker-screen-point [bounds width height {:keys [lat lon]}]
  (when (every? some? [lat lon])
    (project-point bounds width height lat lon)))

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

(defn current-airport-metar-label []
  (utils/get-short-metar config/airport))

(defn static-map-layer-key [bounds width height grid markers]
  [width
   height
   bounds
   (:source grid)
   (:generated-at-ms grid)
   (:radius-nm grid)
   (current-airport-metar-label)
   (mapv marker-layer-key markers)])

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
      (q-text-font! (map-label-font))
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

(defn- draw-layer-flight-category-dot! [layer x y color]
  (let [[r g b] (color-rgb color)]
    (.stroke layer 10 15 22 210)
    (.strokeWeight layer 2)
    (.fill layer r g b)
    (.ellipse layer (float x) (float y) 11 11)))

(defn- draw-layer-flight-category-label! [layer x y airport color]
  (let [[r g b] (color-rgb color)]
    (layer-text-font! layer (map-label-font))
    (.fill layer r g b)
    (.textAlign layer processing.core.PConstants/LEFT processing.core.PConstants/CENTER)
    (.textSize layer 12)
    (.text layer (str airport) (float (+ x 8)) (float y))))

(defn- draw-layer-flight-category-label-if-needed! [layer x y {:keys [airport color] :as marker}]
  (when (label-airport? marker)
    (draw-layer-flight-category-label! layer x y airport color)))

(defn- draw-layer-flight-category-airport! [layer bounds width height marker]
  (when-let [[x y] (marker-screen-point bounds width height marker)]
    (draw-layer-flight-category-dot! layer x y (:color marker))
    (draw-layer-flight-category-label-if-needed! layer x y marker)))

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

(declare source-label-font-size)

(def range-circle-point-count 72)

(defn range-circle-lat-lon [[center-lat center-lon] radius-nm bearing-degrees]
  (let [bearing (Math/toRadians bearing-degrees)
        lat-radius (/ radius-nm 60.0)
        lon-radius (/ radius-nm (* 60.0 (Math/cos (Math/toRadians center-lat))))]
    [(+ center-lat (* lat-radius (Math/cos bearing)))
     (+ center-lon (* lon-radius (Math/sin bearing)))]))

(defn range-circle-points [{:keys [center radius-nm]}]
  (when (every? some? [center radius-nm])
    (let [bearings (map #(* % (/ 360.0 range-circle-point-count))
                        (range range-circle-point-count))
          points (mapv #(range-circle-lat-lon center radius-nm %) bearings)]
      (conj points (first points)))))

(defn range-circle-label-text [radius-nm]
  (str "valid range: " radius-nm "NM"))

(defn range-circle-label-font-size [width height]
  (source-label-font-size width height))

(defn range-circle-label-offset [width height]
  (* 2 (range-circle-label-font-size width height)))

(defn- draw-layer-valid-range-circle! [layer bounds width height {:keys [center radius-nm] :as grid}]
  (when-let [points (range-circle-points grid)]
    (.noFill layer)
    (.stroke layer 255 255 255 210)
    (.strokeWeight layer 1)
    (.beginShape layer)
    (doseq [[lat lon] points
            :let [[x y] (project-point bounds width height lat lon)]]
      (.vertex layer (float x) (float y)))
    (.endShape layer)
    (let [[label-lat label-lon] (range-circle-lat-lon center radius-nm 0)
          [x y] (project-point bounds width height label-lat label-lon)]
      (layer-text-font! layer (map-label-font))
      (.fill layer 255 255 255)
      (.textAlign layer processing.core.PConstants/CENTER processing.core.PConstants/TOP)
      (.textSize layer (range-circle-label-font-size width height))
      (.text layer (range-circle-label-text radius-nm) (float x) (float (+ y (range-circle-label-offset width height)))))))

(defn ceiling-observations [markers]
  (filterv (fn [{:keys [lat lon ceiling-ft-agl]}]
             (and lat lon (number? ceiling-ft-agl)))
           markers))

(defn- ceiling-distance [lat lon observation]
  (max 0.5 (wind-data/nm-distance [lat lon] [(:lat observation) (:lon observation)])))

(defn- weighted-ceiling [lat lon observation]
  (let [distance (ceiling-distance lat lon observation)
        weight (/ 1.0 (* distance distance))]
    {:weight weight
     :ceiling (* weight (:ceiling-ft-agl observation))}))

(defn interpolated-ceiling-ft-agl [observations lat lon]
  (let [nearby (take 6 (sort-by #(ceiling-distance lat lon %) observations))
        weighted (map #(weighted-ceiling lat lon %) nearby)
        total-weight (reduce + (map :weight weighted))]
    (when (pos? total-weight)
      (/ (reduce + (map :ceiling weighted)) total-weight))))

(defn ceiling-band-upper-ft [ceiling-ft-agl]
  (max 500 (* 500 (Math/ceil (/ ceiling-ft-agl 500.0)))))

(def ceiling-color-stops
  [{:ceiling 0 :color [255 50 50]}
   {:ceiling 1000 :color [255 125 50]}
   {:ceiling 3000 :color [255 230 80]}
   {:ceiling 5000 :color [85 220 105]}
   {:ceiling 8000 :color [70 210 255]}
   {:ceiling ceiling-overlay-max-ft :color [70 120 255]}])

(defn- interpolate-channel [a b ratio]
  (int (+ a (* (- b a) ratio))))

(defn- color-between-stops [{low-ceiling :ceiling low-color :color}
                            {high-ceiling :ceiling high-color :color}
                            ceiling]
  (let [ratio (if (= low-ceiling high-ceiling)
                0.0
                (/ (- ceiling low-ceiling) (- high-ceiling low-ceiling)))]
    (mapv interpolate-channel low-color high-color (repeat ratio))))

(defn- ceiling-overlay-visible? [ceiling-ft-agl]
  (boolean (some-> ceiling-ft-agl (< ceiling-overlay-max-ft))))

(defn- ceiling-overlay-band-color [ceiling-ft-agl]
  (let [band-ceiling (min ceiling-overlay-max-ft (ceiling-band-upper-ft ceiling-ft-agl))
        high-stop (first (filter #(<= band-ceiling (:ceiling %)) (rest ceiling-color-stops)))
        low-stop (last (take-while #(< (:ceiling %) (:ceiling high-stop)) ceiling-color-stops))]
    (conj (color-between-stops low-stop high-stop band-ceiling)
          ceiling-overlay-cell-alpha)))

(defn ceiling-overlay-color [ceiling-ft-agl]
  (when (ceiling-overlay-visible? ceiling-ft-agl)
    (ceiling-overlay-band-color ceiling-ft-agl)))

(defn ceiling-scale-color [ceiling-ft-agl]
  (when-let [[r g b _a] (ceiling-overlay-color ceiling-ft-agl)]
    [r g b 210]))

(defn ceiling-overlay-cell-color [bounds width height observations cell-width cell-height col row]
  (let [x (* col cell-width)
        y (* row cell-height)
        [lat lon] (unproject-point bounds width height (+ x (/ cell-width 2.0)) (+ y (/ cell-height 2.0)))
        ceiling (interpolated-ceiling-ft-agl observations lat lon)]
    (ceiling-overlay-color ceiling)))

(defn- draw-layer-ceiling-cell! [layer bounds width height observations cell-width cell-height col row]
  (when-let [[r g b a] (ceiling-overlay-cell-color bounds width height observations cell-width cell-height col row)]
    (let [x (* col cell-width)
          y (* row cell-height)]
      (.fill layer r g b a)
      (.rect layer (float x) (float y) (float cell-width) (float cell-height)))))

(defn- draw-layer-ceiling-overlay! [layer bounds width height markers]
  (let [observations (ceiling-observations markers)
        cell-width (/ width ceiling-overlay-cols)
        cell-height (/ height ceiling-overlay-rows)]
    (when (seq observations)
      (.noStroke layer)
      (doseq [col (range ceiling-overlay-cols)
              row (range ceiling-overlay-rows)]
        (draw-layer-ceiling-cell! layer bounds width height observations cell-width cell-height col row)))))

(defn- format-generated-at [generated-at-ms]
  (try
    (.format (java.time.ZonedDateTime/ofInstant
               (java.time.Instant/ofEpochMilli generated-at-ms)
               (java.time.ZoneId/of "UTC"))
             (java.time.format.DateTimeFormatter/ofPattern "HH:mm'Z'"))
    (catch Exception e
      (core-utils/log :error (str "Error formatting wind poll time: " (.getMessage e)))
      "unknown")))

(defn- generated-at-text [generated-at-ms]
  (if generated-at-ms
    (format-generated-at generated-at-ms)
    "unknown"))

(defn source-label-text [{:keys [source radius-nm generated-at-ms]}]
  (str "Source: " (name source) "  Radius: " radius-nm " NM  Polled: "
       (generated-at-text generated-at-ms)
       " UTC"))

(declare split-flap-metar-geometry)

(defn source-label-font-size [width height]
  (max 9 (int (/ (min width height) 45))))

(defn source-label-y [width height]
  height)

(defn- draw-layer-source-label! [layer grid width height]
  (.fill layer 255 255 255)
  (layer-text-font! layer (map-label-font))
  (.textAlign layer processing.core.PConstants/LEFT processing.core.PConstants/BOTTOM)
  (.textSize layer (source-label-font-size width height))
  (.text layer
         (source-label-text grid)
         (float 20)
         (float (source-label-y width height))))

(defn draw-source-label! [grid width height]
  (try
    (q/fill 255 255 255)
    (q-text-font! (map-label-font))
    (q/text-align :left :bottom)
    (q/text-size (source-label-font-size width height))
    (q/text (source-label-text grid) 20 (source-label-y width height))
    (catch Exception e
      (core-utils/log :error (str "Error drawing wind source label: " (.getMessage e))))))

(defn metar-split-flap-metrics [width height]
  (let [base-font-size (max 10 (int (/ (min width height) 22)))
        defaults {:sf-font-size base-font-size
                  :font-width (* base-font-size 0.58)
                  :font-height base-font-size}
        configured (select-keys @config/display-info [:sf-font-size :font-width :font-height :sf-char-gap])
        {:keys [sf-font-size font-width font-height sf-char-gap]} (merge defaults configured)
        sf-char-gap (get (merge {:sf-char-gap (* font-width config/sf-char-gap)} configured) :sf-char-gap)]
    {:sf-font-size sf-font-size
     :font-width font-width
     :font-height font-height
     :sf-char-gap sf-char-gap
     :flap-width (+ font-width sf-char-gap)
     :flap-height (* font-height (inc config/sf-line-gap))}))

(defn metar-margin [width height]
  (max 12.0 (* 0.025 (min width height))))

(defn metar-max-chars [width margin flap-width]
  (max 1 (int (Math/floor (/ (- width (* 2 margin)) flap-width)))))

(defn truncate-metar-line [text max-chars]
  (let [line (str text)]
    (subs line 0 (min (count line) max-chars))))

(defn split-flap-metar-geometry [width height text]
  (let [{:keys [sf-font-size font-width font-height flap-width flap-height]} (metar-split-flap-metrics width height)
        margin (metar-margin width height)
        line (truncate-metar-line text (metar-max-chars width margin flap-width))
        line-width (* (count line) flap-width)]
    {:line line
     :sf-font-size sf-font-size
     :flap-width flap-width
     :flap-height flap-height
     :x (max margin (- width margin line-width))
     :y (- height margin flap-height)
     :backing-rect-top-left-x (* font-width 0.1)
     :backing-rect-top-left-y (* font-height 0.1)
     :backing-rect-width (* font-width 0.8)
     :backing-rect-height (* font-height 0.8)}))

(defn- draw-layer-split-flap-line! [layer {:keys [line sf-font-size flap-width x y
                                                  backing-rect-top-left-x backing-rect-top-left-y
                                                  backing-rect-width backing-rect-height]}
                                   color]
  (let [[r g b] (color-rgb color)]
    (.noStroke layer)
    (layer-text-font! layer (:sf-font @config/display-info))
    (.textSize layer sf-font-size)
    (.textAlign layer processing.core.PConstants/LEFT processing.core.PConstants/TOP)
    (doseq [[idx c] (map-indexed vector line)
            :when (not= c \space)
            :let [char-x (+ x (* idx flap-width))]]
      (.fill layer r g b)
      (.rect layer
             (float (+ char-x backing-rect-top-left-x))
             (float (+ y backing-rect-top-left-y))
             (float backing-rect-width)
             (float backing-rect-height))
      (.fill layer 0 0 0)
      (.text layer (str c) (float char-x) (float y)))))

(defn- draw-split-flap-line! [{:keys [line sf-font-size flap-width x y
                                      backing-rect-top-left-x backing-rect-top-left-y
                                      backing-rect-width backing-rect-height]}
                              color]
  (let [[r g b] (color-rgb color)]
    (q/no-stroke)
    (q-text-font! (:sf-font @config/display-info))
    (q/text-size sf-font-size)
    (q/text-align :left :top)
    (doseq [[idx c] (map-indexed vector line)
            :when (not= c \space)
            :let [char-x (+ x (* idx flap-width))]]
      (q/fill r g b)
      (q/rect (+ char-x backing-rect-top-left-x)
              (+ y backing-rect-top-left-y)
              backing-rect-width
              backing-rect-height)
      (q/fill 0 0 0)
      (q/text (str c) char-x y))))

(defn- draw-layer-current-airport-metar! [layer width height]
  (try
    (let [{:keys [line color]} (current-airport-metar-label)
          geometry (split-flap-metar-geometry width height line)]
      (draw-layer-split-flap-line! layer geometry color))
    (catch Exception e
      (core-utils/log :error (str "Error drawing wind METAR label: " (.getMessage e))))))

(defn draw-current-airport-metar! [width height]
  (try
    (let [{:keys [line color]} (current-airport-metar-label)
          geometry (split-flap-metar-geometry width height line)]
      (draw-split-flap-line! geometry color))
    (catch Exception e
      (core-utils/log :error (str "Error drawing wind METAR label: " (.getMessage e))))))

(def wind-speed-scale-max 30)

(defn wind-speed-scale-geometry [width height]
  (let [scale-height (/ height 3.0)
        strip-width (max 8.0 (* 0.02 (min width height)))]
    {:x (- width strip-width)
     :y (/ height 3.0)
     :width strip-width
     :height scale-height
     :screen-width width
     :screen-height height
     :title-x width
     :label-x (- width strip-width 6.0)}))

(defn wind-speed-scale-y [{:keys [y height]} speed]
  (+ y (* height (- 1.0 (/ speed wind-speed-scale-max)))))

(defn wind-speed-scale-bands [{:keys [y height]}]
  (let [speeds [0 5 10 15 20 25 wind-speed-scale-max]]
    (mapv (fn [[low high]]
            {:low low
             :high high
             :top (+ y (* height (- 1.0 (/ high wind-speed-scale-max))))
             :bottom (+ y (* height (- 1.0 (/ low wind-speed-scale-max))))
             :color (wind-color low)})
          (partition 2 1 speeds))))

(defn wind-speed-scale-band-label [{:keys [low high]}]
  (if (= high wind-speed-scale-max)
    (str low "+")
    (str low "-" high)))

(defn wind-speed-scale-label-font-size [width height]
  (* 0.88 (source-label-font-size width height)))

(defn- draw-layer-scale-band! [layer {:keys [x width]} {:keys [top bottom color]}]
  (let [[r g b a] color]
    (.fill layer r g b a)
    (.rect layer (float x) (float top) (float width) (float (- bottom top)))))

(defn- draw-layer-wind-speed-scale-label! [layer geometry {:keys [top bottom] :as band}]
  (.fill layer 255 255 255)
  (layer-text-font! layer (map-label-font))
  (.textAlign layer processing.core.PConstants/RIGHT processing.core.PConstants/CENTER)
  (.textSize layer (wind-speed-scale-label-font-size (:screen-width geometry) (:screen-height geometry)))
  (.text layer
         (wind-speed-scale-band-label band)
         (float (:label-x geometry))
         (float (/ (+ top bottom) 2.0))))

(defn scale-title-font-size [width height]
  (* 1.1 (source-label-font-size width height)))

(defn- draw-layer-wind-speed-scale-title! [layer geometry]
  (.fill layer 255 255 255)
  (layer-text-font! layer (map-label-font))
  (.textAlign layer processing.core.PConstants/RIGHT processing.core.PConstants/BOTTOM)
  (.textSize layer (scale-title-font-size (:screen-width geometry) (:screen-height geometry)))
  (.text layer "wind" (float (:title-x geometry)) (float (- (:y geometry) 4.0))))

(defn- draw-layer-wind-speed-scale! [layer width height]
  (let [geometry (wind-speed-scale-geometry width height)]
    (.noStroke layer)
    (doseq [band (wind-speed-scale-bands geometry)]
      (draw-layer-scale-band! layer geometry band))
    (doseq [band (wind-speed-scale-bands geometry)]
      (draw-layer-wind-speed-scale-label! layer geometry band))
    (draw-layer-wind-speed-scale-title! layer geometry)))

(defn ceiling-scale-geometry [width height]
  (let [scale-height (/ height 3.0)
        strip-width (max 8.0 (* 0.02 (min width height)))]
    {:x 0.0
     :y (/ height 3.0)
     :width strip-width
     :height scale-height
     :screen-width width
     :screen-height height
     :title-x 0.0
     :label-x (+ strip-width 6.0)}))

(defn ceiling-scale-y [{:keys [y height]} ceiling-ft-agl]
  (+ y (* height (- 1.0 (/ ceiling-ft-agl ceiling-overlay-max-ft)))))

(defn ceiling-scale-bands [{:keys [y height]}]
  (let [ceilings (range 0 (inc ceiling-overlay-max-ft) 500)]
    (mapv (fn [[low high]]
            {:low low
             :high high
             :top (+ y (* height (- 1.0 (/ high ceiling-overlay-max-ft))))
             :bottom (+ y (* height (- 1.0 (/ low ceiling-overlay-max-ft))))
             :color (ceiling-scale-color low)})
          (partition 2 1 ceilings))))

(defn ceiling-scale-labels [geometry]
  (mapv (fn [[label ceiling]]
          {:label label
           :y (ceiling-scale-y geometry ceiling)})
        (concat [["<500" 250]]
                (map (fn [ceiling]
                       [(str (/ ceiling 1000) "K") ceiling])
                     (range 1000 (inc ceiling-overlay-max-ft) 1000)))))

(defn ceiling-scale-label-font-size [width height]
  (* 0.9 (wind-speed-scale-label-font-size width height)))

(defn ceiling-scale-label-y-offset [width height]
  (* 0.45 (ceiling-scale-label-font-size width height)))

(defn ceiling-scale-title-y-offset [width height]
  (* 0.8 (scale-title-font-size width height)))

(defn- draw-layer-ceiling-scale-label! [layer geometry {:keys [label y]}]
  (.fill layer 255 255 255)
  (layer-text-font! layer (map-label-font))
  (.textAlign layer processing.core.PConstants/LEFT processing.core.PConstants/CENTER)
  (.textSize layer (ceiling-scale-label-font-size (:screen-width geometry) (:screen-height geometry)))
  (.text layer
         (str label)
         (float (:label-x geometry))
         (float (+ y (ceiling-scale-label-y-offset (:screen-width geometry) (:screen-height geometry))))))

(defn- draw-layer-ceiling-scale-title! [layer geometry]
  (.fill layer 255 255 255)
  (layer-text-font! layer (map-label-font))
  (.textAlign layer processing.core.PConstants/LEFT processing.core.PConstants/BOTTOM)
  (.textSize layer (scale-title-font-size (:screen-width geometry) (:screen-height geometry)))
  (.text layer
         "ceil"
         (float (:title-x geometry))
         (float (- (:y geometry)
                   (ceiling-scale-title-y-offset (:screen-width geometry) (:screen-height geometry))))))

(defn- draw-layer-ceiling-scale! [layer width height]
  (let [geometry (ceiling-scale-geometry width height)]
    (.noStroke layer)
    (doseq [band (ceiling-scale-bands geometry)]
      (draw-layer-scale-band! layer geometry band))
    (doseq [label (ceiling-scale-labels geometry)]
      (draw-layer-ceiling-scale-label! layer geometry label))
    (draw-layer-ceiling-scale-title! layer geometry)))

(defn stale-wind-data? [now {:keys [source generated-at-ms]}]
  (or (= :synthetic source)
      (nil? generated-at-ms)
      (> (- now generated-at-ms) stale-wind-data-ms)))

(defn stale-wind-data-warning-geometry [width height]
  (let [margin (metar-margin width height)
        source-clear-y (- height (source-label-font-size width height) 6)
        metar-clear-y (- (:y (split-flap-metar-geometry width height (:line (current-airport-metar-label)))) 6)
        y (min source-clear-y metar-clear-y)
        available-height (max 6 (- height y margin))
        font-size (min 16 (max 6 (int available-height)))]
    {:x (- width margin)
     :y y
     :font-size font-size}))

(defn draw-stale-wind-data-warning! [now grid width height]
  (when (stale-wind-data? now grid)
    (let [{:keys [x y font-size]} (stale-wind-data-warning-geometry width height)]
      (q/fill 255 60 60)
      (q-text-font! (map-label-font))
      (q/text-align :right :bottom)
      (q/text-size font-size)
      (q/text stale-wind-data-message x y))))

(defn- static-map-layer-stale? [cached-key layer-key size-match?]
  (not (and size-match? (= cached-key layer-key))))

(defn- render-static-map-layer! [layer bounds width height grid markers]
  (q/with-graphics layer
    (q/background 10 15 22)
    (draw-state-outlines! bounds width height)
    (draw-layer-ceiling-overlay! layer bounds width height markers)
    (draw-layer-valid-range-circle! layer bounds width height grid)
    (doseq [marker markers]
      (draw-flight-category-airport! bounds width height marker))
    (draw-layer-ceiling-scale! layer width height)
    (draw-layer-wind-speed-scale! layer width height)
    (draw-source-label! grid width height)
    (draw-layer-current-airport-metar! layer width height)))

(defn static-map-layer [bounds width height grid markers]
  (let [layer-key (static-map-layer-key bounds width height grid markers)
        {cached-key :key} @static-map-layer-cache
        {:keys [layer size-match?]} (current-static-map-layer width height)]
    (when (static-map-layer-stale? cached-key layer-key size-match?)
      (render-static-map-layer! layer bounds width height grid markers))
    (reset! static-map-layer-cache {:key layer-key :layer layer})
    layer))

(defn refresh-static-map-layer-on-screen-entry! []
  (when @atoms/screen-changed?
    (reset! static-map-layer-cache {:key nil :layer nil})))

(defn draw-wind-map! []
  (let [grid (wind-data/current-grid)
        width (q/width)
        height (q/height)
        base-bounds (wind-data/radius-bounds (:center grid) (:radius-nm grid))
        bounds (fit-bounds-to-screen base-bounds width height)
        now (System/currentTimeMillis)
        _ (refresh-static-map-layer-on-screen-entry!)
        markers (cached-flight-category-airport-markers now)
        layer (static-map-layer bounds width height grid markers)
        _ (ensure-particles! bounds grid width height now)
        frame (particle-frame bounds grid width height)
        updated (mapv #(step-particle-with-frame frame now %) @particles)]
    (reset! particles updated)
    (q/image layer 0 0)
    (draw-stale-wind-data-warning! now grid width height)
    (draw-particles! updated)))

(defmethod screen/draw-body :wind-map [_ _state]
  (draw-wind-map!)
  true)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-09T09:19:47.341019-05:00", :module-hash "1684827110", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 12, :hash "674352644"} {:id "def/state-outlines-cache", :kind "def", :line 14, :end-line 14, :hash "-350688288"} {:id "def/static-map-layer-cache", :kind "def", :line 15, :end-line 15, :hash "620140335"} {:id "def/airport-marker-cache", :kind "def", :line 16, :end-line 16, :hash "1646684284"} {:id "def/airport-marker-cache-ms", :kind "def", :line 17, :end-line 17, :hash "-1945388248"} {:id "def/stale-wind-data-ms", :kind "def", :line 18, :end-line 18, :hash "218651392"} {:id "def/stale-wind-data-message", :kind "def", :line 19, :end-line 19, :hash "-1316693162"} {:id "def/ceiling-overlay-cols", :kind "def", :line 20, :end-line 20, :hash "368365305"} {:id "def/ceiling-overlay-rows", :kind "def", :line 21, :end-line 21, :hash "-2132883557"} {:id "def/ceiling-overlay-max-ft", :kind "def", :line 22, :end-line 22, :hash "-1107534433"} {:id "def/ceiling-overlay-cell-alpha", :kind "def", :line 23, :end-line 23, :hash "-1428840015"} {:id "def/particles", :kind "def", :line 25, :end-line 25, :hash "155048396"} {:id "def/particle-field-size", :kind "def", :line 26, :end-line 26, :hash "818318783"} {:id "def/wind-field-cache", :kind "def", :line 27, :end-line 27, :hash "-320017671"} {:id "def/animation-seconds-per-frame", :kind "def", :line 28, :end-line 28, :hash "1555334293"} {:id "def/wind-field-cols", :kind "def", :line 29, :end-line 29, :hash "-2105679199"} {:id "def/wind-field-rows", :kind "def", :line 30, :end-line 30, :hash "-1102853663"} {:id "def/particle-segment-min-length", :kind "def", :line 31, :end-line 31, :hash "588459670"} {:id "def/particle-segment-max-length", :kind "def", :line 32, :end-line 32, :hash "910308292"} {:id "def/particle-segment-pixels-per-knot", :kind "def", :line 33, :end-line 33, :hash "-1831478383"} {:id "def/particle-segment-screen-scale", :kind "def", :line 34, :end-line 34, :hash "233315046"} {:id "def/particle-motion-screen-scale", :kind "def", :line 35, :end-line 35, :hash "923541879"} {:id "def/particle-fade-in-ms", :kind "def", :line 36, :end-line 36, :hash "-644840462"} {:id "def/particle-fade-out-ms", :kind "def", :line 37, :end-line 37, :hash "722293244"} {:id "def/particle-min-life-ms", :kind "def", :line 38, :end-line 38, :hash "-1530051359"} {:id "def/particle-max-life-ms", :kind "def", :line 39, :end-line 39, :hash "890007799"} {:id "def/particle-coordinate", :kind "def", :line 40, :end-line 40, :hash "-2059248895"} {:id "def/fit-bounds-to-screen", :kind "def", :line 41, :end-line 41, :hash "-2116898316"} {:id "def/project-point", :kind "def", :line 42, :end-line 42, :hash "-2085532950"} {:id "def/unproject-point", :kind "def", :line 43, :end-line 43, :hash "-1727549963"} {:id "def/nearest-wind", :kind "def", :line 44, :end-line 44, :hash "-1892100238"} {:id "def/interpolated-wind", :kind "def", :line 45, :end-line 45, :hash "-1309347192"} {:id "def/wind-speed", :kind "def", :line 46, :end-line 46, :hash "2078800266"} {:id "def/particle-opacity", :kind "def", :line 47, :end-line 47, :hash "-123347195"} {:id "def/particle-dead?", :kind "def", :line 48, :end-line 48, :hash "-1516087527"} {:id "def/random-particle", :kind "def", :line 49, :end-line 49, :hash "-1857084068"} {:id "def/initial-particle", :kind "def", :line 50, :end-line 50, :hash "-1627751345"} {:id "def/make-particles", :kind "def", :line 51, :end-line 51, :hash "1951808604"} {:id "def/ensure-particles!", :kind "def", :line 52, :end-line 52, :hash "1714997095"} {:id "def/wind-field-key", :kind "def", :line 53, :end-line 53, :hash "-1881373709"} {:id "def/make-wind-field", :kind "def", :line 54, :end-line 54, :hash "774304138"} {:id "def/current-wind-field", :kind "def", :line 55, :end-line 55, :hash "-1861891418"} {:id "def/sample-wind-field", :kind "def", :line 56, :end-line 56, :hash "1655759902"} {:id "def/particle-frame", :kind "def", :line 57, :end-line 57, :hash "-2031095686"} {:id "def/wind-color", :kind "def", :line 58, :end-line 58, :hash "2035026331"} {:id "def/step-particle-with-frame", :kind "def", :line 59, :end-line 59, :hash "-646589692"} {:id "def/step-particle", :kind "def", :line 60, :end-line 60, :hash "279771128"} {:id "def/particle-segment-length", :kind "def", :line 61, :end-line 61, :hash "-1573784694"} {:id "def/particle-segment-end", :kind "def", :line 62, :end-line 62, :hash "1832291123"} {:id "def/draw-particle!", :kind "def", :line 63, :end-line 63, :hash "-1522966060"} {:id "def/draw-particles!", :kind "def", :line 64, :end-line 64, :hash "791491014"} {:id "def/state-labels", :kind "def", :line 66, :end-line 73, :hash "410376313"} {:id "def/nearby-state-names", :kind "def", :line 75, :end-line 75, :hash "-153375614"} {:id "defn-/lon-lat->lat-lon", :kind "defn-", :line 77, :end-line 78, :hash "-34246038"} {:id "defn-/polygon-rings", :kind "defn-", :line 80, :end-line 84, :hash "1249934570"} {:id "defn-/outline-bounds", :kind "defn-", :line 86, :end-line 93, :hash "-613673615"} {:id "defn-/feature->outline", :kind "defn-", :line 95, :end-line 100, :hash "2070406857"} {:id "defn/load-state-outlines", :kind "defn", :line 102, :end-line 106, :hash "-1405659647"} {:id "defn/state-outlines", :kind "defn", :line 108, :end-line 110, :hash "-740183215"} {:id "defn/make-wind-map-screen", :kind "defn", :line 112, :end-line 114, :hash "-1916581111"} {:id "defmethod/screen/make/:wind-map", :kind "defmethod", :line 116, :end-line 117, :hash "1602119916"} {:id "defmethod/screen/header-text/:wind-map", :kind "defmethod", :line 119, :end-line 120, :hash "-829791532"} {:id "defmethod/screen/display-column-headers/:wind-map", :kind "defmethod", :line 122, :end-line 123, :hash "-723555036"} {:id "defn-/map-label-font", :kind "defn-", :line 125, :end-line 127, :hash "1319153087"} {:id "defn-/airport-label-font", :kind "defn-", :line 129, :end-line 131, :hash "-2024494817"} {:id "defn-/metar-label-font", :kind "defn-", :line 133, :end-line 136, :hash "1166997130"} {:id "def/flight-category-colors", :kind "def", :line 138, :end-line 142, :hash "1208642753"} {:id "defn/flight-category-color", :kind "defn", :line 144, :end-line 145, :hash "-1340670323"} {:id "def/color-rgb-values", :kind "def", :line 147, :end-line 153, :hash "-980395204"} {:id "def/default-color-rgb", :kind "def", :line 155, :end-line 155, :hash "-181122293"} {:id "defn/color-rgb", :kind "defn", :line 157, :end-line 158, :hash "-1165963766"} {:id "def/ceiling-covers", :kind "def", :line 160, :end-line 160, :hash "427683661"} {:id "defn/metar-ceiling-ft-agl", :kind "defn", :line 162, :end-line 169, :hash "-1242755510"} {:id "defn/draw-airport!", :kind "defn", :line 171, :end-line 180, :hash "987622579"} {:id "defn/flight-category-airport-markers", :kind "defn", :line 182, :end-line 208, :hash "736452144"} {:id "defn/cached-flight-category-airport-markers", :kind "defn", :line 210, :end-line 217, :hash "-1852679089"} {:id "defn/label-airport?", :kind "defn", :line 219, :end-line 220, :hash "919873300"} {:id "defn/draw-flight-category-airport!", :kind "defn", :line 222, :end-line 236, :hash "-1344100387"} {:id "defn/draw-flight-category-airports!", :kind "defn", :line 238, :end-line 240, :hash "996898366"} {:id "defn/marker-layer-key", :kind "defn", :line 242, :end-line 243, :hash "-1044939270"} {:id "defn/current-airport-metar-label", :kind "defn", :line 245, :end-line 246, :hash "1233621732"} {:id "defn/static-map-layer-key", :kind "defn", :line 248, :end-line 256, :hash "-942071419"} {:id "defn-/current-static-map-layer", :kind "defn-", :line 258, :end-line 264, :hash "630663816"} {:id "defn-/bounds-intersect?", :kind "defn-", :line 266, :end-line 270, :hash "-544654738"} {:id "defn-/ring-center", :kind "defn-", :line 272, :end-line 275, :hash "-1771243540"} {:id "defn/draw-state-outline!", :kind "defn", :line 277, :end-line 295, :hash "-346684475"} {:id "defn/draw-state-outlines!", :kind "defn", :line 297, :end-line 299, :hash "1141578969"} {:id "defn-/layer-text-font!", :kind "defn-", :line 301, :end-line 303, :hash "-1469010749"} {:id "defn-/draw-layer-flight-category-airport!", :kind "defn-", :line 305, :end-line 318, :hash "-681805799"} {:id "defn-/draw-layer-flight-category-airports!", :kind "defn-", :line 320, :end-line 322, :hash "-1911938762"} {:id "defn-/draw-layer-state-outline!", :kind "defn-", :line 324, :end-line 341, :hash "-1316286081"} {:id "defn-/draw-layer-state-outlines!", :kind "defn-", :line 343, :end-line 345, :hash "-104854079"} {:id "form/92/declare", :kind "declare", :line 347, :end-line 347, :hash "744337360"} {:id "def/range-circle-point-count", :kind "def", :line 349, :end-line 349, :hash "-1578680257"} {:id "defn/range-circle-lat-lon", :kind "defn", :line 351, :end-line 356, :hash "941633076"} {:id "defn/range-circle-points", :kind "defn", :line 358, :end-line 363, :hash "-1495990957"} {:id "defn/range-circle-label-text", :kind "defn", :line 365, :end-line 366, :hash "-538750997"} {:id "defn/range-circle-label-font-size", :kind "defn", :line 368, :end-line 369, :hash "911866306"} {:id "defn/range-circle-label-offset", :kind "defn", :line 371, :end-line 372, :hash "1693819112"} {:id "defn-/draw-layer-valid-range-circle!", :kind "defn-", :line 374, :end-line 390, :hash "-823801910"} {:id "defn/ceiling-observations", :kind "defn", :line 392, :end-line 395, :hash "1614300486"} {:id "defn-/ceiling-distance", :kind "defn-", :line 397, :end-line 398, :hash "-232481315"} {:id "defn-/weighted-ceiling", :kind "defn-", :line 400, :end-line 404, :hash "-645407851"} {:id "defn/interpolated-ceiling-ft-agl", :kind "defn", :line 406, :end-line 411, :hash "1903791618"} {:id "defn/ceiling-band-upper-ft", :kind "defn", :line 413, :end-line 414, :hash "-1884758254"} {:id "def/ceiling-color-stops", :kind "def", :line 416, :end-line 422, :hash "-1400591104"} {:id "defn-/interpolate-channel", :kind "defn-", :line 424, :end-line 425, :hash "-179475162"} {:id "defn-/color-between-stops", :kind "defn-", :line 427, :end-line 433, :hash "-2017321468"} {:id "defn/ceiling-overlay-color", :kind "defn", :line 435, :end-line 441, :hash "-130333900"} {:id "defn/ceiling-scale-color", :kind "defn", :line 443, :end-line 445, :hash "35108114"} {:id "defn/ceiling-overlay-cell-color", :kind "defn", :line 447, :end-line 452, :hash "671851820"} {:id "defn-/draw-layer-ceiling-cell!", :kind "defn-", :line 454, :end-line 459, :hash "468826253"} {:id "defn-/draw-layer-ceiling-overlay!", :kind "defn-", :line 461, :end-line 469, :hash "1857341780"} {:id "defn/source-label-text", :kind "defn", :line 471, :end-line 482, :hash "449883718"} {:id "form/114/declare", :kind "declare", :line 484, :end-line 484, :hash "290379583"} {:id "defn/source-label-font-size", :kind "defn", :line 486, :end-line 487, :hash "-622574042"} {:id "defn/source-label-y", :kind "defn", :line 489, :end-line 490, :hash "-1905692084"} {:id "defn-/draw-layer-source-label!", :kind "defn-", :line 492, :end-line 500, :hash "-1326633038"} {:id "defn/draw-source-label!", :kind "defn", :line 502, :end-line 511, :hash "-233005773"} {:id "defn/metar-split-flap-metrics", :kind "defn", :line 513, :end-line 526, :hash "918739642"} {:id "defn/metar-margin", :kind "defn", :line 528, :end-line 529, :hash "-592411333"} {:id "defn/metar-max-chars", :kind "defn", :line 531, :end-line 532, :hash "-481774086"} {:id "defn/truncate-metar-line", :kind "defn", :line 534, :end-line 536, :hash "1866658861"} {:id "defn/split-flap-metar-geometry", :kind "defn", :line 538, :end-line 552, :hash "977690701"} {:id "defn-/draw-layer-split-flap-line!", :kind "defn-", :line 554, :end-line 573, :hash "1889674698"} {:id "defn-/draw-split-flap-line!", :kind "defn-", :line 575, :end-line 594, :hash "1743401762"} {:id "defn-/draw-layer-current-airport-metar!", :kind "defn-", :line 596, :end-line 602, :hash "927688885"} {:id "defn/draw-current-airport-metar!", :kind "defn", :line 604, :end-line 610, :hash "994915483"} {:id "def/wind-speed-scale-max", :kind "def", :line 612, :end-line 612, :hash "218498176"} {:id "defn/wind-speed-scale-geometry", :kind "defn", :line 614, :end-line 624, :hash "-1309337438"} {:id "defn/wind-speed-scale-y", :kind "defn", :line 626, :end-line 627, :hash "-2092454484"} {:id "defn/wind-speed-scale-bands", :kind "defn", :line 629, :end-line 637, :hash "1205873577"} {:id "defn/wind-speed-scale-band-label", :kind "defn", :line 639, :end-line 642, :hash "1240148747"} {:id "defn/wind-speed-scale-label-font-size", :kind "defn", :line 644, :end-line 645, :hash "2103804374"} {:id "defn-/draw-layer-scale-band!", :kind "defn-", :line 647, :end-line 650, :hash "-1865538603"} {:id "defn-/draw-layer-wind-speed-scale-label!", :kind "defn-", :line 652, :end-line 660, :hash "927736510"} {:id "defn/scale-title-font-size", :kind "defn", :line 662, :end-line 663, :hash "1388440132"} {:id "defn-/draw-layer-wind-speed-scale-title!", :kind "defn-", :line 665, :end-line 670, :hash "614846177"} {:id "defn-/draw-layer-wind-speed-scale!", :kind "defn-", :line 672, :end-line 679, :hash "-725571398"} {:id "defn/ceiling-scale-geometry", :kind "defn", :line 681, :end-line 691, :hash "-992914363"} {:id "defn/ceiling-scale-y", :kind "defn", :line 693, :end-line 694, :hash "-189295320"} {:id "defn/ceiling-scale-bands", :kind "defn", :line 696, :end-line 704, :hash "2059161370"} {:id "defn/ceiling-scale-labels", :kind "defn", :line 706, :end-line 713, :hash "1682446522"} {:id "defn/ceiling-scale-label-font-size", :kind "defn", :line 715, :end-line 716, :hash "1173181736"} {:id "defn/ceiling-scale-label-y-offset", :kind "defn", :line 718, :end-line 719, :hash "437750364"} {:id "defn/ceiling-scale-title-y-offset", :kind "defn", :line 721, :end-line 722, :hash "262868395"} {:id "defn-/draw-layer-ceiling-scale-label!", :kind "defn-", :line 724, :end-line 732, :hash "1196054488"} {:id "defn-/draw-layer-ceiling-scale-title!", :kind "defn-", :line 734, :end-line 743, :hash "-507054251"} {:id "defn-/draw-layer-ceiling-scale!", :kind "defn-", :line 745, :end-line 752, :hash "-1451951572"} {:id "defn/stale-wind-data?", :kind "defn", :line 754, :end-line 757, :hash "-1109183279"} {:id "defn/stale-wind-data-warning-geometry", :kind "defn", :line 759, :end-line 768, :hash "1723509000"} {:id "defn/draw-stale-wind-data-warning!", :kind "defn", :line 770, :end-line 778, :hash "943402360"} {:id "defn-/render-static-map-layer!", :kind "defn-", :line 780, :end-line 791, :hash "365465381"} {:id "defn/static-map-layer", :kind "defn", :line 793, :end-line 800, :hash "-1697418385"} {:id "defn/refresh-static-map-layer-on-screen-entry!", :kind "defn", :line 802, :end-line 804, :hash "340398746"} {:id "defn/draw-wind-map!", :kind "defn", :line 806, :end-line 822, :hash "-812032652"} {:id "defmethod/screen/draw-body/:wind-map", :kind "defmethod", :line 824, :end-line 826, :hash "965218688"}]}
;; clj-mutate-manifest-end
