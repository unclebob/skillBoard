(ns skillBoard.presenters.flights-spec
  (:require
    [skillBoard.config :as config]
    [skillBoard.flight-schedule-pro :as fsp]
    [skillBoard.atoms :as atoms]
    [skillBoard.presenters.flights :as flights]
    [skillBoard.presenters.utils :as utils]
    [skillBoard.radar-cape :as radar-cape]
    [skillBoard.time-util :as time-util]
    [speclj.core :refer :all]))

(describe "presenter functions"
  (context "format-res"
    (it "formats a basic reservation"
      (should= {:line "05:50Z TAIL   LAS.P LAS.I        UGN0915309/010/100             ",
                :color config/scheduled-flight-color}
               (flights/format-res {:start-time time-util/epoch
                              :tail-number "TAIL"
                              :pilot-name ["PILOT" "LAST"]
                              :instructor-name ["INSTR" "LAST"]
                              :co nil
                              :altitude 1000
                              :ground-speed 100
                              :lat-lon [0 0]
                              :unscheduled? false
                              :on-ground? false
                              :adsb? false})
               )
      )
    (it "formats a on ground reservation"
      (should= {:line "05:50Z TAIL   LAS.P LAS.I        UGN000000/GND/000 RAMP         ",
                :color config/on-ground-color}
               (flights/format-res {:start-time time-util/epoch
                              :tail-number "TAIL"
                              :pilot-name ["PILOT" "LAST"]
                              :instructor-name ["INSTR" "LAST"]
                              :co nil
                              :altitude 0
                              :ground-speed 0
                              :lat-lon [42.4221486 -87.8679161]
                              :unscheduled? false
                              :on-ground? true
                              :adsb? true})
               )
      )

    (it "formats a taxi reservation"
      (should= {:line "05:50Z TAIL   LAS.P LAS.I        UGN000000/GND/010 TAXI         ",
                :color config/on-ground-color}
               (flights/format-res {:start-time time-util/epoch
                              :tail-number "TAIL"
                              :pilot-name ["PILOT" "LAST"]
                              :instructor-name ["INSTR" "LAST"]
                              :co nil
                              :altitude 0
                              :ground-speed 10
                              :lat-lon [42.4221486 -87.8679161]
                              :unscheduled? false
                              :on-ground? true
                              :adsb? true})
               )
      )

    (it "formats an unscheduled flight in the pattern"
      (should= {:line "05:50Z TAIL   LAS.P LAS.I        UGN000000/020/100 PATN NO-CO   ",
                :color config/unscheduled-flight-color}
               (flights/format-res {:start-time time-util/epoch
                              :tail-number "TAIL"
                              :pilot-name ["PILOT" "LAST"]
                              :instructor-name ["INSTR" "LAST"]
                              :co nil
                              :altitude 2000
                              :ground-speed 100
                              :lat-lon [42.4221486 -87.8679161]
                              :unscheduled? true
                              :on-ground? false
                              :adsb? true})
               )
      )

    (it "formats a reservation flying low"
      (should= {:line "05:50Z TAIL   LAS.P LAS.I        UGN000000/008/100 LOW          ",
                :color config/scheduled-flight-color}
               (flights/format-res {:start-time time-util/epoch
                              :tail-number "TAIL"
                              :pilot-name ["PILOT" "LAST"]
                              :instructor-name ["INSTR" "LAST"]
                              :co nil
                              :altitude 800
                              :ground-speed 100
                              :lat-lon [42.4221486 -87.8679161]
                              :unscheduled? false
                              :on-ground? false
                              :adsb? true})
               )
      )
    (it "formats a reservation flying near"
      (should= {:line "05:50Z TAIL   LAS.P LAS.I        UGN000000/030/100 NEAR         ",
                :color config/scheduled-flight-color}
               (flights/format-res {:start-time time-util/epoch
                              :tail-number "TAIL"
                              :pilot-name ["PILOT" "LAST"]
                              :instructor-name ["INSTR" "LAST"]
                              :co nil
                              :altitude 3000
                              :ground-speed 100
                              :lat-lon [42.4221486 -87.8679161]
                              :unscheduled? false
                              :on-ground? false
                              :adsb? true})
               )
      )

    (it "packs the adsb status into a map"
      (should= {"N419AM" {:tmp nil, :cat "A1", :dst nil, :spi false, :alr 0, :alt 4825, :ns 817072312, :vrt 640, :wsp nil, :src "A", :altg 5125, :tru 41, :uti 1750878586, :wdi nil, :org nil, :hex "A4F59B", :opr nil, :cou "USA ", :fli "N419AM", :gda "A", :dis 42.5, :lon -88.81367, :lla 0, :squ nil, :lat 42.51814, :spd 105, :reg "N419AM", :pic 11, :ava "A", :typ "DA40", :trk 119, :dbm -78}
                "N757HE" {:tmp nil, :cat "A1", :sil 3, :dst nil, :spi false, :alr 0, :alt 1600, :mop 2, :sda 2, :ns 566304636, :vrt -64, :wsp nil, :src "A", :tru 193, :uti 1750878547, :wdi nil, :org nil, :hex "AA34BE", :nacp 9, :opr nil, :cou "USA ", :fli "N757HE", :gda "a", :dis 9.0, :cla 4, :lon -88.06391, :lla 42, :squ nil, :lat 42.44133, :spd 97, :reg "N757HE", :pic 11, :ava "A", :typ "C152", :trk 263, :dbm nil}}
               (flights/make-adsb-tail-number-map
                 [{:tmp nil, :cat "A1", :dst nil, :spi false, :alr 0, :alt 4825, :ns 817072312, :vrt 640, :wsp nil, :src "A", :altg 5125, :tru 41, :uti 1750878586, :wdi nil, :org nil, :hex "A4F59B", :opr nil, :cou "USA ", :fli "N419AM", :gda "A", :dis 42.5, :lon -88.81367, :lla 0, :squ nil, :lat 42.51814, :spd 105, :reg "N419AM", :pic 11, :ava "A", :typ "DA40", :trk 119, :dbm -78}
                  {:tmp nil, :cat "A1", :sil 3, :dst nil, :spi false, :alr 0, :alt 1600, :mop 2, :sda 2, :ns 566304636, :vrt -64, :wsp nil, :src "A", :tru 193, :uti 1750878547, :wdi nil, :org nil, :hex "AA34BE", :nacp 9, :opr nil, :cou "USA ", :fli "N757HE", :gda "a", :dis 9.0, :cla 4, :lon -88.06391, :lla 42, :squ nil, :lat 42.44133, :spd 97, :reg "N757HE", :pic 11, :ava "A", :typ "C152", :trk 263, :dbm nil}])))
    )
  )

(describe "format-name"
  (it "formats normal names correctly: LAST.F → uppercased 3-letter last + dot + first initial"
    (should= "SMI.J" (flights/format-name ["John" "Smith"]))
    (should= "DOE.J" (flights/format-name ["Jane" "Doe"]))
    (should= "NGU.T" (flights/format-name ["Tuan" "Nguyen"])))

  (it "handles empty and blank strings gracefully"
    (should= "ALICE" (flights/format-name ["Alice" ""]))
    (should= "ROBER" (flights/format-name ["Robert" ""]))
    (should= "BOB  " (flights/format-name ["Bob" "   "]))
    (should= "     " (flights/format-name ["" "   "]))
    (should= "SMITH" (flights/format-name ["" "Smith"]))
    (should= "MARTI" (flights/format-name ["" "Martinez"]))
    (should= "LI .J" (flights/format-name ["John" "Li"]))
    (should= "SM .J" (flights/format-name ["Jane" "Sm"]))
    (should= "     " (flights/format-name [nil nil])))
  )

(describe "format-flight-screen"
  (it "formats an empty flight screen correctly"
    (with-redefs [utils/get-short-metar (fn [] {:line "METAR" :color config/info-color})
                  fsp/unpack-reservations (fn [_] [])
                  fsp/unpack-flights (fn [_] [])
                  fsp/sort-and-filter-reservations (fn [_ _] [])
                  flights/make-adsb-tail-number-map (fn [_] {})
                  radar-cape/update-with-adsb (fn [_ _] [])
                  radar-cape/include-unscheduled-flights (fn [_ _] [])
                  flights/format-res (fn [_] {:line "RES" :color config/in-fleet-color})
                  atoms/test? (atom false)
                  config/display-info (atom {:line-count 10})
                  config/cols 64]
      (let [blank-line (apply str (repeat 64 " "))
            expected (concat (repeat 8 {:line blank-line :color config/info-color})
                             [{:color config/info-color :line "             "}
                              {:line "METAR" :color config/info-color}])]
        (should= expected (flights/make-flights-screen [] [])))))

  (it "formats a flight screen with dropped items correctly"
    (with-redefs [utils/get-short-metar (fn [] {:line "METAR" :color config/info-color})
                  fsp/unpack-reservations (fn [_] [])
                  fsp/unpack-flights (fn [_] [])
                  fsp/sort-and-filter-reservations (fn [_ _] [])
                  flights/make-adsb-tail-number-map (fn [_] {})
                  radar-cape/update-with-adsb (fn [_ _] [])
                  radar-cape/include-unscheduled-flights (fn [_ _] (repeat 10 {}))
                  flights/format-res (fn [_] {:line "RES" :color config/in-fleet-color})
                  atoms/test? (atom false)
                  config/display-info (atom {:line-count 10})
                  config/cols 64]
      (let [expected (concat (repeat 8 {:line "RES" :color config/in-fleet-color})
                             [{:color config/info-color :line "... 2 MORE..."}
                              {:line "METAR" :color config/info-color}])]
        (should= expected (flights/make-flights-screen [] [])))))
)