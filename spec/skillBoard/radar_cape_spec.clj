(ns skillBoard.radar-cape-spec
  (:require
    [skillBoard.radar-cape :as rc]
    [skillBoard.sources :as sources]
    [speclj.core :refer :all]))

(def adsb-stub-source {:type :adsb-stub})

(defmethod sources/get-adsb-raw :adsb-stub [_source tail-numbers]
  [{:tmp nil, :cat "A1", :dst nil, :spi false, :alr 0, :alt 4825, :ns 817072312, :vrt 640, :wsp nil, :src "A", :altg 5125, :tru 41, :uti 1750878586, :wdi nil, :org nil, :hex "A4F59B", :opr nil, :cou "USA ", :fli "N419AM", :gda "A", :dis 42.5, :lon -88.81367, :lla 0, :squ nil, :lat 42.51814, :spd 105, :reg "N419AM", :pic 11, :ava "A", :typ "DA40", :trk 119, :dbm -78}
   {:tmp nil, :cat "A1", :sil 3, :dst nil, :spi false, :alr 0, :alt 1600, :mop 2, :sda 2, :ns 566304636, :vrt -64, :wsp nil, :src "A", :tru 193, :uti 1750878547, :wdi nil, :org nil, :hex "AA34BE", :nacp 9, :opr nil, :cou "USA ", :fli "N757HE", :gda "a", :dis 9.0, :cla 4, :lon -88.06391, :lla 42, :squ nil, :lat 42.44133, :spd 97, :reg "N757HE", :pic 11, :ava "A", :typ "C152", :trk 263, :dbm nil}]
  )

(describe "Radar Cape"
  (it "fetches the ADSB raw status"
    (should= [{:tmp nil, :cat "A1", :dst nil, :spi false, :alr 0, :alt 4825, :ns 817072312, :vrt 640, :wsp nil, :src "A", :altg 5125, :tru 41, :uti 1750878586, :wdi nil, :org nil, :hex "A4F59B", :opr nil, :cou "USA ", :fli "N419AM", :gda "A", :dis 42.5, :lon -88.81367, :lla 0, :squ nil, :lat 42.51814, :spd 105, :reg "N419AM", :pic 11, :ava "A", :typ "DA40", :trk 119, :dbm -78}
              {:tmp nil, :cat "A1", :sil 3, :dst nil, :spi false, :alr 0, :alt 1600, :mop 2, :sda 2, :ns 566304636, :vrt -64, :wsp nil, :src "A", :tru 193, :uti 1750878547, :wdi nil, :org nil, :hex "AA34BE", :nacp 9, :opr nil, :cou "USA ", :fli "N757HE", :gda "a", :dis 9.0, :cla 4, :lon -88.06391, :lla 42, :squ nil, :lat 42.44133, :spd 97, :reg "N757HE", :pic 11, :ava "A", :typ "C152", :trk 263, :dbm nil}]
             (sources/get-adsb-raw adsb-stub-source ["N345TS" "N419AM"]))
    )

  (it "packs the raw status into a map"
    (should= {"N419AM" {:tmp nil, :cat "A1", :dst nil, :spi false, :alr 0, :alt 4825, :ns 817072312, :vrt 640, :wsp nil, :src "A", :altg 5125, :tru 41, :uti 1750878586, :wdi nil, :org nil, :hex "A4F59B", :opr nil, :cou "USA ", :fli "N419AM", :gda "A", :dis 42.5, :lon -88.81367, :lla 0, :squ nil, :lat 42.51814, :spd 105, :reg "N419AM", :pic 11, :ava "A", :typ "DA40", :trk 119, :dbm -78}
              "N757HE" {:tmp nil, :cat "A1", :sil 3, :dst nil, :spi false, :alr 0, :alt 1600, :mop 2, :sda 2, :ns 566304636, :vrt -64, :wsp nil, :src "A", :tru 193, :uti 1750878547, :wdi nil, :org nil, :hex "AA34BE", :nacp 9, :opr nil, :cou "USA ", :fli "N757HE", :gda "a", :dis 9.0, :cla 4, :lon -88.06391, :lla 42, :squ nil, :lat 42.44133, :spd 97, :reg "N757HE", :pic 11, :ava "A", :typ "C152", :trk 263, :dbm nil}}
             (rc/get-adsb adsb-stub-source ["N345TS" "N419AM"])))
  )
