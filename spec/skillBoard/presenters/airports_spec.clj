(ns skillBoard.presenters.airports-spec
  (:require
    [skillBoard.presenters.airports :as airports]
    [speclj.core :refer :all]))

(describe "make-flight-category-line"
  (it "formats a complete metar correctly"
    (should= {:line "KORD  VFR CLR        10 15   ", :color :green}
             (airports/make-flight-category-line {:fltCat "VFR" :icaoId "KORD" :visib "10" :cover "CLR" :clouds [] :wspd 15 :wgst nil})))

  (it "handles nil values correctly"
    (should= {:line "KORD      CLR        10 15G20", :color :white}
             (airports/make-flight-category-line {:fltCat nil :icaoId "KORD" :visib "10" :cover "CLR" :clouds [] :wspd 15 :wgst 20})))

  (it "handles non-CLR cover with cloud base"
    (should= {:line "KORD  IFR BKN   030   5 10G15", :color :red}
             (airports/make-flight-category-line {:fltCat "IFR" :icaoId "KORD" :visib "5" :cover "BKN" :clouds [{:base "030"}] :wspd 10 :wgst 15})))

  (it "handles nil cover"
    (should= {:line "KORD  IFR       050   5 10G15", :color :red}
             (airports/make-flight-category-line {:fltCat "IFR" :icaoId "KORD" :visib "5" :cover nil :clouds [{:base "050"}] :wspd 10 :wgst 15})))

  (it "handles nil base"
    (should= {:line "KORD  IFR OVC         5 10G15", :color :red}
             (airports/make-flight-category-line {:fltCat "IFR" :icaoId "KORD" :visib "5" :cover "OVC" :clouds [] :wspd 10 :wgst 15})))

  (it "colors MVFR blue"
    (should= {:line "KJFK MVFR CLR        10 10   ", :color :blue}
             (airports/make-flight-category-line {:fltCat "MVFR" :icaoId "KJFK" :visib "10" :cover "CLR" :clouds [] :wspd 10 :wgst nil})))

  (it "colors LIFR magenta"
    (should= {:line "KLAX LIFR OVC   005   1  5G10", :color :magenta}
             (airports/make-flight-category-line {:fltCat "LIFR" :icaoId "KLAX" :visib "1" :cover "OVC" :clouds [{:base "005"}] :wspd 5 :wgst 10})))
)