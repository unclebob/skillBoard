(ns skillBoard.presenters.wind-map.aircraft
  (:require
    [quil.core :as q]
    [skillBoard.foundation.config :as config]
    [skillBoard.presenters.wind-map.geo :as geo]))

(def triangle-length 16.0)
(def triangle-half-width 6.0)
(def minimum-agl 500)
(def airport-exclusion-nm 3)
(def data-block-font-size 11)

(defn- shown [n]
  (if (number? n)
    (str (Math/round (double n)))
    "---"))

(defn- altitude [{:keys [altg alt]}]
  (or altg alt))

(defn data-block [{:keys [reg trk spd] :as aircraft}]
  (str (or reg "----")
       " "
       (shown (altitude aircraft))
       " "
       (shown trk)
       " "
       (shown spd)))

(defn- screen-offset [track-deg ahead side]
  (let [rad (Math/toRadians track-deg)
        east (+ (* ahead (Math/sin rad)) (* side (Math/cos rad)))
        north (+ (* ahead (Math/cos rad)) (* side (- (Math/sin rad))))]
    [east north]))

(defn triangle-vertices [x y track-deg]
  (let [track (or track-deg 0.0)
        [nose-e nose-n] (screen-offset track triangle-length 0.0)
        [left-e left-n] (screen-offset track (- triangle-half-width) (- triangle-half-width))
        [right-e right-n] (screen-offset track (- triangle-half-width) triangle-half-width)]
    [[(+ x nose-e) (- y nose-n)]
     [(+ x left-e) (- y left-n)]
     [(+ x right-e) (- y right-n)]]))

(def test-fleet #{"N345TS" "N378MA"})

(def test-adsbs
  {"N345TS" {:reg "N345TS" :lat 42.5960633 :lon -87.9273236 :alt 3000 :spd 100 :trk 315}
   "N378MA" {:reg "N378MA" :lat 42.4221486 :lon -87.8679161 :alt 2000 :trk 315}})

(defn adsbs-for-snapshot [snapshot]
  (if (:test? snapshot)
    test-adsbs
    (:adsbs snapshot)))

(defn- records [adsbs]
  (cond
    (map? adsbs) (vals adsbs)
    (sequential? adsbs) adsbs
    :else []))

(defn fleet-adsbs [snapshot]
  (let [fleet (if (:test? snapshot)
                test-fleet
                (set (:aircraft snapshot)))]
    (filter #(contains? fleet (:reg %))
            (records (adsbs-for-snapshot snapshot)))))

(defn above-minimum-agl? [aircraft]
  (let [msl (altitude aircraft)]
    (and (number? msl)
         (> (- msl config/airport-elevation) minimum-agl))))

(defn outside-airport? [{:keys [lat lon]}]
  (let [[airport-lat airport-lon] config/airport-lat-lon]
    (> (geo/nm-distance [airport-lat airport-lon] [lat lon])
       airport-exclusion-nm)))

(defn positioned-aircraft [adsbs]
  (filter (fn [{:keys [lat lon] :as aircraft}]
            (and (number? lat)
                 (number? lon)
                 (above-minimum-agl? aircraft)
                 (outside-airport? aircraft)))
          (records adsbs)))

(defn- draw-aircraft! [bounds width height aircraft]
  (let [[x y] (geo/project-point bounds width height (:lat aircraft) (:lon aircraft))
        [[x1 y1] [x2 y2] [x3 y3]] (triangle-vertices x y (:trk aircraft))
        label-x (+ x triangle-length)]
    (q/triangle x1 y1 x2 y2 x3 y3)
    (q/text (data-block aircraft) label-x y)))

(defn draw-fleet! [bounds width height adsbs]
  (when-let [fleet (seq (positioned-aircraft adsbs))]
    (q/fill 255)
    (q/no-stroke)
    (q/text-font (q/create-font "Helvetica" data-block-font-size))
    (q/text-align :left :center)
    (q/text-size data-block-font-size)
    (doseq [aircraft fleet]
      (draw-aircraft! bounds width height aircraft))))
