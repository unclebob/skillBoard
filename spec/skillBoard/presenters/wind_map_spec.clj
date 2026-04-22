(ns skillBoard.presenters.wind-map-spec
  (:require
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
                    (stroke
                      ([gray] (swap! calls conj [:stroke gray]))
                      ([r g b] (swap! calls conj [:stroke r g b]))
                      ([r g b a] (swap! calls conj [:stroke r g b a])))
                    (strokeWeight [weight] (swap! calls conj [:stroke-weight weight]))
                    (fill
                      ([gray] (swap! calls conj [:fill gray]))
                      ([r g b] (swap! calls conj [:fill r g b]))
                      ([r g b a] (swap! calls conj [:fill r g b a])))
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

  (it "uses a zero-length segment when wind speed is zero"
    (should= [300 200]
             (wind-map/particle-segment-end {:x 300 :y 200 :u 0 :v 0 :speed 0})))

  (it "maps wind speeds to particle colors"
    (should= [155 210 255 205] (wind-map/wind-color 4))
    (should= [165 255 190 220] (wind-map/wind-color 14))
    (should= [255 242 125 235] (wind-map/wind-color 24))
    (should= [255 135 135 245] (wind-map/wind-color 25)))

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
        (wind-map/draw-source-label! {:source :synthetic :radius-nm 150} 400)
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
        (should-contain [:text "Source: synthetic  Radius: 150 NM" 20 380] @calls)
        (should-contain [:line 300 200 303.0 196.0] @calls))))

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
        (#'wind-map/draw-layer-source-label! layer {:source :open-meteo :radius-nm 200} 400)
        (should-contain [:ellipse 300.0 200.0 11.0 11.0] @calls)
        (should-contain [:text "KORD" 308.0 200.0] @calls)
        (should-contain [:text "IL" 300.0 200.0] @calls)
        (should-contain [:text "Source: open-meteo  Radius: 200 NM" 20.0 380.0] @calls))))

  (it "draws the cached map layer before updated particles"
    (let [calls (atom [])
          particle-store (atom [{:id 1}])
          bounds {:top 44.0 :bottom 40.0 :left -90.0 :right -84.0}
          grid {:center [42.0 -87.0] :radius-nm 100}]
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
        (should-contain [:layer bounds 600 400 grid [{:airport "KORD"}]] @calls)
        (should-contain [:frame bounds grid 600 400] @calls)
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
          (should= 2 @renders))))))
