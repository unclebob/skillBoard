(ns skillBoard.presenters.wind-map-spec
  (:require
    [skillBoard.atoms :as atoms]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.presenters.wind-map :as wind-map]
    [skillBoard.presenters.wind-map.particles :as particles]
    [skillBoard.wind-data :as wind-data]
    [quil.core :as q]
    [speclj.core :refer :all]))

(defn- fake-graphics
  ([calls] (fake-graphics calls 600 400))
  ([calls width height]
   (let [graphics (proxy [processing.core.PGraphics] []
                    (beginDraw [] (swap! calls conj [:begin-draw]))
                    (endDraw [] (swap! calls conj [:end-draw]))
                    (beginShape [] (swap! calls conj [:begin-shape]))
                    (endShape [] (swap! calls conj [:end-shape]))
                    (noFill [] (swap! calls conj [:no-fill]))
                    (noStroke [] (swap! calls conj [:no-stroke]))
                    (stroke
                      ([gray] (swap! calls conj [:stroke gray]))
                      ([r g b] (swap! calls conj [:stroke r g b]))
                      ([r g b a] (swap! calls conj [:stroke r g b a])))
                    (strokeWeight [weight] (swap! calls conj [:stroke-weight weight]))
                    (fill
                      ([gray] (swap! calls conj [:fill gray]))
                      ([r g b] (swap! calls conj [:fill r g b]))
                      ([r g b a] (swap! calls conj [:fill r g b a])))
                    (rect [x y w h] (swap! calls conj [:rect x y w h]))
                    (ellipse [x y w h] (swap! calls conj [:ellipse x y w h]))
                    (textFont [font] (swap! calls conj [:text-font font]))
                    (textAlign
                      ([align-x] (swap! calls conj [:text-align align-x]))
                      ([align-x align-y] (swap! calls conj [:text-align align-x align-y])))
                    (textSize [size] (swap! calls conj [:text-size size]))
                    (text [text x y] (swap! calls conj [:text text x y]))
                    (vertex [x y] (swap! calls conj [:vertex x y])))]
     (set! (.-width graphics) width)
     (set! (.-height graphics) height)
     graphics)))

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
      (with-redefs [particles/particles (atom [{:x -1 :y -1}])
                    particles/particle-field-size (atom [600 400])]
        (wind-map/ensure-particles! bounds grid 600 400 5000)
        (should= [{:x -1 :y -1}] @particles/particles)
        (wind-map/ensure-particles! bounds grid 601 400 5000)
        (should= config/wind-map-particle-count (count @particles/particles))
        (should= [601 400] @particles/particle-field-size))))

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
    (should= [70 150 255] (wind-map/color-rgb :blue))
    (should= [255 60 60] (wind-map/color-rgb config/ifr-color))
    (should= [255 0 255] (wind-map/color-rgb :magenta))
    (should= [255 235 90] (wind-map/color-rgb :yellow))
    (should= [255 255 255] (wind-map/color-rgb :white))
    (should= [255 255 255] (wind-map/color-rgb :unknown)))

  (it "creates flight category airport markers from nearby polled metars"
    (with-redefs [comm/polled-nearby-metars (atom {"KUGN" {:icaoId "KUGN"
                                                           :lat 42.4
                                                           :lon -87.9
                                                           :fltCat "IFR"
                                                           :clouds [{:cover "BKN" :base 700}]}
                                                   "KMKE" {:icaoId "KMKE"
                                                           :lat 42.9
                                                           :lon -87.9
                                                           :fltCat "VFR"
                                                           :clouds [{:cover "SCT" :base 2500}]}})
                  comm/polled-airspace-classes (atom {"KUGN" "D"
                                                      "KMKE" "C"})
                  comm/polled-metars (atom {"KMDW" {:icaoId "KMDW"
                                                    :lat 41.8
                                                    :lon -87.8
                                                    :fltCat "MVFR"}})]
      (should= [{:airport "KMKE" :lat 42.9 :lon -87.9 :color config/vfr-color :ceiling-ft-agl 10000 :airspace-class "C"}
                {:airport "KUGN" :lat 42.4 :lon -87.9 :color config/ifr-color :ceiling-ft-agl 700 :airspace-class "D"}]
               (vec (wind-map/flight-category-airport-markers)))))

  (it "falls back to configured polled metars before nearby metars have loaded"
    (with-redefs [comm/polled-nearby-metars (atom {})
                  comm/polled-airspace-classes (atom {})
                  comm/polled-metars (atom {"KUGN" {:icaoId "KUGN"
                                                    :lat 42.4
                                                    :lon -87.9
                                                    :fltCat "IFR"}})]
      (should= [{:airport "KUGN" :lat 42.4 :lon -87.9 :color config/ifr-color :ceiling-ft-agl nil :airspace-class nil}]
               (vec (wind-map/flight-category-airport-markers)))))

  (it "caches flight category airport markers briefly"
    (let [built (atom 0)]
      (reset! wind-map/airport-marker-cache {:time 0 :markers nil})
      (with-redefs [wind-map/flight-category-airport-markers (fn []
                                                               (swap! built inc)
                                                               [{:airport (str "K" @built)}])]
        (should= [{:airport "K1"}] (wind-map/cached-flight-category-airport-markers 1000))
        (should= [{:airport "K1"}] (wind-map/cached-flight-category-airport-markers 1500))
        (should= 1 @built)
        (should= [{:airport "K2"}] (wind-map/cached-flight-category-airport-markers 2101))
        (should= 2 @built))))

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

  (it "computes the lowest ceiling from broken, overcast, and vertical visibility layers"
    (should= 600 (wind-map/metar-ceiling-ft-agl {:clouds [{:cover "SCT" :base 1200}
                                                          {:cover "OVC" :base 900}
                                                          {:cover "BKN" :base 600}]}))
    (should= 300 (wind-map/metar-ceiling-ft-agl {:clouds [{:cover "VV" :base 300}]}))
    (should= 10000 (wind-map/metar-ceiling-ft-agl {:clouds [{:cover "FEW" :base 1200}
                                                            {:cover "SCT" :base 2500}]}))
    (should-be-nil (wind-map/metar-ceiling-ft-agl {})))

  (it "labels only class B, C, and D airport markers"
    (should (wind-map/label-airport? {:airspace-class "B"}))
    (should (wind-map/label-airport? {:airspace-class "C"}))
    (should (wind-map/label-airport? {:airspace-class "D"}))
    (should-not (wind-map/label-airport? {:airspace-class "E"}))
    (should-not (wind-map/label-airport? {})))

  (it "keys the static map layer by bounds, grid, size, poll time, and airport markers"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          grid {:source :open-meteo-gfs-hrrr :generated-at-ms 1000 :radius-nm 200}
          markers [{:airport "KUGN" :lat 42.4 :lon -87.9 :color config/ifr-color :ceiling-ft-agl 700 :airspace-class "D"}]]
      (with-redefs [wind-map/current-airport-metar-label (fn [] {:line "METAR KUGN" :color :green})]
        (should= [600 400 bounds :open-meteo-gfs-hrrr 1000 200 {:line "METAR KUGN" :color :green}
                  [["KUGN" 42.4 -87.9 config/ifr-color 700 "D"]]]
                 (wind-map/static-map-layer-key bounds 600 400 grid markers))
        (should-not= (wind-map/static-map-layer-key bounds 600 400 grid markers)
                     (wind-map/static-map-layer-key bounds 600 400 grid
                                                    (assoc-in markers [0 :color] config/vfr-color)))))))

  (it "interpolates ceiling observations and renders clear above ten thousand feet"
    (let [observations [{:lat 42.0 :lon -87.0 :ceiling-ft-agl 500}
                        {:lat 43.0 :lon -87.0 :ceiling-ft-agl 10000}]]
      (should (< (wind-map/interpolated-ceiling-ft-agl observations 42.1 -87.0) 5000))
      (should-be-nil (wind-map/ceiling-overlay-color nil))
      (should-be-nil (wind-map/ceiling-overlay-color 10000))
      (should= [255 87 50 42] (wind-map/ceiling-overlay-color 0))
      (should= [255 125 50 42] (wind-map/ceiling-overlay-color 750))))

  (it "draws a transparent ceiling overlay cell when the interpolated ceiling is below ten thousand feet"
    (let [calls (atom [])
          layer (fake-graphics calls)
          bounds {:top 43.0 :bottom 41.0 :left -88.0 :right -86.0}
          observations [{:lat 42.0 :lon -87.0 :ceiling-ft-agl 750}]]
      (#'wind-map/draw-layer-ceiling-cell! layer bounds 600 400 observations 100 100 3 2)
      (should-contain [:fill 255 125 50 42] @calls)
      (should-contain [:rect 300.0 200.0 100.0 100.0] @calls)))

  (it "orients particle line segments with local wind"
    (let [[x2 y2] (wind-map/particle-segment-end {:x 300 :y 200 :u 3 :v 4 :speed 5})]
      (should= 303.0 (double x2))
      (should= 196.0 (double y2))))

  (it "scales particle line segments with the drawing area"
    (should= 1.0 (wind-map/particle-segment-screen-scale 600 400))
    (should= 5.0 (wind-map/particle-segment-length 600 400 0))
    (should= 10.0 (wind-map/particle-segment-length 1200 800 0))
    (should= 12.0 (wind-map/particle-segment-length 1200 800 10))
    (should= 68.0 (wind-map/particle-segment-length 1200 800 100))
    (let [[x2 y2] (wind-map/particle-segment-end 1200 800 {:x 300 :y 200 :u 3 :v 4 :speed 5})]
      (should= 306.0 (double x2))
      (should= 192.0 (double y2))))

  (it "scales particle motion with the drawing area"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          base-frame (wind-map/particle-frame bounds {:points []} 600 400)
          high-res-frame (wind-map/particle-frame bounds {:points []} 1200 800)]
      (should= 1.0 (wind-map/particle-motion-screen-scale 600 400))
      (should= 2.0 (wind-map/particle-motion-screen-scale 1200 800))
      (should= (* 4 (:dx-per-knot base-frame)) (:dx-per-knot high-res-frame))
      (should= (* 4 (:dy-per-knot base-frame)) (:dy-per-knot high-res-frame))))

  (it "compresses particle velocity after screen scaling"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          reduced-frame (wind-map/particle-frame bounds {:points []} 600 400)]
      (with-redefs [particles/particle-motion-speed-scale 1.0]
        (let [full-speed-frame (wind-map/particle-frame bounds {:points []} 600 400)]
          (should= (* (/ 1.0 6.0) (:dx-per-knot full-speed-frame)) (:dx-per-knot reduced-frame))
          (should= (* (/ 1.0 6.0) (:dy-per-knot full-speed-frame)) (:dy-per-knot reduced-frame))))))

  (it "uses a zero-length segment when wind speed is zero"
    (should= [300 200]
             (wind-map/particle-segment-end {:x 300 :y 200 :u 0 :v 0 :speed 0})))

  (it "maps wind speeds to particle colors"
    (should= [155 210 255 205] (wind-map/wind-color 4))
    (should= [125 235 255 215] (wind-map/wind-color 9))
    (should= [165 255 190 225] (wind-map/wind-color 14))
    (should= [255 242 125 235] (wind-map/wind-color 19))
    (should= [255 185 100 240] (wind-map/wind-color 24))
    (should= [255 135 135 245] (wind-map/wind-color 29))
    (should= [255 80 120 250] (wind-map/wind-color 30)))

  (it "scales particle line segment length with wind speed"
    (should= 5.0 (wind-map/particle-segment-length 0))
    (should= 6.0 (wind-map/particle-segment-length 10))
    (should= 34.0 (wind-map/particle-segment-length 100)))

  (it "computes repeatable particle coordinates from fractional salt"
    (should= 50.0 (wind-map/particle-coordinate 0 100 1.5))
    (should= 25.0 (wind-map/particle-coordinate 1 100 1.125)))

  (it "projects and unprojects coordinates"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          [x y] (wind-map/project-point bounds 600 400 42.0 -87.0)]
      (should= [300.0 200.0] [x y])
      (should= [42.0 -87.0] (wind-map/unproject-point bounds 600 400 x y))))

  (it "fits map bounds to the screen aspect ratio to avoid stretching"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          fitted (wind-map/fit-bounds-to-screen bounds 600 400)]
      (should= 44.0 (:top fitted))
      (should= 40.0 (:bottom fitted))
      (should (< (:left fitted) -90.0))
      (should (> (:right fitted) -84.0))
      (should (< (Math/abs (- 1.5 (particles/bounds-aspect-ratio-nm fitted))) 0.001))))

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

  (it "uses calm wind when nearest wind has no points"
    (should= {:u 0 :v 0} (wind-map/nearest-wind {:points []} 42.0 -87.0)))

  (it "interpolates nearby wind vectors"
    (let [grid {:points [{:lat 42.0 :lon -87.0 :u 10 :v 0}
                         {:lat 42.0 :lon -88.0 :u 0 :v 10}]}]
      (should= {:u 5.0 :v 5.0}
               (wind-map/interpolated-wind grid 42.0 -87.5))))

  (it "uses calm wind when interpolation has no points"
    (should= {:u 0 :v 0} (wind-map/interpolated-wind {:points []} 42.0 -87.0)))

  (it "builds and reuses cached screen-space wind fields"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          grid {:source :synthetic
                :generated-at "now"
                :radius-nm 100
                :points [{:lat 42.0 :lon -87.0 :u 10 :v 0}
                         {:lat 42.0 :lon -88.0 :u 0 :v 10}]}]
      (with-redefs [particles/wind-field-cols 3
                    particles/wind-field-rows 3]
        (reset! wind-map/wind-field-cache {:key nil :field nil})
        (let [field (wind-map/current-wind-field bounds grid 600 400)]
          (should= [600 400 bounds :synthetic "now" 100 2]
                   (wind-map/wind-field-key bounds grid 600 400))
          (should= field (wind-map/current-wind-field bounds grid 600 400))
          (should= 3 (:cols field))
          (should= 3 (:rows field))
          (should= 3 (count (:cells field)))
          (should= 3 (count (first (:cells field))))))))

  (it "samples cached screen-space wind with bilinear interpolation"
    (let [field {:width 100
                 :height 100
                 :cols 2
                 :rows 2
                 :cells [[{:u 0 :v 0} {:u 10 :v 0}]
                         [{:u 0 :v 10} {:u 10 :v 10}]]}]
      (should= {:u 5.0 :v 5.0} (wind-map/sample-wind-field field 50 50))
      (should= {:u 0.0 :v 0.0} (wind-map/sample-wind-field field -10 -10))
      (should= {:u 10.0 :v 10.0} (wind-map/sample-wind-field field 150 150))))

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

  (it "caches particle drawing endpoints and stroke colors while stepping"
    (let [bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          field {:width 600
                 :height 400
                 :cols 1
                 :rows 1
                 :cells [[{:u 3 :v 4}]]}
          frame (merge {:bounds bounds :grid {:points []} :width 600 :height 400 :wind-field field}
                       (#'particles/wind-pixel-factors bounds 600 400))
          particle {:x 300 :y 200 :age 119 :born-at 1000}
          stepped (wind-map/step-particle-with-frame frame 2500 particle)]
      (should (:x2 stepped))
      (should (:y2 stepped))
      (should= [165 255 190 220.0] (:stroke stepped))))

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
          grid {:points [{:lat 42.0 :lon -87.0 :u 100000 :v 0}]}
          particle {:x 599 :y 200 :seed 5 :age 3 :born-at 1000}]
      (with-redefs [rand (fn [size] (* size 0.5))]
        (reset! wind-map/wind-field-cache {:key nil :field nil})
        (let [replacement (wind-map/step-particle bounds grid 600 400 2500 particle)]
          (should= 300.0 (:x replacement))
          (should= 200.0 (:y replacement))
          (should= 2500 (:born-at replacement))
          (should= 0 (:age replacement))))))

  (it "provides fallback text for split-flap summaries"
    (should= [{:line "SURFACE WINDS" :color config/info-color}
              {:line "OPEN-METEO 10M WIND FIELD" :color config/info-color}]
             (wind-map/make-wind-map-screen)))

  (it "draws airport, marker, outline, particle, and source label with Quil calls"
    (let [calls (atom [])
          bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          outline {:name "WI"
                   :bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
                   :rings [[[44.0 -90.0] [44.0 -84.0] [40.0 -84.0] [40.0 -90.0]]]}
          marker {:airport "KUGN" :lat 42.0 :lon -87.0 :color config/vfr-color :airspace-class "D"}]
      (with-redefs [config/display-info (atom {:header-font nil :annotation-font nil})
                    q/fill (fn [& args] (swap! calls conj (into [:fill] args)))
                    q/stroke (fn [& args] (swap! calls conj (into [:stroke] args)))
                    q/stroke-weight (fn [& args] (swap! calls conj (into [:stroke-weight] args)))
                    q/ellipse (fn [& args] (swap! calls conj (into [:ellipse] args)))
                    q/text-font (fn [& args] (swap! calls conj (into [:text-font] args)))
                    q/text-align (fn [& args] (swap! calls conj (into [:text-align] args)))
                    q/text-size (fn [& args] (swap! calls conj (into [:text-size] args)))
                    q/text (fn [& args] (swap! calls conj (into [:text] args)))
                    q/no-fill (fn [& args] (swap! calls conj (into [:no-fill] args)))
                    q/begin-shape (fn [& args] (swap! calls conj (into [:begin-shape] args)))
                    q/end-shape (fn [& args] (swap! calls conj (into [:end-shape] args)))
                    q/vertex (fn [& args] (swap! calls conj (into [:vertex] args)))
                    q/line (fn [& args] (swap! calls conj (into [:line] args)))]
        (wind-map/draw-airport! bounds 600 400)
        (wind-map/draw-flight-category-airport! bounds 600 400 marker)
        (wind-map/draw-state-outline! bounds 600 400 outline)
        (wind-map/draw-source-label! {:source :synthetic :radius-nm 150} 600 400)
        (wind-map/draw-particle! 600 400 {:x 300 :y 200 :u 3 :v 4 :speed 5 :opacity 0.5})
        (should (some (fn [[op _ _ w h]]
                        (and (= :ellipse op)
                             (= 10.0 (double w))
                             (= 10.0 (double h))))
                      @calls))
        (should (some (fn [[op x y w h]]
                        (and (= :ellipse op)
                             (= 300.0 (double x))
                             (= 200.0 (double y))
                             (= 11.0 (double w))
                             (= 11.0 (double h))))
                      @calls))
        (should-contain [:text "KUGN" 308.0 200.0] @calls)
        (should-contain [:text "WI" 300.0 200.0] @calls)
        (should-contain [:text "Source: synthetic  Radius: 150 NM  Polled: unknown UTC" 20 400] @calls)
        (should-contain [:line 300 200 303.0 196.0] @calls))))

  (it "flags default or stale wind data"
    (let [now 10000000]
      (should (wind-map/stale-wind-data? now {:source :synthetic :generated-at-ms now}))
      (should (wind-map/stale-wind-data? now {:source :open-meteo-gfs-hrrr}))
      (should (wind-map/stale-wind-data? now {:source :open-meteo-gfs-hrrr
                                              :generated-at-ms (- now wind-map/stale-wind-data-ms 1)}))
      (should-not (wind-map/stale-wind-data? now {:source :open-meteo-gfs-hrrr
                                                  :generated-at-ms (- now wind-map/stale-wind-data-ms)}))))

  (it "draws a red stale wind data warning at the bottom of the screen"
    (let [calls (atom [])]
      (with-redefs [config/display-info (atom {:header-font nil :annotation-font nil})
                    q/fill (fn [& args] (swap! calls conj (into [:fill] args)))
                    q/text-font (fn [& args] (swap! calls conj (into [:text-font] args)))
                    q/text-align (fn [& args] (swap! calls conj (into [:text-align] args)))
                    q/text-size (fn [& args] (swap! calls conj (into [:text-size] args)))
                    q/text (fn [& args] (swap! calls conj (into [:text] args)))]
        (wind-map/draw-stale-wind-data-warning! 10000 {:source :synthetic :generated-at-ms 10000} 600 400)
        (should-contain [:fill 255 60 60] @calls)
        (should-contain [:text "WIND DATA IS OUT OF DATE" 300 394] @calls))))

  (it "draws particle lines grouped by cached stroke"
    (let [calls (atom [])
          particles [{:x 1 :y 2 :x2 3 :y2 4 :stroke [1 2 3 4]}
                     {:x 5 :y 6 :x2 7 :y2 8 :stroke [1 2 3 4]}
                     {:x 9 :y 10 :x2 11 :y2 12 :stroke [5 6 7 8]}]]
      (with-redefs [q/stroke-weight (fn [& args] (swap! calls conj (into [:stroke-weight] args)))
                    q/stroke (fn [& args] (swap! calls conj (into [:stroke] args)))
                    q/line (fn [& args] (swap! calls conj (into [:line] args)))]
        (wind-map/draw-particles! particles)
        (should= 1 (count (filter #(= :stroke-weight (first %)) @calls)))
        (should= 2 (count (filter #(= :stroke (first %)) @calls)))
        (should-contain [:line 1 2 3 4] @calls)
        (should-contain [:line 5 6 7 8] @calls)
        (should-contain [:line 9 10 11 12] @calls))))

  (it "skips drawing unlabeled or coordinate-less airport markers"
    (let [calls (atom [])
          bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}]
      (with-redefs [q/fill (fn [& args] (swap! calls conj (into [:fill] args)))
                    q/stroke (fn [& args] (swap! calls conj (into [:stroke] args)))
                    q/stroke-weight (fn [& args] (swap! calls conj (into [:stroke-weight] args)))
                    q/ellipse (fn [& args] (swap! calls conj (into [:ellipse] args)))
                    q/text (fn [& args] (swap! calls conj (into [:text] args)))]
        (wind-map/draw-flight-category-airport! bounds 600 400 {:airport "KAAA" :color :green})
        (wind-map/draw-flight-category-airport! bounds 600 400 {:airport "KBBB" :lat 42.0 :lon -87.0 :color :green})
        (should-not (some #(= [:text "KAAA"] (take 2 %)) @calls))
        (should-not (some #(= [:text "KBBB"] (take 2 %)) @calls)))))

  (it "draws layer-backed airports, outlines, and source labels"
    (let [calls (atom [])
          layer (fake-graphics calls)
          bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          outline {:name "IL"
                   :bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
                   :rings [[[44.0 -90.0] [44.0 -84.0] [40.0 -84.0] [40.0 -90.0]]]}
          marker {:airport "KORD" :lat 42.0 :lon -87.0 :color :yellow :airspace-class "B"}]
      (with-redefs [config/display-info (atom {:header-font nil :annotation-font nil})
                    wind-map/state-outlines (fn [] [outline])]
        (#'wind-map/draw-layer-flight-category-airport! layer bounds 600 400 marker)
        (#'wind-map/draw-layer-flight-category-airport! layer bounds 600 400 {:airport "KAAA" :color :green})
        (#'wind-map/draw-layer-state-outline! layer bounds 600 400 outline)
        (#'wind-map/draw-layer-state-outlines! layer bounds 600 400)
        (#'wind-map/draw-layer-source-label! layer {:source :open-meteo :radius-nm 200 :generated-at-ms 0} 600 400)
        (should-contain [:ellipse 300.0 200.0 11.0 11.0] @calls)
        (should-contain [:text "KORD" 308.0 200.0] @calls)
        (should-contain [:text "IL" 300.0 200.0] @calls)
        (should-contain [:text-size 9.0] @calls)
        (should-contain [:text "Source: open-meteo  Radius: 200 NM  Polled: 00:00Z UTC" 20.0 400.0] @calls))))

  (it "formats the source label with the poll time in utc"
    (should= "Source: open-meteo  Radius: 200 NM  Polled: 00:00Z UTC"
             (wind-map/source-label-text {:source :open-meteo :radius-nm 200 :generated-at-ms 0})))

  (it "scales the source label font size with screen size"
    (should= 9 (wind-map/source-label-font-size 300 200))
    (should= 9 (wind-map/source-label-font-size 600 400))
    (should= 17 (wind-map/source-label-font-size 1200 800)))

  (it "computes metar split-flap metrics from configured display metrics"
    (with-redefs [config/display-info (atom {:sf-font-size 16
                                             :font-width 8
                                             :font-height 20
                                             :sf-char-gap 1})]
      (should= {:sf-font-size 16
                :font-width 8
                :font-height 20
                :sf-char-gap 1
                :flap-width 9
                :flap-height 25.0}
               (wind-map/metar-split-flap-metrics 600 400))))

  (it "computes fallback metar split-flap metrics from screen size"
    (with-redefs [config/display-info (atom {})]
      (should= {:sf-font-size 18
                :font-width 10.44
                :font-height 18
                :sf-char-gap 0.8352
                :flap-width 11.2752
                :flap-height 22.5}
               (wind-map/metar-split-flap-metrics 600 400))))

  (it "truncates metar text to the available split-flap columns"
    (should= 12.0 (wind-map/metar-margin 600 400))
    (should= 64 (wind-map/metar-max-chars 600 12.0 9))
    (should= "METAR" (wind-map/truncate-metar-line "METAR KUGN" 5))
    (should= "METAR KUGN" (wind-map/truncate-metar-line "METAR KUGN" 64)))

  (it "places the split-flap metar line at the lower right"
    (with-redefs [config/display-info (atom {:sf-font-size 16
                                             :font-width 8
                                             :font-height 20
                                             :sf-char-gap 1})]
      (should= {:line "METAR"
                :sf-font-size 16
                :flap-width 9
                :flap-height 25.0
                :x 543.0
                :y 363.0
                :backing-rect-top-left-x 0.8
                :backing-rect-top-left-y 2.0
                :backing-rect-width 6.4
                :backing-rect-height 16.0}
               (wind-map/split-flap-metar-geometry 600 400 "METAR"))))

  (it "draws the current airport metar as split-flap text on the bottom right"
    (let [calls (atom [])]
      (with-redefs [config/display-info (atom {:sf-font :split-flap
                                               :sf-font-size 16
                                               :font-width 8
                                               :font-height 20
                                               :sf-char-gap 1})
                    wind-map/current-airport-metar-label (fn [] {:line "METAR KUGN 231853Z 18012KT 10SM CLR" :color :green})
                    q/no-stroke (fn [& args] (swap! calls conj (into [:no-stroke] args)))
                    q/fill (fn [& args] (swap! calls conj (into [:fill] args)))
                    q/rect (fn [& args] (swap! calls conj (into [:rect] args)))
                    q/text-font (fn [& args] (swap! calls conj (into [:text-font] args)))
                    q/text-align (fn [& args] (swap! calls conj (into [:text-align] args)))
                    q/text-size (fn [& args] (swap! calls conj (into [:text-size] args)))
                    q/text (fn [& args] (swap! calls conj (into [:text] args)))]
        (wind-map/draw-current-airport-metar! 600 400)
        (should-contain [:no-stroke] @calls)
        (should-contain [:fill 0 255 0] @calls)
        (should-contain [:text-font :split-flap] @calls)
        (should-contain [:text-align :left :top] @calls)
        (should-contain [:text-size 16] @calls)
        (should-contain [:rect 273.8 365.0 6.4 16.0] @calls)
        (should-contain [:text "M" 273 363.0] @calls))))

  (it "draws the cached layer metar as split-flap text on the bottom right"
    (let [calls (atom [])
          layer (fake-graphics calls)]
      (with-redefs [config/display-info (atom {:sf-font :split-flap
                                               :sf-font-size 16
                                               :font-width 8
                                               :font-height 20
                                               :sf-char-gap 1})
                    wind-map/current-airport-metar-label (fn [] {:line "METAR KUGN 231853Z 18012KT 10SM CLR" :color :green})]
        (#'wind-map/draw-layer-current-airport-metar! layer 600 400)
        (should-contain [:no-stroke] @calls)
        (should-contain [:fill 0.0 255.0 0.0] @calls)
        (should-contain [:text-font :split-flap] @calls)
        (should-contain [:text-align processing.core.PConstants/LEFT processing.core.PConstants/TOP] @calls)
        (should-contain [:text-size 16.0] @calls)
        (should-contain [:rect 273.8 365.0 6.4 16.0] @calls)
        (should-contain [:text "M" 273.0 363.0] @calls))))

  (it "places the wind speed scale on the right edge in the middle third"
    (should= {:x 592.0
              :y (/ 400 3.0)
              :width 8.0
              :height (/ 400 3.0)
              :screen-width 600
              :screen-height 400
              :title-x 600
              :label-x 586.0}
             (wind-map/wind-speed-scale-geometry 600 400))
    (let [geometry (wind-map/wind-speed-scale-geometry 600 400)]
      (should= (:y geometry) (wind-map/wind-speed-scale-y geometry wind-map/wind-speed-scale-max))
      (should= 200.0 (wind-map/wind-speed-scale-y geometry 15))
      (should= (+ (:y geometry) (:height geometry)) (wind-map/wind-speed-scale-y geometry 0))))

  (it "uses wind particle colors for the wind speed scale bands"
    (let [bands (wind-map/wind-speed-scale-bands (wind-map/wind-speed-scale-geometry 600 400))]
      (should= [[0 5 [155 210 255 205]]
                [5 10 [125 235 255 215]]
                [10 15 [165 255 190 225]]
                [15 20 [255 242 125 235]]
                [20 25 [255 185 100 240]]
                [25 30 [255 135 135 245]]]
               (mapv (juxt :low :high :color) bands))
      (should= ["0-5" "5-10" "10-15" "15-20" "20-25" "25+"]
               (mapv wind-map/wind-speed-scale-band-label bands))))

  (it "draws a static wind speed scale with labels centered in each color band"
    (let [calls (atom [])
          layer (fake-graphics calls)]
      (#'wind-map/draw-layer-wind-speed-scale! layer 600 400)
      (should-contain [:no-stroke] @calls)
      (should-contain [:rect 592.0 244.44444 8.0 22.222221] @calls)
      (should-contain [:rect 592.0 222.22223 8.0 22.222214] @calls)
      (should-contain [:rect 592.0 200.0 8.0 22.222229] @calls)
      (should-contain [:rect 592.0 177.77779 8.0 22.222214] @calls)
      (should-contain [:rect 592.0 155.55556 8.0 22.222229] @calls)
      (should-contain [:rect 592.0 133.33333 8.0 22.222221] @calls)
      (should-contain [:text-size 7.92] @calls)
      (should-contain [:text "0-5" 586.0 255.55556] @calls)
      (should-contain [:text "5-10" 586.0 233.33334] @calls)
      (should-contain [:text "10-15" 586.0 211.11111] @calls)
      (should-contain [:text "15-20" 586.0 188.8889] @calls)
      (should-contain [:text "20-25" 586.0 166.66667] @calls)
      (should-contain [:text "25+" 586.0 144.44444] @calls)
      (should-contain [:text-size 9.9] @calls)
      (should-contain [:text "wind" 600.0 129.33333] @calls)))

  (it "draws a static ceiling scale abutted to the left edge"
    (let [calls (atom [])
          layer (fake-graphics calls)]
      (should= {:x 0.0
                :y 133.33333333333334
                :width 8.0
                :height 133.33333333333334
                :screen-width 600
                :screen-height 400
                :title-x 0.0
                :label-x 14.0}
               (wind-map/ceiling-scale-geometry 600 400))
      (let [geometry (wind-map/ceiling-scale-geometry 600 400)]
        (should= (+ (:y geometry) (:height geometry)) (wind-map/ceiling-scale-y geometry 0))
        (should= 200.0 (wind-map/ceiling-scale-y geometry 5000))
        (should= (:y geometry) (wind-map/ceiling-scale-y geometry wind-map/ceiling-overlay-max-ft))
        (should= 20 (count (wind-map/ceiling-scale-bands geometry)))
        (should= [{:label "<500" :y 263.33333333333337}
                  {:label "1K" :y 253.33333333333334}
                  {:label "2K" :y 240.0}
                  {:label "3K" :y 226.66666666666669}
                  {:label "4K" :y 213.33333333333334}
                  {:label "5K" :y 200.0}
                  {:label "6K" :y 186.66666666666669}
                  {:label "7K" :y 173.33333333333334}
                  {:label "8K" :y 160.0}
                  {:label "9K" :y 146.66666666666669}
                  {:label "10K" :y 133.33333333333334}]
                 (wind-map/ceiling-scale-labels geometry)))
      (#'wind-map/draw-layer-ceiling-scale! layer 600 400)
      (should-contain [:no-stroke] @calls)
      (should-contain [:rect 0.0 260.0 8.0 6.6666565] @calls)
      (should-contain [:rect 0.0 133.33333 8.0 6.6666718] @calls)
      (should-contain [:text-size 7.92] @calls)
      (should-contain [:text "<500" 14.0 263.33334] @calls)
      (should-contain [:text "1K" 14.0 253.33333] @calls)
      (should-contain [:text "5K" 14.0 200.0] @calls)
      (should-contain [:text "10K" 14.0 133.33333] @calls)
      (should-contain [:text-size 9.9] @calls)
      (should-contain [:text "ceil" 0.0 129.33333] @calls)))

  (it "scales side scale labels and titles from the screen size"
    (should= 7.92 (wind-map/wind-speed-scale-label-font-size 600 400))
    (should= 14.96 (wind-map/wind-speed-scale-label-font-size 1200 800))
    (should= 9.9 (wind-map/scale-title-font-size 600 400))
    (should= 18.7 (wind-map/scale-title-font-size 1200 800)))

  (it "draws the cached map layer before updated particles"
    (let [calls (atom [])
          particle-store (atom [{:id 1}])
          bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          fitted-bounds (wind-map/fit-bounds-to-screen bounds 600 400)
          grid {:center [42.0 -87.0]
                :radius-nm 100
                :source :open-meteo-gfs-hrrr
                :generated-at-ms Long/MAX_VALUE}]
      (with-redefs [wind-data/current-grid (fn [] grid)
                    wind-data/radius-bounds (fn [center radius-nm]
                                              (swap! calls conj [:bounds center radius-nm])
                                              bounds)
                    q/width (fn [] 600)
                    q/height (fn [] 400)
                    wind-map/particles particle-store
                    wind-map/cached-flight-category-airport-markers (fn [now]
                                                                      (swap! calls conj [:markers (integer? now)])
                                                                      [{:airport "KORD"}])
                    wind-map/static-map-layer (fn [received-bounds width height received-grid markers]
                                                (swap! calls conj [:layer received-bounds width height received-grid markers])
                                                :layer)
                    wind-map/ensure-particles! (fn [received-bounds received-grid width height now]
                                                 (swap! calls conj [:ensure received-bounds received-grid width height (integer? now)]))
                    wind-map/particle-frame (fn [received-bounds received-grid width height]
                                              (swap! calls conj [:frame received-bounds received-grid width height])
                                              :frame)
                    wind-map/step-particle-with-frame (fn [frame now particle]
                                                        (swap! calls conj [:step frame (integer? now) particle])
                                                        (assoc particle :updated true))
                    q/image (fn [& args] (swap! calls conj (into [:image] args)))
                    wind-map/draw-particles! (fn [particles] (swap! calls conj [:particles particles]))]
        (wind-map/draw-wind-map!)
        (should-contain [:bounds [42.0 -87.0] 100] @calls)
        (should-contain [:markers true] @calls)
        (should-contain [:layer fitted-bounds 600 400 grid [{:airport "KORD"}]] @calls)
        (should-contain [:frame fitted-bounds grid 600 400] @calls)
        (should-contain [:step :frame true {:id 1}] @calls)
        (should-contain [:image :layer 0 0] @calls)
        (should-contain [:particles [{:id 1 :updated true}]] @calls)
        (should= [{:id 1 :updated true}] @particle-store))))

  (it "reuses and rerenders static map layers by cache key"
    (let [calls (atom [])
          layer (fake-graphics calls)
          renders (atom 0)
          bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          grid {:source :synthetic :radius-nm 100}
          markers []]
      (with-redefs-fn {#'wind-map/static-map-layer-cache (atom {:key nil :layer nil})
                       #'q/create-graphics (fn [_ _] layer)
                       #'wind-map/render-static-map-layer! (fn [& _] (swap! renders inc))}
        (fn []
          (should= layer (wind-map/static-map-layer bounds 600 400 grid markers))
          (should= layer (wind-map/static-map-layer bounds 600 400 grid markers))
          (should= 1 @renders)
          (should= layer (wind-map/static-map-layer bounds 601 400 grid markers))
          (should= 2 @renders)))))

  (it "invalidates the cached static map layer when the screen changes"
    (with-redefs [atoms/screen-changed? (atom true)
                  wind-map/static-map-layer-cache (atom {:key [:old] :layer :layer})]
      (wind-map/refresh-static-map-layer-on-screen-entry!)
      (should= {:key nil :layer nil} @wind-map/static-map-layer-cache)))
