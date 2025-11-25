(ns skillBoard.presenter-spec
  (:require [skillBoard.presenter :as p]
            [skillBoard.time-util :as time-util]
            [speclj.core :refer :all]
            ))

(def ref-lat 42)
(def ref-lon -87)

(describe "find-location"
  (it "handles degenerate cases"
    (should= "" (p/find-location 0 0 0 [])))

  (it "returns no position when outside of geo-fences"
    (should= "" (p/find-location ref-lat ref-lon 1700
                                 [
                                  {:lat (inc ref-lat)
                                   :lon (inc ref-lon)
                                   :radius 1
                                   :min-alt 720
                                   :max-alt 2000
                                   :name "NAME"}]))
    (should= "" (p/find-location ref-lat ref-lon 3000
                                 [
                                  {:lat ref-lat
                                   :lon ref-lon
                                   :radius 1
                                   :min-alt 720
                                   :max-alt 2000
                                   :name "NAME"}]))
    (should= "" (p/find-location ref-lat ref-lon 700
                                 [
                                  {:lat ref-lat
                                   :lon ref-lon
                                   :radius 1
                                   :min-alt 1000
                                   :max-alt 2000
                                   :name "NAME"}]))
    )
  (it "finds first geofence"
    (should= "NAME" (p/find-location ref-lat ref-lon 1100
                                     [
                                      {:lat ref-lat
                                       :lon ref-lon
                                       :radius 1
                                       :min-alt 1000
                                       :max-alt 2000
                                       :name "NAME"}]))

    (should= "NAME" (p/find-location ref-lat ref-lon 1100
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
    (should= "NAME" (p/find-location ref-lat ref-lon 1200
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

(describe "presenter functions"
  (context "format-res"
    (it "formats a basic reservation"
      (should= {:line "05:50Z TAIL   LAS.P LAS.I        UGN0915309/GND/100             ",
                :color :white}
               (p/format-res {:start-time time-util/epoch
                              :tail-number "TAIL"
                              :pilot-name ["PILOT" "LAST"]
                              :instructor-name ["INSTR" "LAST"]
                              :co nil
                              :altitude 1000
                              :ground-speed 100
                              :lat-lon [0 0]
                              :rogue? false
                              :on-ground? false?
                              :adsb? false})
               )
      )
    (it "formats a on ground reservation"
      (should= {:line "05:50Z TAIL   LAS.P LAS.I        UGN000000/GND/000 RAMP         ",
                :color :green}
               (p/format-res {:start-time time-util/epoch
                              :tail-number "TAIL"
                              :pilot-name ["PILOT" "LAST"]
                              :instructor-name ["INSTR" "LAST"]
                              :co nil
                              :altitude 0
                              :ground-speed 0
                              :lat-lon [42.4221486 -87.8679161]
                              :rogue? false
                              :on-ground? true
                              :adsb? true})
               )
      )

    (it "formats a taxi reservation"
      (should= {:line "05:50Z TAIL   LAS.P LAS.I        UGN000000/GND/010 TAXI         ",
                :color :green}
               (p/format-res {:start-time time-util/epoch
                              :tail-number "TAIL"
                              :pilot-name ["PILOT" "LAST"]
                              :instructor-name ["INSTR" "LAST"]
                              :co nil
                              :altitude 0
                              :ground-speed 10
                              :lat-lon [42.4221486 -87.8679161]
                              :rogue? false
                              :on-ground? true
                              :adsb? true})
               )
      )

    (it "formats a rogue (non-checked-out) reservation in the pattern"
      (should= {:line "05:50Z TAIL   LAS.P LAS.I        UGN000000/020/100 PATN NO-CO   ",
                :color :blue}
               (p/format-res {:start-time time-util/epoch
                              :tail-number "TAIL"
                              :pilot-name ["PILOT" "LAST"]
                              :instructor-name ["INSTR" "LAST"]
                              :co nil
                              :altitude 2000
                              :ground-speed 100
                              :lat-lon [42.4221486 -87.8679161]
                              :rogue? true
                              :on-ground? false
                              :adsb? true})
               )
      )

    (it "formats a reservation flying low"
      (should= {:line "05:50Z TAIL   LAS.P LAS.I        UGN000000/008/100 LOW          ",
                :color :white}
               (p/format-res {:start-time time-util/epoch
                              :tail-number "TAIL"
                              :pilot-name ["PILOT" "LAST"]
                              :instructor-name ["INSTR" "LAST"]
                              :co nil
                              :altitude 800
                              :ground-speed 100
                              :lat-lon [42.4221486 -87.8679161]
                              :rogue? false
                              :on-ground? false
                              :adsb? true})
               )
      )
    (it "formats a reservation flying near"
      (should= {:line "05:50Z TAIL   LAS.P LAS.I        UGN000000/030/100 NEAR         ",
                :color :white}
               (p/format-res {:start-time time-util/epoch
                              :tail-number "TAIL"
                              :pilot-name ["PILOT" "LAST"]
                              :instructor-name ["INSTR" "LAST"]
                              :co nil
                              :altitude 3000
                              :ground-speed 100
                              :lat-lon [42.4221486 -87.8679161]
                              :rogue? false
                              :on-ground? false
                              :adsb? true})
               )
      )
    )

  (context "split-taf"
    (it "splits normal TAF strings correctly"
      (should= [{:line "TAF KORD", :color :white}
                {:line "121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250", :color :white}
                {:line "TEMPO 1212/1216 5SM -RA BR OVC015", :color :white}
                {:line "PROB30 121600 25012KT P6SM BKN020", :color :white}
                {:line "PROB40 TEMPO 130000 24008KT P6SM SCT030 BKN200", :color :white}
                {:line "BECMG131200 22005KT P6SM SCT250", :color :white}
                {:line "FM141200 22005KT P6SM SCT250", :color :white}]
               (p/split-taf
                 (str
                   "TAF KORD 121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250 "
                   "TEMPO 1212/1216 5SM -RA BR OVC015 PROB30 121600 25012KT P6SM BKN020 "
                   "PROB40 TEMPO 130000 24008KT P6SM SCT030 BKN200 "
                   "BECMG131200 22005KT P6SM SCT250 "
                   "FM141200 22005KT P6SM SCT250"))))
    (it "handles amended TAFs correctly"
      (should= [{:line "TAF AMD KORD", :color :white}
                {:line "121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250", :color :white}
                {:line "FM141200 22005KT P6SM SCT250", :color :white}]
               (p/split-taf
                 (str
                   "TAF AMD KORD 121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250 "
                   "FM141200 22005KT P6SM SCT250"))))
    )
  )