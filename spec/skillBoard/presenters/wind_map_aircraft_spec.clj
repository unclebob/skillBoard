(ns skillBoard.presenters.wind-map-aircraft-spec
  (:require
    [quil.core :as q]
    [skillBoard.foundation.config :as config]
    [skillBoard.presenters.wind-map.aircraft :as aircraft]
    [skillBoard.presenters.wind-map.geo :as geo]
    [speclj.core :refer :all]))

(describe "fleet symbols"
  (it "points the triangle along the track"
    (should= [[100.0 184.0] [94.0 206.0] [106.0 206.0]]
             (aircraft/triangle-vertices 100.0 200.0 0))
    (should= [[116.0 200.0] [94.0 194.0] [94.0 206.0]]
             (aircraft/triangle-vertices 100.0 200.0 90))
    (should= (aircraft/triangle-vertices 100.0 200.0 0)
             (aircraft/triangle-vertices 100.0 200.0 nil)))

  (it "writes tail, geometric altitude, track, and ground speed"
    (should= "N419AM 5125 119 105"
             (aircraft/data-block {:reg "N419AM" :altg 5125 :alt 4000 :trk 119 :spd 105}))
    (should= "N12345 2500 --- ---"
             (aircraft/data-block {:reg "N12345" :alt 2500}))
    (should= "---- 1000 10 80"
             (aircraft/data-block {:altg 1000 :trk 10 :spd 80})))

  (it "keeps positioned aircraft more than 500 feet above the field"
    (let [field config/airport-elevation]
      (should= ["HIGH"]
               (map :reg (aircraft/positioned-aircraft
                           {"HIGH" {:reg "HIGH" :lat 42.0 :lon -87.0 :alt (+ field 501)}
                            "EXACT" {:reg "EXACT" :lat 42.0 :lon -87.0 :alt (+ field 500)}
                            "FIELD" {:reg "FIELD" :lat 42.0 :lon -87.0 :alt field}
                            "NOLON" {:reg "NOLON" :lat 42.0 :alt (+ field 1000)}})))
      (should= [] (vec (aircraft/positioned-aircraft nil)))))

  (it "hides aircraft within three nautical miles of the home airport"
    (let [[lat lon] config/airport-lat-lon
          high (+ config/airport-elevation 1000)]
      (should= ["OUT"]
               (map :reg (aircraft/positioned-aircraft
                           {"ON" {:reg "ON" :lat lat :lon lon :alt high}
                            "THREE" {:reg "THREE" :lat (+ lat (/ 3 60.0)) :lon lon :alt high}
                            "OUT" {:reg "OUT" :lat (+ lat (/ 3.1 60.0)) :lon lon :alt high}})))))

  (it "draws a white triangle and the data block beside a fleet aircraft"
    (let [triangles (atom [])
          labels (atom [])]
      (with-redefs [geo/project-point (fn [& _] [300.0 200.0])
                    q/fill (fn [& _])
                    q/no-stroke (fn [])
                    q/create-font (fn [& _])
                    q/text-font (fn [& _])
                    q/text-align (fn [& _])
                    q/text-size (fn [_])
                    q/triangle (fn [& args] (swap! triangles conj (vec args)))
                    q/text (fn [s x y] (swap! labels conj [s x y]))]
        (aircraft/draw-fleet!
          {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          600 400
          {"N419AM" {:reg "N419AM" :lat 42.5 :lon -87.8 :altg 5125 :trk 90 :spd 105}}))
      (should= [[316.0 200.0 294.0 194.0 294.0 206.0]] @triangles)
      (should= [["N419AM 5125 90 105" 316.0 200.0]] @labels)))

  (it "draws the test fleet northwest when the snapshot is a test run"
    (let [triangles (atom [])
          labels (atom [])
          nose (first (aircraft/triangle-vertices 100.0 200.0 315))]
      (let [fonts (atom [])
            sizes (atom [])]
        (with-redefs [geo/project-point (fn [& _] [100.0 200.0])
                      q/fill (fn [& _])
                      q/no-stroke (fn [])
                      q/create-font (fn [name size] [name size])
                      q/text-font (fn [font] (swap! fonts conj font))
                      q/text-align (fn [& _])
                      q/text-size (fn [size] (swap! sizes conj size))
                      q/triangle (fn [& args] (swap! triangles conj (vec args)))
                      q/text (fn [s _ _] (swap! labels conj s))]
          (aircraft/draw-fleet! {} 600 400 (aircraft/fleet-adsbs {:test? true
                                                                             :aircraft ["N12345"]
                                                                             :adsbs {"N12345" {:reg "N12345" :lat 1 :lon 2 :alt 2500 :trk 90}}}))
          (aircraft/draw-fleet! {} 600 400 (aircraft/fleet-adsbs {:aircraft ["N9"]
                                                                 :adsbs {"N9" {:reg "N9" :lat 1 :lon 2 :alt 2000 :trk 90}
                                                                        "N12345" {:reg "N12345" :lat 1 :lon 2 :alt 2500 :trk 90}}}))
          (should= [["Helvetica" 11]] (distinct @fonts))
          (should= [11] (distinct @sizes))))
      (should (< (first nose) 100.0))
      (should (< (second nose) 200.0))
      (should= #{"N345TS 3000 315 100"}
               (set (take 1 @labels)))
      (should= ["N9 2000 90 ---"] (drop 1 @labels))
      (should= (vec (apply concat (aircraft/triangle-vertices 100.0 200.0 315)))
               (first @triangles))))

  (it "draws nothing when no fleet aircraft has a position"
    (let [calls (atom 0)]
      (with-redefs [q/fill (fn [& _] (swap! calls inc))]
        (aircraft/draw-fleet! {} 600 400 {})
        (should= 0 @calls)))))
