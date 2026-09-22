(ns skillBoard.presenters.utils-spec
  (:require
    [skillBoard.presenters.utils :as utils]
    [skillBoard.domain.navigation :as nav]
    [skillBoard.foundation.config :as config]
    [speclj.core :refer :all]))

(def ref-lat 42)
(def ref-lon -87)

(describe "flight-category-color"
  (it "uses the visibility and ceiling boundaries"
    (should= config/vfr-color (utils/flight-category-color 5.0 3000))
    (should= config/mvfr-color (utils/flight-category-color 4.9 3000))
    (should= config/mvfr-color (utils/flight-category-color 5.0 2999))
    (should= config/mvfr-color (utils/flight-category-color 3.0 1000))
    (should= config/ifr-color (utils/flight-category-color 2.9 1000))
    (should= config/ifr-color (utils/flight-category-color 3.0 999))
    (should= config/ifr-color (utils/flight-category-color 3.0 500))
    (should= config/ifr-color (utils/flight-category-color 1.0 500))
    (should= config/lifr-color (utils/flight-category-color 0.9 500))
    (should= config/lifr-color (utils/flight-category-color 1.0 499))))

(describe "by-distance"
  (it "orders closer metars first and ignores a missing distance"
    (with-redefs [nav/dist-and-bearing (fn [_ _ lat _]
                                         {:distance (when-not (neg? lat) lat)})]
      (should (utils/by-distance {:lat 1.0 :lon 0} {:lat 2.0 :lon 0}))
      (should-not (utils/by-distance {:lat 2.0 :lon 0} {:lat 1.0 :lon 0}))
      (should-not (utils/by-distance {:lat 2.0 :lon 0} {:lat 2.0 :lon 0}))
      (should-not (utils/by-distance {:lat -1.0 :lon 0} {:lat 2.0 :lon 0}))
      (should-not (utils/by-distance {:lat 1.0 :lon 0} {:lat -2.0 :lon 0}))))

  (it "calls nav/dist-and-bearing with correct arguments"
    (let [calls (atom [])
          [airport-lat airport-lon] config/airport-lat-lon
          metar1 {:lat 10.0 :lon 20.0}
          metar2 {:lat 30.0 :lon 40.0}]
      (with-redefs [nav/dist-and-bearing (fn [& args]
                                           (swap! calls conj args)
                                           {:distance 1.0})]  ; dummy return
        (utils/by-distance metar1 metar2)
        (should= [[airport-lat airport-lon 10.0 20.0]
                  [airport-lat airport-lon 30.0 40.0]] @calls)))))

(describe "get-short-metar"
  (it "calls shorten-metar with the correct metar"
    (let [calls (atom [])
          airport "KJFK"
          metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014"}]
      (with-redefs [utils/shorten-metar (fn [m]
                                          (swap! calls conj m)
                                          {:line "shortened" :color :white})]
        (utils/get-short-metar {airport metar} airport)
        (should= [metar] @calls)))))

(describe "shorten-metar"
  (it "returns NO-METAR for nil input"
    (should= {:line "NO-METAR" :color :white} (utils/shorten-metar nil)))

  (it "shortens METAR without RMK"
    (should= {:line "METAR KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014" :color :white}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014"})))

  (it "shortens METAR with RMK, taking before RMK"
    (should= {:line "METAR KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014" :color :white}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014 RMK AO2 SLP221 T02440044"})))

  (it "keeps a METAR that is exactly one line"
    (let [exact (apply str (repeat config/cols "A"))]
      (should= {:line exact :color :white} (utils/shorten-metar {:rawOb exact}))))

  (it "truncates if the shortened METAR is longer than 64 characters"
    (let [long-part (apply str (repeat 70 "A"))]
      (should= {:line (subs long-part 0 64) :color :white} (utils/shorten-metar {:rawOb long-part}))))

  (it "colors VFR green"
    (should= {:line "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :color :green}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :fltCat "VFR"})))

  (it "colors MVFR blue"
    (should= {:line "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :color :blue}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :fltCat "MVFR"})))

  (it "colors IFR red"
    (should= {:line "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :color :red}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :fltCat "IFR"})))

  (it "colors LIFR magenta"
    (should= {:line "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :color :magenta}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :fltCat "LIFR"})))

  (it "colors unknown fltCat white"
    (should= {:line "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :color :white}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :fltCat "UNKNOWN"})))

  (it "colors nil fltCat white"
    (should= {:line "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :color :white}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :fltCat nil}))))

(describe "find-location"
  (it "handles degenerate cases"
    (should= "" (utils/find-location 0 0 0 [])))

  (it "returns no position when outside of geo-fences"
    (should= "" (utils/find-location ref-lat ref-lon 1700
                                 [
                                  {:lat (inc ref-lat)
                                   :lon (inc ref-lon)
                                   :radius 1
                                   :min-alt 720
                                   :max-alt 2000
                                   :name "NAME"}]))
    (should= "" (utils/find-location ref-lat ref-lon 3000
                                 [
                                  {:lat ref-lat
                                   :lon ref-lon
                                   :radius 1
                                   :min-alt 720
                                   :max-alt 2000
                                   :name "NAME"}]))
    (should= "" (utils/find-location ref-lat ref-lon 700
                                 [
                                  {:lat ref-lat
                                   :lon ref-lon
                                   :radius 1
                                   :min-alt 1000
                                   :max-alt 2000
                                   :name "NAME"}]))
    )
  (it "includes an aircraft on the fence boundary"
    (should= "EDGE" (utils/find-location ref-lat ref-lon 1000
                                         [{:lat ref-lat
                                           :lon ref-lon
                                           :radius 0
                                           :min-alt 1000
                                           :max-alt 1000
                                           :name "EDGE"}])))

  (it "finds first geofence"
    (should= "NAME" (utils/find-location ref-lat ref-lon 1100
                                     [
                                      {:lat ref-lat
                                       :lon ref-lon
                                       :radius 1
                                       :min-alt 1000
                                       :max-alt 2000
                                       :name "NAME"}]))

    (should= "NAME" (utils/find-location ref-lat ref-lon 1100
                                     [
                                      {:lat ref-lat
                                       :lon ref-lon
                                       :radius 1
                                       :min-alt 720
                                       :max-alt 1000
                                       :name "NO"}
                                      {:lat ref-lat
                                       :lon ref-lon
                                       :radius 1
                                       :min-alt 1099
                                       :max-alt 2000
                                       :name "NAME"}]))
    (should= "NAME" (utils/find-location ref-lat ref-lon 1200
                                     [
                                      {:lat ref-lat
                                       :lon ref-lon
                                       :radius 1
                                       :min-alt 720
                                       :max-alt 1000
                                       :name "NO"}
                                      {:lat (inc ref-lat)
                                       :lon (inc ref-lon)
                                       :radius 100
                                       :min-alt 1099
                                       :max-alt 2000
                                       :name "NAME"}])))
  )
(describe "generate-position-remark"
  (it "returns RAMP for nearby on-ground low speed"
    (with-redefs [config/airport-elevation 100
                  config/pattern-altitude 2000]
      (should= "RAMP" (utils/generate-position-remark 1 105 1 true 42 -87))))

  (it "returns TAXI for nearby on-ground medium speed"
    (with-redefs [config/airport-elevation 100
                  config/pattern-altitude 2000]
      (should= "TAXI" (utils/generate-position-remark 1 105 15 true 42 -87))))

  (it "returns LOW for flying below pattern altitude"
    (with-redefs [config/airport-elevation 100
                  config/pattern-altitude 2000]
      (should= "LOW " (utils/generate-position-remark 3 135 60 false 42 -87))))

  (it "returns PATN for nearby flying in pattern"
    (with-redefs [config/airport-elevation 100
                  config/pattern-altitude 2000]
      (should= "PATN" (utils/generate-position-remark 1 2150 60 false 42 -87))))

  (it "returns NEAR for close aircraft"
    (with-redefs [config/airport-elevation 100
                  config/pattern-altitude 2000]
      (should= "NEAR" (utils/generate-position-remark 3 3000 60 false 42 -87))))

  (it "returns nothing when position data or coordinates are missing"
    (should= "" (utils/generate-position-remark nil 100 10 false 42 -87))
    (with-redefs [utils/find-location (fn [& _] "LOCATION")]
      (should= "" (utils/generate-position-remark 10 5000 10 false nil nil))))

  (it "treats the speed and distance cutoffs as the lower category"
    (with-redefs [config/airport-elevation 100
                  config/pattern-altitude 2000
                  config/geofences []]
      (should= "TAXI" (utils/generate-position-remark 1 105 2 true 42 -87))
      (should= "TAXI" (utils/generate-position-remark 1 105 25 true 42 -87))
      (should= "NEAR" (utils/generate-position-remark 3 120 60 false 42 -87))
      (should= "NEAR" (utils/generate-position-remark 2 2150 60 false 42 -87))
      (should= "NEAR" (utils/generate-position-remark 3 135 50 false 42 -87))
      (should= "NEAR" (utils/generate-position-remark 3 130 60 false 42 -87))
      (should= "NEAR" (utils/generate-position-remark 1 1500 60 false 42 -87))
      (should= "NEAR" (utils/generate-position-remark 2 105 1 true 42 -87))))

  (it "returns location for distant aircraft"
    (with-redefs [config/airport-elevation 100
                  config/pattern-altitude 2000
                  config/geofences []
                  utils/find-location (fn [_ _ _ _] "LOCATION")]
      (should= "LOCATION" (utils/generate-position-remark 7 3000 60 false 42 -87))))
  )
