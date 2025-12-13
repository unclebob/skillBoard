(ns skillBoard.presenters.airports-spec
  (:require
    [skillBoard.config :as config]
    [skillBoard.presenters.airports :as airports]
    [speclj.core :refer :all]))

(describe "make-flight-category-line"
  (it "formats a complete metar correctly"
    (should= {:line "KORD  VFR CLR         10 15    010", :color config/vfr-color}
             (airports/make-flight-category-line {:fltCat "VFR" :icaoId "KORD" :visib "10" :cover "CLR" :clouds []
                                                  :wspd 15 :wgst nil :lat 42.5960633 :lon -87.9273236})))

  (it "handles nil values correctly"
    (should= {:line "KORD      CLR         10 15G20 010", :color config/info-color}
             (airports/make-flight-category-line {:fltCat nil :icaoId "KORD" :visib "10" :cover "CLR" :clouds []
                                                  :wspd 15 :wgst 20 :lat 42.5960633 :lon -87.9273236})))

  (it "handles non-CLR cover with cloud base"
    (should= {:line "KORD  IFR BKN   030    5 10G15 010", :color config/ifr-color}
             (airports/make-flight-category-line {:fltCat "IFR" :icaoId "KORD" :visib "5"
                                                  :cover "BKN" :clouds [{:base "030"}]
                                                  :wspd 10 :wgst 15 :lat 42.5960633 :lon -87.9273236})))

  (it "handles nil cover"
    (should= {:line "KORD  IFR       050    5 10G15 010", :color config/ifr-color}
             (airports/make-flight-category-line {:fltCat "IFR" :icaoId "KORD" :visib "5"
                                                  :cover nil :clouds [{:base "050"}]
                                                  :wspd 10 :wgst 15 :lat 42.5960633 :lon -87.9273236})))

  (it "handles nil base"
    (should= {:line "KORD  IFR OVC          5 10G15 010", :color config/ifr-color}
             (airports/make-flight-category-line {:fltCat "IFR" :icaoId "KORD" :visib "5" :cover "OVC" :clouds []
                                                  :wspd 10 :wgst 15 :lat 42.5960633 :lon -87.9273236})))

  (it "colors MVFR"
    (should= {:line "KJFK MVFR CLR         10 10    010", :color config/mvfr-color}
             (airports/make-flight-category-line {:fltCat "MVFR" :icaoId "KJFK" :visib "10" :cover "CLR" :clouds []
                                                  :wspd 10 :wgst nil :lat 42.5960633 :lon -87.9273236})))

  (it "colors LIFR"
    (should= {:line "KLAX LIFR OVC   005    1  5G10 010", :color config/lifr-color}
             (airports/make-flight-category-line {:fltCat "LIFR" :icaoId "KLAX" :visib "1" :cover "OVC" :clouds [{:base "005"}]
                                                  :wspd 5 :wgst 10 :lat 42.5960633 :lon -87.9273236})))
  )