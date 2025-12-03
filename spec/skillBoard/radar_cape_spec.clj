(ns skillBoard.radar-cape-spec
  (:require
    [java-time.api :as time]
    [skillBoard.radar-cape :as rc]
    [skillBoard.sources :as sources]
    [skillBoard.test-source :as stubs]
    [speclj.core :refer :all]))

(declare start-time co now)

(describe "Radar Cape"
  (context "fetching adsb raw status from a stub source"
    (it "fetches the ADSB raw status"
      (should= [{:tmp nil, :cat "A1", :dst nil, :spi false, :alr 0, :alt 4825, :ns 817072312, :vrt 640, :wsp nil, :src "A", :altg 5125, :tru 41, :uti 1750878586, :wdi nil, :org nil, :hex "A4F59B", :opr nil, :cou "USA ", :fli "N419AM", :gda "A", :dis 42.5, :lon -88.81367, :lla 0, :squ nil, :lat 42.51814, :spd 105, :reg "N419AM", :pic 11, :ava "A", :typ "DA40", :trk 119, :dbm -78}
                {:tmp nil, :cat "A1", :sil 3, :dst nil, :spi false, :alr 0, :alt 1600, :mop 2, :sda 2, :ns 566304636, :vrt -64, :wsp nil, :src "A", :tru 193, :uti 1750878547, :wdi nil, :org nil, :hex "AA34BE", :nacp 9, :opr nil, :cou "USA ", :fli "N757HE", :gda "a", :dis 9.0, :cla 4, :lon -88.06391, :lla 42, :squ nil, :lat 42.44133, :spd 97, :reg "N757HE", :pic 11, :ava "A", :typ "C152", :trk 263, :dbm nil}]
               (sources/get-adsb-by-tail-numbers stubs/adsb-source ["N345TS" "N419AM"]))
      )
    )
  (context "unreserved flights"
    (with start-time (time/local-date-time 2023 10 1 12 0 0))
    (with co (time/plus @start-time (time/hours 1)))
    (with now (time/minus @start-time (time/hours 2)))
    (it "handles degenerate cases"
      (should= []
               (rc/include-unreserved-flights
                 []
                 {}))
      (should= [{:start-time @start-time
                 :tail-number "t1"}]
               (rc/include-unreserved-flights
                 [{:start-time @start-time
                   :tail-number "t1"}]
                 {}))
      (should= [{:start-time @start-time
                 :tail-number "t1"
                 :co @co}]
               (rc/include-unreserved-flights
                 [{:start-time @start-time
                   :tail-number "t1"
                   :co @co}]
                 {"t1" {:reg "t1"}}))
      )
    (it "does not include an adsb flight that is checked out"
      (should= [{:start-time @start-time
                 :tail-number "t1"
                 :co @co}]
               (rc/include-unreserved-flights
                 [{:start-time @start-time
                   :tail-number "t1"
                   :co @co}]
                 {"t1" {:reg "t1"}}))
      )

    (it "includes an adsb flight that is not checked out"
      (with-redefs [rc/get-now (fn [] @now)]
        (should= [{:start-time @now
                   :tail-number "t1"
                   :altitude 1000
                   :lat-lon ["lat" "lon"]
                   :ground-speed 160
                   :rogue? true
                   :adsb? true
                   :on-ground? false}
                  {:start-time @start-time
                   :tail-number "t1"}]
                 (rc/include-unreserved-flights
                   [{:start-time @start-time
                     :tail-number "t1"}]
                   {"t1" {:reg "t1"
                          :altg 1000
                          :lat "lat"
                          :lon "lon"
                          :spd 160}}))
        )

      )
    )
  )
