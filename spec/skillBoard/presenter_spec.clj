(ns skillBoard.presenter-spec
  (:require
    [skillBoard.config :as config]
    [skillBoard.flight-schedule-pro :as fsp]
    [skillBoard.presenter :as p]
    [skillBoard.radar-cape :as radar-cape]
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

    (it "packs the adsb status into a map"
      (should= {"N419AM" {:tmp nil, :cat "A1", :dst nil, :spi false, :alr 0, :alt 4825, :ns 817072312, :vrt 640, :wsp nil, :src "A", :altg 5125, :tru 41, :uti 1750878586, :wdi nil, :org nil, :hex "A4F59B", :opr nil, :cou "USA ", :fli "N419AM", :gda "A", :dis 42.5, :lon -88.81367, :lla 0, :squ nil, :lat 42.51814, :spd 105, :reg "N419AM", :pic 11, :ava "A", :typ "DA40", :trk 119, :dbm -78}
                "N757HE" {:tmp nil, :cat "A1", :sil 3, :dst nil, :spi false, :alr 0, :alt 1600, :mop 2, :sda 2, :ns 566304636, :vrt -64, :wsp nil, :src "A", :tru 193, :uti 1750878547, :wdi nil, :org nil, :hex "AA34BE", :nacp 9, :opr nil, :cou "USA ", :fli "N757HE", :gda "a", :dis 9.0, :cla 4, :lon -88.06391, :lla 42, :squ nil, :lat 42.44133, :spd 97, :reg "N757HE", :pic 11, :ava "A", :typ "C152", :trk 263, :dbm nil}}
               (p/make-adsb-tail-number-map
                 [{:tmp nil, :cat "A1", :dst nil, :spi false, :alr 0, :alt 4825, :ns 817072312, :vrt 640, :wsp nil, :src "A", :altg 5125, :tru 41, :uti 1750878586, :wdi nil, :org nil, :hex "A4F59B", :opr nil, :cou "USA ", :fli "N419AM", :gda "A", :dis 42.5, :lon -88.81367, :lla 0, :squ nil, :lat 42.51814, :spd 105, :reg "N419AM", :pic 11, :ava "A", :typ "DA40", :trk 119, :dbm -78}
                  {:tmp nil, :cat "A1", :sil 3, :dst nil, :spi false, :alr 0, :alt 1600, :mop 2, :sda 2, :ns 566304636, :vrt -64, :wsp nil, :src "A", :tru 193, :uti 1750878547, :wdi nil, :org nil, :hex "AA34BE", :nacp 9, :opr nil, :cou "USA ", :fli "N757HE", :gda "a", :dis 9.0, :cla 4, :lon -88.06391, :lla 42, :squ nil, :lat 42.44133, :spd 97, :reg "N757HE", :pic 11, :ava "A", :typ "C152", :trk 263, :dbm nil}])))
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

    (it "handles corrected TAFs correctly"
      (should= [{:line "TAF COR KORD", :color :white}
                {:line "121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250", :color :white}
                {:line "BECMG131200 22005KT P6SM SCT250", :color :white}]
               (p/split-taf
                 (str
                   "TAF COR KORD 121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250 "
                   "BECMG131200 22005KT P6SM SCT250"))))
    )

  (context "make-flight-category-line"
    (it "formats a complete metar correctly"
      (should= {:line "KORD  VFR CLR        10 15   ", :color :white}
               (p/make-flight-category-line {:fltCat "VFR" :icaoId "KORD" :visib "10" :cover "CLR" :clouds [] :wspd 15 :wgst nil})))

    (it "handles nil values correctly"
      (should= {:line "KORD      CLR        10 15G20", :color :white}
               (p/make-flight-category-line {:fltCat nil :icaoId "KORD" :visib "10" :cover "CLR" :clouds [] :wspd 15 :wgst 20})))

    (it "handles non-CLR cover with cloud base"
      (should= {:line "KORD  IFR BKN   030   5 10G15", :color :white}
               (p/make-flight-category-line {:fltCat "IFR" :icaoId "KORD" :visib "5" :cover "BKN" :clouds [{:base "030"}] :wspd 10 :wgst 15})))

    (it "handles nil cover"
      (should= {:line "KORD  IFR       050   5 10G15", :color :white}
               (p/make-flight-category-line {:fltCat "IFR" :icaoId "KORD" :visib "5" :cover nil :clouds [{:base "050"}] :wspd 10 :wgst 15})))

    (it "handles nil base"
      (should= {:line "KORD  IFR OVC         5 10G15", :color :white}
               (p/make-flight-category-line {:fltCat "IFR" :icaoId "KORD" :visib "5" :cover "OVC" :clouds [] :wspd 10 :wgst 15})))
    )
  )

(describe "format-name"
  (it "formats normal names correctly: LAST.F → uppercased 3-letter last + dot + first initial"
    (should= "SMI.J" (p/format-name ["John" "Smith"]))
    (should= "DOE.J" (p/format-name ["Jane" "Doe"]))
    (should= "NGU.T" (p/format-name ["Tuan" "Nguyen"])))

  (it "handles empty and blank strings gracefully"
    (should= "ALICE" (p/format-name ["Alice" ""]))
    (should= "ROBER" (p/format-name ["Robert" ""]))
    (should= "BOB  " (p/format-name ["Bob" "   "]))
    (should= "     " (p/format-name ["" "   "]))
    (should= "SMITH" (p/format-name ["" "Smith"]))
    (should= "MARTI" (p/format-name ["" "Martinez"]))
    (should= "LI .J" (p/format-name ["John" "Li"]))
    (should= "SM .J" (p/format-name ["Jane" "Sm"]))
    (should= "     " (p/format-name [nil nil])))
  )

(describe "shorten-metar"
  (it "returns NO-METAR for nil input"
    (should= {:line "NO-METAR" :color :white} (p/shorten-metar nil)))

  (it "shortens METAR without RMK"
    (should= {:line "KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014" :color :white}
             (p/shorten-metar "METAR KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014")))

  (it "shortens METAR with RMK, taking before RMK"
    (should= {:line "KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014" :color :white}
             (p/shorten-metar "METAR KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014 RMK AO2 SLP221 T02440044")))

  (it "truncates if the shortened METAR is longer than 64 characters"
    (let [long-part (apply str (repeat 70 "A"))
          metar-text (str "METAR " long-part)]
      (should= {:line (subs long-part 0 64) :color :white} (p/shorten-metar metar-text))))
)

(describe "format-flight-screen"
  (it "formats an empty flight screen correctly"
    (with-redefs [p/get-short-metar (fn [] {:line "METAR" :color :white})
                  fsp/unpack-reservations (fn [_] [])
                  fsp/unpack-flights (fn [_] [])
                  fsp/sort-and-filter-reservations (fn [_ _] [])
                  p/make-adsb-tail-number-map (fn [_] {})
                  radar-cape/update-with-adsb (fn [_ _] [])
                  radar-cape/include-unreserved-flights (fn [_ _] [])
                  p/format-res (fn [_] {:line "RES" :color :white})
                  config/display-info (atom {:line-count 10})
                  config/cols 64]
      (let [blank-line (apply str (repeat 64 " "))
            expected (concat (repeat 8 {:line blank-line :color :white})
                             [{:color :white :line "             "} {:line "METAR" :color :white}])]
        (should= expected (p/format-flight-screen [] [])))))

  (it "formats a flight screen with dropped items correctly"
    (with-redefs [p/get-short-metar (fn [] {:line "METAR" :color :white})
                  fsp/unpack-reservations (fn [_] [])
                  fsp/unpack-flights (fn [_] [])
                  fsp/sort-and-filter-reservations (fn [_ _] [])
                  p/make-adsb-tail-number-map (fn [_] {})
                  radar-cape/update-with-adsb (fn [_ _] [])
                  radar-cape/include-unreserved-flights (fn [_ _] (repeat 10 {}))
                  p/format-res (fn [_] {:line "RES" :color :white})
                  config/display-info (atom {:line-count 10})
                  config/cols 64]
      (let [expected (concat (repeat 8 {:line "RES" :color :white})
                             [{:color :white :line "... 2 MORE..."} {:line "METAR" :color :white}])]
        (should= expected (p/format-flight-screen [] [])))))

  )