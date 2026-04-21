(ns skillBoard.presenters.wind-map
  (:require
    [clojure.data.json :as json]
    [quil.core :as q]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.presenters.wind-map.particles :as wind-particles]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.wind-data :as wind-data]))

(def state-outlines-cache (atom nil))
(def static-map-layer-cache (atom {:key nil :layer nil}))

(def particles wind-particles/particles)
(def particle-field-size wind-particles/particle-field-size)
(def animation-seconds-per-frame wind-particles/animation-seconds-per-frame)
(def particle-segment-min-length wind-particles/particle-segment-min-length)
(def particle-segment-max-length wind-particles/particle-segment-max-length)
(def particle-segment-pixels-per-knot wind-particles/particle-segment-pixels-per-knot)
(def particle-fade-in-ms wind-particles/particle-fade-in-ms)
(def particle-fade-out-ms wind-particles/particle-fade-out-ms)
(def particle-min-life-ms wind-particles/particle-min-life-ms)
(def particle-max-life-ms wind-particles/particle-max-life-ms)
(def particle-coordinate wind-particles/particle-coordinate)
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
(def wind-color wind-particles/wind-color)
(def step-particle wind-particles/step-particle)
(def particle-segment-length wind-particles/particle-segment-length)
(def particle-segment-end wind-particles/particle-segment-end)
(def draw-particle! wind-particles/draw-particle!)

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T15:38:31.068973-05:00", :module-hash "-1547214145", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "-413269193"} {:id "def/state-outlines-cache", :kind "def", :line 11, :end-line 11, :hash "-350688288"} {:id "def/static-map-layer-cache", :kind "def", :line 12, :end-line 12, :hash "620140335"} {:id "def/particles", :kind "def", :line 14, :end-line 14, :hash "155048396"} {:id "def/particle-field-size", :kind "def", :line 15, :end-line 15, :hash "818318783"} {:id "def/animation-seconds-per-frame", :kind "def", :line 16, :end-line 16, :hash "1555334293"} {:id "def/particle-segment-min-length", :kind "def", :line 17, :end-line 17, :hash "588459670"} {:id "def/particle-segment-max-length", :kind "def", :line 18, :end-line 18, :hash "910308292"} {:id "def/particle-segment-pixels-per-knot", :kind "def", :line 19, :end-line 19, :hash "-1831478383"} {:id "def/particle-fade-in-ms", :kind "def", :line 20, :end-line 20, :hash "-644840462"} {:id "def/particle-fade-out-ms", :kind "def", :line 21, :end-line 21, :hash "722293244"} {:id "def/particle-min-life-ms", :kind "def", :line 22, :end-line 22, :hash "-1530051359"} {:id "def/particle-max-life-ms", :kind "def", :line 23, :end-line 23, :hash "890007799"} {:id "def/particle-coordinate", :kind "def", :line 24, :end-line 24, :hash "-2059248895"} {:id "def/project-point", :kind "def", :line 25, :end-line 25, :hash "-2085532950"} {:id "def/unproject-point", :kind "def", :line 26, :end-line 26, :hash "-1727549963"} {:id "def/nearest-wind", :kind "def", :line 27, :end-line 27, :hash "-1892100238"} {:id "def/interpolated-wind", :kind "def", :line 28, :end-line 28, :hash "-1309347192"} {:id "def/wind-speed", :kind "def", :line 29, :end-line 29, :hash "2078800266"} {:id "def/particle-opacity", :kind "def", :line 30, :end-line 30, :hash "-123347195"} {:id "def/particle-dead?", :kind "def", :line 31, :end-line 31, :hash "-1516087527"} {:id "def/random-particle", :kind "def", :line 32, :end-line 32, :hash "-1857084068"} {:id "def/initial-particle", :kind "def", :line 33, :end-line 33, :hash "-1627751345"} {:id "def/make-particles", :kind "def", :line 34, :end-line 34, :hash "1951808604"} {:id "def/ensure-particles!", :kind "def", :line 35, :end-line 35, :hash "1714997095"} {:id "def/wind-color", :kind "def", :line 36, :end-line 36, :hash "2035026331"} {:id "def/step-particle", :kind "def", :line 37, :end-line 37, :hash "279771128"} {:id "def/particle-segment-length", :kind "def", :line 38, :end-line 38, :hash "-1573784694"} {:id "def/particle-segment-end", :kind "def", :line 39, :end-line 39, :hash "1832291123"} {:id "def/draw-particle!", :kind "def", :line 40, :end-line 40, :hash "-1522966060"} {:id "def/state-labels", :kind "def", :line 42, :end-line 49, :hash "410376313"} {:id "def/nearby-state-names", :kind "def", :line 51, :end-line 51, :hash "-153375614"} {:id "defn-/lon-lat->lat-lon", :kind "defn-", :line 53, :end-line 54, :hash "-34246038"} {:id "defn-/polygon-rings", :kind "defn-", :line 56, :end-line 60, :hash "1249934570"} {:id "defn-/outline-bounds", :kind "defn-", :line 62, :end-line 69, :hash "-613673615"} {:id "defn-/feature->outline", :kind "defn-", :line 71, :end-line 76, :hash "2070406857"} {:id "defn/load-state-outlines", :kind "defn", :line 78, :end-line 82, :hash "-1405659647"} {:id "defn/state-outlines", :kind "defn", :line 84, :end-line 86, :hash "-740183215"} {:id "defn/make-wind-map-screen", :kind "defn", :line 88, :end-line 90, :hash "-1916581111"} {:id "defmethod/screen/make/:wind-map", :kind "defmethod", :line 92, :end-line 93, :hash "1602119916"} {:id "defmethod/screen/header-text/:wind-map", :kind "defmethod", :line 95, :end-line 96, :hash "-829791532"} {:id "defmethod/screen/display-column-headers/:wind-map", :kind "defmethod", :line 98, :end-line 99, :hash "-723555036"} {:id "defn-/map-label-font", :kind "defn-", :line 101, :end-line 103, :hash "-1362124108"} {:id "defn-/airport-label-font", :kind "defn-", :line 105, :end-line 107, :hash "-2024494817"} {:id "def/flight-category-colors", :kind "def", :line 109, :end-line 113, :hash "1208642753"} {:id "defn/flight-category-color", :kind "defn", :line 115, :end-line 116, :hash "-1340670323"} {:id "def/color-rgb-values", :kind "def", :line 118, :end-line 124, :hash "-980395204"} {:id "def/default-color-rgb", :kind "def", :line 126, :end-line 126, :hash "-181122293"} {:id "defn/color-rgb", :kind "defn", :line 128, :end-line 129, :hash "-1165963766"} {:id "defn/draw-airport!", :kind "defn", :line 131, :end-line 140, :hash "987622579"} {:id "defn/flight-category-airport-markers", :kind "defn", :line 142, :end-line 166, :hash "-237429149"} {:id "defn/label-airport?", :kind "defn", :line 168, :end-line 169, :hash "919873300"} {:id "defn/draw-flight-category-airport!", :kind "defn", :line 171, :end-line 185, :hash "-1344100387"} {:id "defn/draw-flight-category-airports!", :kind "defn", :line 187, :end-line 189, :hash "996898366"} {:id "defn/marker-layer-key", :kind "defn", :line 191, :end-line 192, :hash "1553508954"} {:id "defn/static-map-layer-key", :kind "defn", :line 194, :end-line 200, :hash "-173616304"} {:id "defn-/current-static-map-layer", :kind "defn-", :line 202, :end-line 208, :hash "630663816"} {:id "defn-/bounds-intersect?", :kind "defn-", :line 210, :end-line 214, :hash "-544654738"} {:id "defn-/ring-center", :kind "defn-", :line 216, :end-line 219, :hash "-1771243540"} {:id "defn/draw-state-outline!", :kind "defn", :line 221, :end-line 239, :hash "-346684475"} {:id "defn/draw-state-outlines!", :kind "defn", :line 241, :end-line 243, :hash "1141578969"} {:id "defn-/layer-text-font!", :kind "defn-", :line 245, :end-line 247, :hash "-1469010749"} {:id "defn-/draw-layer-flight-category-airport!", :kind "defn-", :line 249, :end-line 262, :hash "-681805799"} {:id "defn-/draw-layer-flight-category-airports!", :kind "defn-", :line 264, :end-line 266, :hash "-1911938762"} {:id "defn-/draw-layer-state-outline!", :kind "defn-", :line 268, :end-line 285, :hash "-1316286081"} {:id "defn-/draw-layer-state-outlines!", :kind "defn-", :line 287, :end-line 289, :hash "-104854079"} {:id "defn-/draw-layer-source-label!", :kind "defn-", :line 291, :end-line 299, :hash "597317914"} {:id "defn/draw-source-label!", :kind "defn", :line 301, :end-line 307, :hash "-818530098"} {:id "defn-/render-static-map-layer!", :kind "defn-", :line 309, :end-line 314, :hash "-528502862"} {:id "defn/static-map-layer", :kind "defn", :line 316, :end-line 323, :hash "-1697418385"} {:id "defn/draw-wind-map!", :kind "defn", :line 325, :end-line 338, :hash "868089126"} {:id "defmethod/screen/draw-body/:wind-map", :kind "defmethod", :line 340, :end-line 342, :hash "965218688"}]}
;; clj-mutate-manifest-end
