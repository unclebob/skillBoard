(ns skillBoard.presenters.wind-map.overlays
  (:require
    [quil.core :as q]
    [skillBoard.config :as config]
    [skillBoard.core-utils :as core-utils]
    [skillBoard.presenters.utils :as utils]
    [skillBoard.presenters.wind-map.draw :as draw]
    [skillBoard.presenters.wind-map.geo :as geo]
    [skillBoard.wind-data :as wind-data]))

(def stale-wind-data-ms (* 2 60 60 1000))
(def stale-wind-data-message "WIND DATA IS OUT OF DATE")
(def range-circle-point-count 72)

(defn current-airport-metar-label []
  (utils/get-short-metar config/airport))

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
  (draw/source-label-font-size width height))

(defn range-circle-label-offset [width height]
  (* 2 (range-circle-label-font-size width height)))

(defn draw-layer-valid-range-circle! [layer bounds width height {:keys [center radius-nm] :as grid}]
  (when-let [points (range-circle-points grid)]
    (.noFill layer)
    (.stroke layer 255 255 255 210)
    (.strokeWeight layer 1)
    (.beginShape layer)
    (doseq [[lat lon] points
            :let [[x y] (geo/project-point bounds width height lat lon)]]
      (.vertex layer (float x) (float y)))
    (.endShape layer)
    (let [[label-lat label-lon] (range-circle-lat-lon center radius-nm 0)
          [x y] (geo/project-point bounds width height label-lat label-lon)]
      (draw/layer-text-font! layer (draw/map-label-font))
      (.fill layer 255 255 255)
      (.textAlign layer processing.core.PConstants/CENTER processing.core.PConstants/TOP)
      (.textSize layer (range-circle-label-font-size width height))
      (.text layer (range-circle-label-text radius-nm) (float x) (float (+ y (range-circle-label-offset width height)))))))

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

(defn source-label-y [width height]
  height)

(defn draw-layer-source-label! [layer grid width height]
  (.fill layer 255 255 255)
  (draw/layer-text-font! layer (draw/map-label-font))
  (.textAlign layer processing.core.PConstants/LEFT processing.core.PConstants/BOTTOM)
  (.textSize layer (draw/source-label-font-size width height))
  (.text layer
         (source-label-text grid)
         (float 20)
         (float (source-label-y width height))))

(defn draw-source-label! [grid width height]
  (try
    (q/fill 255 255 255)
    (draw/q-text-font! (draw/map-label-font))
    (q/text-align :left :bottom)
    (q/text-size (draw/source-label-font-size width height))
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
  (let [[r g b] (draw/color-rgb color)]
    (.noStroke layer)
    (draw/layer-text-font! layer (:sf-font @config/display-info))
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
  (let [[r g b] (draw/color-rgb color)]
    (q/no-stroke)
    (draw/q-text-font! (:sf-font @config/display-info))
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

(defn draw-layer-current-airport-metar! [layer width height]
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

(defn stale-wind-data? [now {:keys [source generated-at-ms]}]
  (or (= :synthetic source)
      (nil? generated-at-ms)
      (> (- now generated-at-ms) stale-wind-data-ms)))

(defn stale-wind-data-warning-geometry [width height]
  (let [margin (metar-margin width height)
        source-clear-y (- height (draw/source-label-font-size width height) 6)
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
      (draw/q-text-font! (draw/map-label-font))
      (q/text-align :right :bottom)
      (q/text-size font-size)
      (q/text stale-wind-data-message x y))))

(def ceiling-overlay-cols 36)
(def ceiling-overlay-rows 24)

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

(defn ceiling-overlay-cell-color [bounds width height observations cell-width cell-height col row]
  (let [x (* col cell-width)
        y (* row cell-height)
        [lat lon] (geo/unproject-point bounds width height (+ x (/ cell-width 2.0)) (+ y (/ cell-height 2.0)))
        ceiling (interpolated-ceiling-ft-agl observations lat lon)]
    (draw/ceiling-overlay-color ceiling)))

(defn draw-layer-ceiling-cell! [layer bounds width height observations cell-width cell-height col row]
  (when-let [[r g b a] (ceiling-overlay-cell-color bounds width height observations cell-width cell-height col row)]
    (let [x (* col cell-width)
          y (* row cell-height)]
      (.fill layer r g b a)
      (.rect layer (float x) (float y) (float cell-width) (float cell-height)))))

(defn draw-layer-ceiling-overlay! [layer bounds width height markers]
  (let [observations (ceiling-observations markers)
        cell-width (/ width ceiling-overlay-cols)
        cell-height (/ height ceiling-overlay-rows)]
    (when (seq observations)
      (.noStroke layer)
      (doseq [col (range ceiling-overlay-cols)
              row (range ceiling-overlay-rows)]
        (draw-layer-ceiling-cell! layer bounds width height observations cell-width cell-height col row)))))

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
             :color (draw/wind-color low)})
          (partition 2 1 speeds))))

(defn wind-speed-scale-band-label [{:keys [low high]}]
  (if (= high wind-speed-scale-max)
    (str low "+")
    (str low "-" high)))

(defn wind-speed-scale-label-font-size [width height]
  (* 0.88 (draw/source-label-font-size width height)))

(defn- draw-layer-scale-band! [layer {:keys [x width]} {:keys [top bottom color]}]
  (let [[r g b a] color]
    (.fill layer r g b a)
    (.rect layer (float x) (float top) (float width) (float (- bottom top)))))

(defn- draw-layer-wind-speed-scale-label! [layer geometry {:keys [top bottom] :as band}]
  (.fill layer 255 255 255)
  (draw/layer-text-font! layer (draw/map-label-font))
  (.textAlign layer processing.core.PConstants/RIGHT processing.core.PConstants/CENTER)
  (.textSize layer (wind-speed-scale-label-font-size (:screen-width geometry) (:screen-height geometry)))
  (.text layer
         (wind-speed-scale-band-label band)
         (float (:label-x geometry))
         (float (/ (+ top bottom) 2.0))))

(defn- draw-layer-wind-speed-scale-title! [layer geometry]
  (.fill layer 255 255 255)
  (draw/layer-text-font! layer (draw/map-label-font))
  (.textAlign layer processing.core.PConstants/RIGHT processing.core.PConstants/BOTTOM)
  (.textSize layer (draw/scale-title-font-size (:screen-width geometry) (:screen-height geometry)))
  (.text layer "wind" (float (:title-x geometry)) (float (- (:y geometry) 4.0))))

(defn draw-layer-wind-speed-scale! [layer width height]
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
  (+ y (* height (- 1.0 (/ ceiling-ft-agl draw/ceiling-overlay-max-ft)))))

(defn ceiling-scale-bands [{:keys [y height]}]
  (let [ceilings (range 0 (inc draw/ceiling-overlay-max-ft) 500)]
    (mapv (fn [[low high]]
            {:low low
             :high high
             :top (+ y (* height (- 1.0 (/ high draw/ceiling-overlay-max-ft))))
             :bottom (+ y (* height (- 1.0 (/ low draw/ceiling-overlay-max-ft))))
             :color (draw/ceiling-scale-color low)})
          (partition 2 1 ceilings))))

(defn ceiling-scale-labels [geometry]
  (mapv (fn [[label ceiling]]
          {:label label
           :y (ceiling-scale-y geometry ceiling)})
        (concat [["<500" 250]]
                (map (fn [ceiling]
                       [(str (/ ceiling 1000) "K") ceiling])
                     (range 1000 (inc draw/ceiling-overlay-max-ft) 1000)))))

(defn ceiling-scale-label-font-size [width height]
  (* 0.9 (wind-speed-scale-label-font-size width height)))

(defn ceiling-scale-label-y-offset [width height]
  (* 0.45 (ceiling-scale-label-font-size width height)))

(defn ceiling-scale-title-y-offset [width height]
  (* 0.8 (draw/scale-title-font-size width height)))

(defn- draw-layer-ceiling-scale-label! [layer geometry {:keys [label y]}]
  (.fill layer 255 255 255)
  (draw/layer-text-font! layer (draw/map-label-font))
  (.textAlign layer processing.core.PConstants/LEFT processing.core.PConstants/CENTER)
  (.textSize layer (ceiling-scale-label-font-size (:screen-width geometry) (:screen-height geometry)))
  (.text layer
         (str label)
         (float (:label-x geometry))
         (float (+ y (ceiling-scale-label-y-offset (:screen-width geometry) (:screen-height geometry))))))

(defn- draw-layer-ceiling-scale-title! [layer geometry]
  (.fill layer 255 255 255)
  (draw/layer-text-font! layer (draw/map-label-font))
  (.textAlign layer processing.core.PConstants/LEFT processing.core.PConstants/BOTTOM)
  (.textSize layer (draw/scale-title-font-size (:screen-width geometry) (:screen-height geometry)))
  (.text layer
         "ceil"
         (float (:title-x geometry))
         (float (- (:y geometry)
                   (ceiling-scale-title-y-offset (:screen-width geometry) (:screen-height geometry))))))

(defn draw-layer-ceiling-scale! [layer width height]
  (let [geometry (ceiling-scale-geometry width height)]
    (.noStroke layer)
    (doseq [band (ceiling-scale-bands geometry)]
      (draw-layer-scale-band! layer geometry band))
    (doseq [label (ceiling-scale-labels geometry)]
      (draw-layer-ceiling-scale-label! layer geometry label))
    (draw-layer-ceiling-scale-title! layer geometry)))
