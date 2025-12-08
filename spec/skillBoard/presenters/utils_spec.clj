(ns skillBoard.presenters.utils-spec
  (:require
    [skillBoard.presenters.utils :as utils]
    [speclj.core :refer :all]))

(describe "shorten-metar"
  (it "returns NO-METAR for nil input"
    (should= {:line "NO-METAR" :color :white} (utils/shorten-metar nil)))

  (it "shortens METAR without RMK"
    (should= {:line "KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014" :color :white}
             (utils/shorten-metar "METAR KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014")))

  (it "shortens METAR with RMK, taking before RMK"
    (should= {:line "KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014" :color :white}
             (utils/shorten-metar "METAR KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014 RMK AO2 SLP221 T02440044")))

  (it "truncates if the shortened METAR is longer than 64 characters"
    (let [long-part (apply str (repeat 70 "A"))
          metar-text (str "METAR " long-part)]
      (should= {:line (subs long-part 0 64) :color :white} (utils/shorten-metar metar-text))))
)