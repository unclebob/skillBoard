(ns skillBoard.presenters.wind-map-spec
  (:require
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.presenters.wind-map :as wind-map]
    [speclj.core :refer :all]))

(describe "wind map presenter"
  (it "initializes particles across the current drawing bounds"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          grid {:points [{:lat 42.0 :lon -87.0 :u 1 :v 0}]}
          particles (wind-map/make-particles 20 bounds grid 600 400 1000)]
      (should= 20 (count particles))
      (should (every? #(<= 0 (:x %) 600) particles))
      (should (every? #(<= 0 (:y %) 400) particles))
      (should (every? some? (map :seed particles)))
      (should (every? #(<= 0 (:born-at %) 1000) particles))
      (should (every? #(<= 0 (:age %) 999) particles))
      (should (every? #(<= 2000 (:life-ms %) 3000) particles))))

  (it "uses random birth positions"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          grid {:points [{:lat 42.0 :lon -87.0 :u 1 :v 0}]}
          random-values (atom [0.1 0.2 0.5 0.4 0.7 0.8 0.5 0.6])]
      (with-redefs [rand (fn [size]
                           (* size (let [value (first @random-values)]
                                     (swap! random-values rest)
                                     value)))]
        (let [particles (wind-map/make-particles 2 bounds grid 600 400 1000)]
          (should= [[120.0 200.0] [480.0 200.0]]
                   (mapv (juxt :x :y) particles))))))

  (it "initializes particles when empty or size changes"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          grid {:points [{:lat 42.0 :lon -87.0 :u 1 :v 0}]}]
      (with-redefs [wind-map/particles (atom [{:x -1 :y -1}])
                    wind-map/particle-field-size (atom [600 400])]
        (wind-map/ensure-particles! bounds grid 600 400 5000)
        (should= [{:x -1 :y -1}] @wind-map/particles)
        (wind-map/ensure-particles! bounds grid 601 400 5000)
        (should= config/wind-map-particle-count (count @wind-map/particles))
        (should= [601 400] @wind-map/particle-field-size))))

  (it "fades particles in, holds them, fades them out by their own lifetime, then marks them dead"
    (let [particle {:born-at 1000 :life-ms 2500}]
      (should= 0.0 (wind-map/particle-opacity 1000 particle))
      (should= 0.5 (wind-map/particle-opacity 1250 particle))
      (should= 1.0 (wind-map/particle-opacity 1500 particle))
      (should= 1.0 (wind-map/particle-opacity 2999 particle))
      (should= 0.5 (wind-map/particle-opacity 3250 particle))
      (should= 0.0 (double (wind-map/particle-opacity 3500 particle)))
      (should-not (wind-map/particle-dead? 3499 particle))
      (should (wind-map/particle-dead? 3500 particle))))

  (it "assigns each new particle a random lifetime between two and three seconds"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          grid {:points [{:lat 42.0 :lon -87.0 :u 1 :v 0}]}
          random-values (atom [0.5 0.5 0.25])]
      (with-redefs [rand (fn [size]
                           (* size (let [value (first @random-values)]
                                     (swap! random-values rest)
                                     value)))]
        (let [particle (wind-map/random-particle bounds grid 600 400 1 1000)]
          (should= 2250.0 (:life-ms particle))))))

  (it "maps flight categories to marker colors"
    (should= config/vfr-color (wind-map/flight-category-color "VFR"))
    (should= config/mvfr-color (wind-map/flight-category-color "MVFR"))
    (should= config/ifr-color (wind-map/flight-category-color "IFR"))
    (should= config/lifr-color (wind-map/flight-category-color "LIFR"))
    (should= config/info-color (wind-map/flight-category-color nil)))

  (it "converts configured marker color keywords to RGB"
    (should= [0 255 0] (wind-map/color-rgb config/vfr-color))
    (should= [255 60 60] (wind-map/color-rgb config/ifr-color))
    (should= [255 255 255] (wind-map/color-rgb :unknown)))

  (it "creates flight category airport markers from nearby polled metars"
    (with-redefs [comm/polled-nearby-metars (atom {"KUGN" {:icaoId "KUGN"
                                                           :lat 42.4
                                                           :lon -87.9
                                                           :fltCat "IFR"}
                                                   "KMKE" {:icaoId "KMKE"
                                                           :lat 42.9
                                                           :lon -87.9
                                                           :fltCat "VFR"}})
                  comm/polled-airspace-classes (atom {"KUGN" "D"
                                                      "KMKE" "C"})
                  comm/polled-metars (atom {"KMDW" {:icaoId "KMDW"
                                                    :lat 41.8
                                                    :lon -87.8
                                                    :fltCat "MVFR"}})]
      (should= [{:airport "KMKE" :lat 42.9 :lon -87.9 :color config/vfr-color :airspace-class "C"}
                {:airport "KUGN" :lat 42.4 :lon -87.9 :color config/ifr-color :airspace-class "D"}]
               (vec (wind-map/flight-category-airport-markers)))))

  (it "falls back to configured polled metars before nearby metars have loaded"
    (with-redefs [comm/polled-nearby-metars (atom {})
                  comm/polled-airspace-classes (atom {})
                  comm/polled-metars (atom {"KUGN" {:icaoId "KUGN"
                                                    :lat 42.4
                                                    :lon -87.9
                                                    :fltCat "IFR"}})]
      (should= [{:airport "KUGN" :lat 42.4 :lon -87.9 :color config/ifr-color :airspace-class nil}]
               (vec (wind-map/flight-category-airport-markers)))))

  (it "always includes the home airport marker"
    (with-redefs [comm/polled-nearby-metars (atom {})
                  comm/polled-airspace-classes (atom {})
                  comm/polled-metars (atom {})
                  config/flight-category-airports ["KMKE"]]
      (should= [{:airport config/airport
                 :lat (first config/airport-lat-lon)
                 :lon (second config/airport-lat-lon)
                 :color config/info-color}]
               (vec (wind-map/flight-category-airport-markers)))))

  (it "labels only class B, C, and D airport markers"
    (should (wind-map/label-airport? {:airspace-class "B"}))
    (should (wind-map/label-airport? {:airspace-class "C"}))
    (should (wind-map/label-airport? {:airspace-class "D"}))
    (should-not (wind-map/label-airport? {:airspace-class "E"}))
    (should-not (wind-map/label-airport? {})))

  (it "keys the static map layer by bounds, grid, size, and airport markers"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          grid {:source :open-meteo-gfs-hrrr :radius-nm 200}
          markers [{:airport "KUGN" :lat 42.4 :lon -87.9 :color config/ifr-color :airspace-class "D"}]]
      (should= [600 400 bounds :open-meteo-gfs-hrrr 200 [["KUGN" 42.4 -87.9 config/ifr-color "D"]]]
               (wind-map/static-map-layer-key bounds 600 400 grid markers))
      (should-not= (wind-map/static-map-layer-key bounds 600 400 grid markers)
                   (wind-map/static-map-layer-key bounds 600 400 grid
                                                  (assoc-in markers [0 :color] config/vfr-color)))))

  (it "orients particle line segments with local wind"
    (let [[x2 y2] (wind-map/particle-segment-end {:x 300 :y 200 :u 3 :v 4 :speed 5})]
      (should= 303.6 (double x2))
      (should= 195.2 (double y2))))

  (it "scales particle line segment length with wind speed"
    (should= 5 (wind-map/particle-segment-length 0))
    (should= 12.0 (wind-map/particle-segment-length 10))
    (should= 34 (wind-map/particle-segment-length 100)))

  (it "projects and unprojects coordinates"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          [x y] (wind-map/project-point bounds 600 400 42.0 -87.0)]
      (should= [300.0 200.0] [x y])
      (should= [42.0 -87.0] (wind-map/unproject-point bounds 600 400 x y))))

  (it "loads nearby state outlines from GeoJSON"
    (let [outlines (wind-map/load-state-outlines)]
      (should (some #{"WI"} (map :name outlines)))
      (should (some #{"IL"} (map :name outlines)))
      (should (< 10 (count (first (:rings (first outlines))))))
      (should (:bounds (first outlines)))))

  (it "selects nearest wind vector"
    (let [grid {:points [{:lat 42.0 :lon -87.0 :u 1 :v 2}
                         {:lat 43.0 :lon -88.0 :u 9 :v 8}]}]
      (should= {:lat 43.0 :lon -88.0 :u 9 :v 8}
               (wind-map/nearest-wind grid 42.9 -88.1))))

  (it "interpolates nearby wind vectors"
    (let [grid {:points [{:lat 42.0 :lon -87.0 :u 10 :v 0}
                         {:lat 42.0 :lon -88.0 :u 0 :v 10}]}]
      (should= {:u 5.0 :v 5.0}
               (wind-map/interpolated-wind grid 42.0 -87.5))))

  (it "computes wind speed"
    (should= 5.0 (wind-map/wind-speed {:u 3 :v 4})))

  (it "steps particles along wind with map-scaled motion"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          grid {:points [{:lat 42.0 :lon -87.0 :u 10 :v -10}]}
          particle {:x 300 :y 200 :age 119 :born-at 1000}
          stepped (wind-map/step-particle bounds grid 600 400 2500 particle)]
      (should (> (:x stepped) (:x particle)))
      (should (> (:y stepped) (:y particle)))
      (should= 120 (:age stepped))
      (should= 10.0 (:u stepped))
      (should= -10.0 (:v stepped))
      (should= 1.0 (:opacity stepped))
      (should= (Math/sqrt 200) (:speed stepped))))

  (it "keeps living particles regardless of age count"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          grid {:points [{:lat 42.0 :lon -87.0 :u 1 :v 0}]}
          particle {:x 300 :y 200 :seed 1 :age 10000 :born-at 1000}
          stepped (wind-map/step-particle bounds grid 600 400 2500 particle)]
      (should (< 300 (:x stepped)))
      (should= 10001 (:age stepped))))

  (it "replaces dead particles with new random particles"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          grid {:points [{:lat 42.0 :lon -87.0 :u 1 :v 0}]}
          particle {:x 300 :y 200 :seed 4 :age 3 :born-at 1000}]
      (with-redefs [rand (fn [size] (* size 0.25))]
        (let [replacement (wind-map/step-particle bounds grid 600 400 4000 particle)]
          (should= 150.0 (:x replacement))
          (should= 100.0 (:y replacement))
          (should= 4 (:seed replacement))
          (should= 4000 (:born-at replacement))
          (should= 0 (:age replacement))
          (should= 0.0 (:opacity replacement))))))

  (it "replaces particles that leave the map instead of wrapping them"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          grid {:points [{:lat 42.0 :lon -87.0 :u 10000 :v 0}]}
          particle {:x 599 :y 200 :seed 5 :age 3 :born-at 1000}]
      (with-redefs [rand (fn [size] (* size 0.5))]
        (let [replacement (wind-map/step-particle bounds grid 600 400 2500 particle)]
          (should= 300.0 (:x replacement))
          (should= 200.0 (:y replacement))
          (should= 2500 (:born-at replacement))
          (should= 0 (:age replacement))))))

  (it "provides fallback text for split-flap summaries"
    (should= [{:line "SURFACE WINDS" :color config/info-color}
              {:line "OPEN-METEO 10M WIND FIELD" :color config/info-color}]
             (wind-map/make-wind-map-screen))))
