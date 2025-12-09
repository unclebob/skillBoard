(ns skillBoard.presenters.utils-spec
  (:require
    [skillBoard.presenters.utils :as utils]
    [skillBoard.navigation :as nav]
    [skillBoard.config :as config]
    [skillBoard.comm-utils :as comm]
    [speclj.core :refer :all]))

(describe "by-distance"
  (it "calls nav/dist-and-bearing with correct arguments"
    (let [calls (atom [])
          [airport-lat airport-lon] config/airport-lat-lon
          metar1 {:lat 10.0 :lon 20.0}
          metar2 {:lat 30.0 :lon 40.0}]
      (with-redefs [nav/dist-and-bearing (fn [& args]
                                           (swap! calls conj args)
                                           {:distance 1.0})]  ; dummy return
        (utils/by-distance metar1 metar2)
        (should= [[airport-lat airport-lon 10.0 20.0]
                  [airport-lat airport-lon 30.0 40.0]] @calls)))))

(describe "get-short-metar"
  (it "calls shorten-metar with the correct metar"
    (let [calls (atom [])
          airport "KJFK"
          metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014"}]
      (with-redefs [comm/polled-metars (atom {airport metar})
                    utils/shorten-metar (fn [m]
                                          (swap! calls conj m)
                                          {:line "shortened" :color :white})]
        (utils/get-short-metar airport)
        (should= [metar] @calls)))))

(describe "shorten-metar"
  (it "returns NO-METAR for nil input"
    (should= {:line "NO-METAR" :color :white} (utils/shorten-metar nil)))

  (it "shortens METAR without RMK"
    (should= {:line "KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014" :color :white}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014"})))

  (it "shortens METAR with RMK, taking before RMK"
    (should= {:line "KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014" :color :white}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM FEW250 24/04 A3014 RMK AO2 SLP221 T02440044"})))

  (it "truncates if the shortened METAR is longer than 64 characters"
    (let [long-part (apply str (repeat 70 "A"))
          metar-text (str "METAR " long-part)]
      (should= {:line (subs long-part 0 64) :color :white} (utils/shorten-metar {:rawOb metar-text}))))

  (it "colors VFR green"
    (should= {:line "KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :color :green}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :fltCat "VFR"})))

  (it "colors MVFR blue"
    (should= {:line "KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :color :blue}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :fltCat "MVFR"})))

  (it "colors IFR red"
    (should= {:line "KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :color :red}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :fltCat "IFR"})))

  (it "colors LIFR magenta"
    (should= {:line "KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :color :magenta}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :fltCat "LIFR"})))

  (it "colors unknown fltCat white"
    (should= {:line "KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :color :white}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :fltCat "UNKNOWN"})))

  (it "colors nil fltCat white"
    (should= {:line "KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :color :white}
             (utils/shorten-metar {:rawOb "METAR KJFK 191251Z 31008KT 10SM CLR 24/04 A3014" :fltCat nil}))))