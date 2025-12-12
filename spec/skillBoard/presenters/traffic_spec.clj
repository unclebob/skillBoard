(ns skillBoard.presenters.traffic-spec
  (:require
    [skillBoard.presenters.traffic :as traffic]
    [skillBoard.atoms :as atoms]
    [skillBoard.config :as config]
    [skillBoard.comm-utils :as comm]
    [skillBoard.presenters.utils :as utils]
    [skillBoard.navigation :as nav]
    [speclj.core :refer :all]))

(describe "make-traffic-screen"

  (it "generates RAMP remark for nearby on-ground low speed aircraft"
    (with-redefs [atoms/test? (atom false)
                  comm/polled-nearby-adsbs (atom [])
                  comm/polled-aircraft (atom [])
                  config/display-info (atom {:line-count 10})
                  config/airport-lat-lon [42.0 -87.0]
                  config/airport-elevation 100
                  config/bearing-center "C"
                  config/geofences []
                  utils/get-short-metar (fn [] {:line "METAR" :color config/info-color})
                  utils/find-location (fn [_ _ _ _] "LOCATION")
                  nav/dist-and-bearing (fn [_ _ lat _lon]
                                         (let [distance (abs (- lat 42.0))] ; simplify: distance = |lat - 42|
                                           {:distance distance :bearing 0}))]
      (let [adsb [{:fli "N12345" :lat 45.0 :lon -87.0 :alt 105 :spd 1}]
            scheduled []
            result (traffic/make-traffic-screen adsb scheduled)
            expected-line "N12345   C000003/GND/001  RAMP    "]
        (should= expected-line (:line (first result)))
        (should= config/out-of-fleet-color (:color (first result))))))

  (it "generates TAXI remark for nearby on-ground medium speed aircraft"
    (with-redefs [atoms/test? (atom false)
                  comm/polled-nearby-adsbs (atom [])
                  comm/polled-aircraft (atom [])
                  config/display-info (atom {:line-count 10})
                  config/airport-lat-lon [42.0 -87.0]
                  config/airport-elevation 100
                  config/bearing-center "C"
                  config/geofences []
                  utils/get-short-metar (fn [] {:line "METAR" :color config/info-color})
                  utils/find-location (fn [_ _ _ _] "LOCATION")
                  nav/dist-and-bearing (fn [_ _ lat _lon]
                                         (let [distance (abs (- lat 42.0))]
                                           {:distance distance :bearing 0}))]
      (let [adsb [{:fli "N12345" :lat 44.0 :lon -87.0 :alt 105 :spd 10}]
            scheduled []
            result (traffic/make-traffic-screen adsb scheduled)
            expected-line "N12345   C000002/GND/010  TAXI    "]
        (should= expected-line (:line (first result)))
        (should= config/out-of-fleet-color (:color (first result))))))

  (it "generates NEAR remark for close aircraft"
    (with-redefs [atoms/test? (atom false)
                  comm/polled-nearby-adsbs (atom [])
                  comm/polled-aircraft (atom [])
                  config/display-info (atom {:line-count 10})
                  config/airport-lat-lon [42.0 -87.0]
                  config/airport-elevation 100
                  config/bearing-center "C"
                  config/geofences []
                  utils/get-short-metar (fn [] {:line "METAR" :color config/info-color})
                  utils/find-location (fn [_ _ _ _] "LOCATION")
                  nav/dist-and-bearing (fn [_ _ lat _lon]
                                         (let [distance (abs (- lat 42.0))]
                                           {:distance distance :bearing 0}))]
      (let [adsb [{:fli "N12345" :lat 43.5 :lon -87.0 :alt 3000 :spd 100}]
            scheduled []
            result (traffic/make-traffic-screen adsb scheduled)
            expected-line "N12345   C000002/030/100  NEAR    "]
        (should= expected-line (:line (first result)))
        (should= config/out-of-fleet-color (:color (first result))))))

   (it "generates LOW remark for flying aircraft below pattern altitude"
     (with-redefs [atoms/test? (atom false)
                   comm/polled-nearby-adsbs (atom [])
                   comm/polled-aircraft (atom [])
                   config/display-info (atom {:line-count 10})
                   config/airport-lat-lon [42.0 -87.0]
                   config/airport-elevation 100
                   config/pattern-altitude 2000
                   config/bearing-center "C"
                   config/geofences []
                   utils/get-short-metar (fn [] {:line "METAR" :color config/info-color})
                   utils/find-location (fn [_ _ _ _] "LOCATION")
                   nav/dist-and-bearing (fn [_ _ lat _lon]
                                          (let [distance (abs (- lat 42.0))]
                                            {:distance distance :bearing 0}))]
       (let [adsb [{:fli "N12345" :lat 49.0 :lon -87.0 :alt 1400 :spd 100}]
             scheduled []
             result (traffic/make-traffic-screen adsb scheduled)
             expected-line "N12345   C000007/014/100  LOW     "]
         (should= expected-line (:line (first result)))
         (should= config/out-of-fleet-color (:color (first result))))))

   (it "generates PATN remark for nearby flying aircraft in pattern"
     (with-redefs [atoms/test? (atom false)
                   comm/polled-nearby-adsbs (atom [])
                   comm/polled-aircraft (atom [])
                   config/display-info (atom {:line-count 10})
                   config/airport-lat-lon [42.0 -87.0]
                   config/airport-elevation 100
                   config/pattern-altitude 2000
                   config/bearing-center "C"
                   config/geofences []
                   utils/get-short-metar (fn [] {:line "METAR" :color config/info-color})
                   utils/find-location (fn [_ _ _ _] "LOCATION")
                   nav/dist-and-bearing (fn [_ _ lat _lon]
                                          (let [distance (abs (- lat 42.0))]
                                            {:distance distance :bearing 0}))]
       (let [adsb [{:fli "N12345" :lat 44.0 :lon -87.0 :alt 2200 :spd 100}]
             scheduled []
             result (traffic/make-traffic-screen adsb scheduled)
             expected-line "N12345   C000002/022/100  PATN    "]
         (should= expected-line (:line (first result)))
         (should= config/out-of-fleet-color (:color (first result))))))

  (it "uses GND for aircraft with gda flag G"
    (with-redefs [atoms/test? (atom false)
                  comm/polled-nearby-adsbs (atom [])
                  comm/polled-aircraft (atom [])
                  config/display-info (atom {:line-count 10})
                  config/airport-lat-lon [42.0 -87.0]
                  config/airport-elevation 100
                  config/bearing-center "C"
                  config/geofences []
                  utils/get-short-metar (fn [] {:line "METAR" :color config/info-color})
                  utils/find-location (fn [_ _ _ _] "LOCATION")
                  nav/dist-and-bearing (fn [_ _ lat _lon]
                                         (let [distance (abs (- lat 42.0))]
                                           {:distance distance :bearing 0}))]
      (let [adsb [{:fli "N12345" :lat 43.5 :lon -87.0 :alt config/airport-elevation :spd 1 :gda "G"}]
            scheduled []
            result (traffic/make-traffic-screen adsb scheduled)
            expected-line "N12345   C000002/GND/001  RAMP    "]
        (should= expected-line (:line (first result)))
        (should= config/out-of-fleet-color (:color (first result))))))

  (it "generates location remark for distant aircraft"
    (with-redefs [atoms/test? (atom false)
                  comm/polled-nearby-adsbs (atom [])
                  comm/polled-aircraft (atom [])
                  config/display-info (atom {:line-count 10})
                  config/airport-lat-lon [42.0 -87.0]
                  config/airport-elevation 100
                  config/bearing-center "C"
                  config/geofences []
                  utils/get-short-metar (fn [] {:line "METAR" :color config/info-color})
                  utils/find-location (fn [_ _ _ _] "LOCATION")
                  nav/dist-and-bearing (fn [_ _ lat _lon]
                                         (let [distance (abs (- lat 42.0))]
                                           {:distance distance :bearing 0}))]
      (let [adsb [{:fli "N12345" :lat 48.0 :lon -87.0 :alt 2000 :spd 100}]
            scheduled []
            result (traffic/make-traffic-screen adsb scheduled)
            expected-line "N12345   C000006/020/100  LOCATION"]
        (should= expected-line (:line (first result)))
        (should= config/out-of-fleet-color (:color (first result))))))

  (it "uses on-ground color for on-ground aircraft"
    (with-redefs [atoms/test? (atom false)
                  comm/polled-nearby-adsbs (atom [])
                  comm/polled-aircraft (atom ["N12345"])
                  config/display-info (atom {:line-count 10})
                  config/airport-lat-lon [42.0 -87.0]
                  config/airport-elevation 100
                  config/bearing-center "C"
                  config/geofences []
                  utils/get-short-metar (fn [] {:line "METAR" :color config/info-color})
                  utils/find-location (fn [_ _ _ _] "LOCATION")
                  nav/dist-and-bearing (fn [_ _ lat _lon]
                                         (let [distance (abs (- lat 42.0))]
                                           {:distance distance :bearing 0}))]
      (let [adsb [{:fli "N12345" :lat 45.0 :lon -87.0 :alt 105 :spd 1}]
            scheduled ["N12345"]
            result (traffic/make-traffic-screen adsb scheduled)]
        (should= config/on-ground-color (:color (first result))))))

  (it "uses in-fleet color for in-fleet aircraft"
    (with-redefs [atoms/test? (atom false)
                  comm/polled-nearby-adsbs (atom [])
                  comm/polled-aircraft (atom ["N12345"])
                  config/display-info (atom {:line-count 10})
                  config/airport-lat-lon [42.0 -87.0]
                  config/airport-elevation 100
                  config/bearing-center "C"
                  config/geofences []
                  utils/get-short-metar (fn [] {:line "METAR" :color config/info-color})
                  utils/find-location (fn [_ _ _ _] "LOCATION")
                  nav/dist-and-bearing (fn [_ _ lat _lon]
                                         (let [distance (abs (- lat 42.0))]
                                           {:distance distance :bearing 0}))]
      (let [adsb [{:fli "N12345" :lat 45.0 :lon -87.0 :alt 1000 :spd 100}]
            scheduled ["N12345"]
            result (traffic/make-traffic-screen adsb scheduled)]
        (should= config/in-fleet-color (:color (first result))))))

  (it "sorts aircraft by distance"
    (with-redefs [atoms/test? (atom false)
                  comm/polled-nearby-adsbs (atom [])
                  comm/polled-aircraft (atom [])
                  config/display-info (atom {:line-count 10})
                  config/airport-lat-lon [42.0 -87.0]
                  config/airport-elevation 100
                  config/bearing-center "C"
                  config/geofences []
                  utils/get-short-metar (fn [] {:line "METAR" :color :white})
                  utils/find-location (fn [_ _ _ _] "LOCATION")
                  nav/dist-and-bearing (fn [_ _ lat _lon]
                                         (let [distance (abs (- lat 42.0))]
                                           {:distance distance :bearing 0}))]
      (let [adsb [{:fli "N1" :lat 47.0 :lon -87.0 :alt 2000 :spd 100} ; dist 5
                  {:fli "N2" :lat 43.0 :lon -87.0 :alt 2000 :spd 100} ; dist 1
                  {:fli "N3" :lat 45.0 :lon -87.0 :alt 2000 :spd 100}] ; dist 3
            scheduled []
            result (traffic/make-traffic-screen adsb scheduled)]
        (should= "N2" (subs (:line (first result)) 0 2))
        (should= "N3" (subs (:line (second result)) 0 2))
        (should= "N1" (subs (:line (nth result 2)) 0 2)))))

  (it "pads with blank lines and adds metar"
    (with-redefs [atoms/test? (atom false)
                  comm/polled-nearby-adsbs (atom [])
                  comm/polled-aircraft (atom [])
                  config/display-info (atom {:line-count 10})
                  config/airport-lat-lon [42.0 -87.0]
                  config/airport-elevation 100
                  config/bearing-center "C"
                  config/geofences []
                  utils/get-short-metar (fn [] {:line "METAR" :color :white})
                  utils/find-location (fn [_ _ _ _] "LOCATION")
                  nav/dist-and-bearing (fn [_ _ lat _lon]
                                         (let [distance (abs (- lat 42.0))]
                                           {:distance distance :bearing 0}))]
      (let [adsb [{:fli "N1" :lat 43.0 :lon -87.0 :alt 2000 :spd 100}]
            scheduled []
            result (traffic/make-traffic-screen adsb scheduled)]
        (should= 10 (count result))
        (should= "" (:line (nth result 1))) ; blank
        (should= "METAR" (:line (last result))))))

  ; Add more test cases here
)