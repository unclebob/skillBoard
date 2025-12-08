(ns skillBoard.presenters.weather-spec
  (:require
    [skillBoard.presenters.weather :as weather]
    [speclj.core :refer :all]))

(describe "split-taf"
  (it "splits normal TAF strings correctly"
    (should= [{:line "TAF KORD", :color :white}
              {:line "121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250", :color :white}
              {:line "TEMPO 1212/1216 5SM -RA BR OVC015", :color :white}
              {:line "PROB30 121600 25012KT P6SM BKN020", :color :white}
              {:line "PROB40 TEMPO 130000 24008KT P6SM SCT030 BKN200", :color :white}
              {:line "BECMG131200 22005KT P6SM SCT250", :color :white}
              {:line "FM141200 22005KT P6SM SCT250", :color :white}]
             (weather/split-taf
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
             (weather/split-taf
               (str
                 "TAF AMD KORD 121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250 "
                 "FM141200 22005KT P6SM SCT250"))))

  (it "handles corrected TAFs correctly"
    (should= [{:line "TAF COR KORD", :color :white}
              {:line "121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250", :color :white}
              {:line "BECMG131200 22005KT P6SM SCT250", :color :white}]
             (weather/split-taf
               (str
                 "TAF COR KORD 121130Z 1212/1318 27015G25KT P6SM SCT020 BKN250 "
                 "BECMG131200 22005KT P6SM SCT250"))))
)