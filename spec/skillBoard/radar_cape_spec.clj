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

  (context "update-with-adsb"
    (it "returns empty list for empty reservations"
      (should= [] (rc/update-with-adsb [] {})))

    (it "does not update reservation without checkout"
      (let [res {:tail-number "N123" :start-time "10:00"}]
        (should= [res] (rc/update-with-adsb [res] {"N123" {:reg "N123" :altg 5000 :lat 42.0 :lon -87.0 :spd 120 :gda "A"}}))))

    (it "does not update reservation with checkout but no adsb"
      (let [res {:tail-number "N123" :start-time "10:00" :co "checked"}]
        (should= [res] (rc/update-with-adsb [res] {}))))

    (it "updates reservation with checkout and adsb data"
      (let [res {:tail-number "N123" :start-time "10:00" :co "checked"}
            adsb {:reg "N123" :altg 5000 :lat 42.0 :lon -87.0 :spd 120 :gda "A"}]
        (should= [(assoc res :adsb? true :altitude 5000 :lat-lon [42.0 -87.0] :ground-speed 120 :on-ground? false)]
                 (rc/update-with-adsb [res] {"N123" adsb}))))

    (it "marks as on ground when gda is 'g' or 'G'"
      (let [res {:tail-number "N123" :co "checked"}
            adsb-g {:reg "N123" :altg 5000 :lat 42.0 :lon -87.0 :spd 120 :gda "g"}
            adsb-G {:reg "N123" :altg 5000 :lat 42.0 :lon -87.0 :spd 120 :gda "G"}]
        (should= [(assoc res :adsb? true :altitude 5000 :lat-lon [42.0 -87.0] :ground-speed 120 :on-ground? true)]
                 (rc/update-with-adsb [res] {"N123" adsb-g}))
        (should= [(assoc res :adsb? true :altitude 5000 :lat-lon [42.0 -87.0] :ground-speed 120 :on-ground? true)]
                 (rc/update-with-adsb [res] {"N123" adsb-G}))))

    (it "handles multiple reservations with mixed updates"
      (let [res1 {:tail-number "N123" :co "checked"}
            res2 {:tail-number "N456"} ; no co
            res3 {:tail-number "N789" :co "checked"}
            adsbs {"N123" {:reg "N123" :altg 5000 :lat 42.0 :lon -87.0 :spd 120 :gda "A"}
                   "N789" {:reg "N789" :altg 6000 :lat 43.0 :lon -88.0 :spd 130 :gda "g"}}]
        (should= [(assoc res1 :adsb? true :altitude 5000 :lat-lon [42.0 -87.0] :ground-speed 120 :on-ground? false)
                  res2
                  (assoc res3 :adsb? true :altitude 6000 :lat-lon [43.0 -88.0] :ground-speed 130 :on-ground? true)]
                 (rc/update-with-adsb [res1 res2 res3] adsbs))))
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
