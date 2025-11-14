(ns skillBoard.presenter-spec
  (:require [skillBoard.presenter :as p]
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
  (context "split-taf"
    (it "splits normal TAF strings correctly"
      (should= ["TAF KORD"
                "121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250"
                "TEMPO 1212/1216 5SM -RA BR OVC015"
                "PROB30 121600 25012KT P6SM BKN020"
                "PROB40 TEMPO 130000 24008KT P6SM SCT030 BKN200"
                "BECMG131200 22005KT P6SM SCT250"
                "FM141200 22005KT P6SM SCT250"]
               (p/split-taf
                 (str
                   "TAF KORD 121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250 "
                   "TEMPO 1212/1216 5SM -RA BR OVC015 PROB30 121600 25012KT P6SM BKN020 "
                   "PROB40 TEMPO 130000 24008KT P6SM SCT030 BKN200 "
                   "BECMG131200 22005KT P6SM SCT250 "
                   "FM141200 22005KT P6SM SCT250"))))
    (it "handles amended TAFs correctly"
      (should= ["TAF AMD KORD"
                "121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250"
                "FM141200 22005KT P6SM SCT250"]
               (p/split-taf
                 (str
                   "TAF AMD KORD 121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250 "
                   "FM141200 22005KT P6SM SCT250"))))
    )
  )