(ns skillBoard.presenters.weather-spec
  (:require
    [skillBoard.config :as config]
    [skillBoard.presenters.weather :as weather]
    [speclj.core :refer :all]))

(describe "split-taf"
  (it "splits normal TAF strings correctly"
    (should= [{:line "TAF KORD", :color config/info-color}
              {:line "121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250", :color config/mvfr-color}
              {:line "TEMPO 1212/1216 5SM -RA BR OVC015", :color config/mvfr-color}
              {:line "PROB30 121600 25012KT P6SM BKN020", :color config/mvfr-color}
              {:line "PROB40 TEMPO 130000 24008KT P6SM SCT030 BKN200", :color config/mvfr-color}
              {:line "BECMG131200 22005KT P6SM SCT250", :color config/mvfr-color}
              {:line "FM141200 22005KT P6SM SCT250", :color config/mvfr-color}]
             (weather/split-taf
               (str
                 "TAF KORD 121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250 "
                 "TEMPO 1212/1216 5SM -RA BR OVC015 PROB30 121600 25012KT P6SM BKN020 "
                 "PROB40 TEMPO 130000 24008KT P6SM SCT030 BKN200 "
                 "BECMG131200 22005KT P6SM SCT250 "
                 "FM141200 22005KT P6SM SCT250"))))
  (it "handles amended TAFs correctly"
    (should= [{:line "TAF AMD KORD", :color config/info-color}
              {:line "121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250", :color config/mvfr-color}
              {:line "FM141200 22005KT P6SM SCT250", :color config/mvfr-color}]
             (weather/split-taf
               (str
                 "TAF AMD KORD 121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250 "
                 "FM141200 22005KT P6SM SCT250"))))

  (it "handles corrected TAFs correctly"
    (should= [{:line "TAF COR KORD", :color config/info-color}
              {:line "121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250", :color config/mvfr-color}
              {:line "BECMG131200 22005KT P6SM SCT250", :color config/mvfr-color}]
             (weather/split-taf
               (str
                 "TAF COR KORD 121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250 "
                 "BECMG131200 22005KT P6SM SCT250"))))

  (it "colors VFR forecast"
    (should= [{:line "TAF KJFK", :color config/info-color}
              {:line "121130Z 1212/1318 27015KT 10SM CLR 24/04 A3014", :color config/vfr-color}]
             (weather/split-taf "TAF KJFK 121130Z 1212/1318 27015KT 10SM CLR 24/04 A3014")))

  (it "colors IFR forecast"
    (should= [{:line "TAF KJFK", :color config/info-color}
              {:line "121130Z 1212/1318 27015KT 2SM OVC006 24/04 A3014", :color config/ifr-color}]
             (weather/split-taf "TAF KJFK 121130Z 1212/1318 27015KT 2SM OVC006 24/04 A3014")))

  (it "colors LIFR forecast"
    (should= [{:line "TAF KJFK", :color config/info-color}
              {:line "121130Z 1212/1318 27015KT 1/2SM OVC003 24/04 A3014", :color config/lifr-color}]
             (weather/split-taf "TAF KJFK 121130Z 1212/1318 27015KT 1/2SM OVC003 24/04 A3014")))
)