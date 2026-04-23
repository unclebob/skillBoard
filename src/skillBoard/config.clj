(ns skillBoard.config)

(def config (atom nil))

(defn load-config []
  (reset! config (read-string (slurp "private/config"))))

(def version "20260423")

;Display configuration
(def display-info (atom {}))                                ;determined after sketch started.
(def cols 64)                                               ;The number of columns in the display
(def header-height-fraction 0.1)
(def label-height-fraction 0.03)
(def sf-char-gap 0.08)
(def sf-line-gap 0.25)
(def flap-duration 3000)                                    ;milliseconds
(def frame-rate 30)
(def flap-steps-per-update 1)

;colors
(def in-fleet-color :white)
(def out-of-fleet-color :yellow)
(def scheduled-flight-color :white)
(def unscheduled-flight-color :yellow)
(def on-ground-color :green)
(def vfr-color :green)
(def mvfr-color :blue)
(def ifr-color :red)
(def lifr-color :magenta)
(def info-color :white)

;screens
(def screens
  (atom (cycle
          [{:duration 40 :screen :flights}
           {:duration 20 :screen :taf}
           {:duration 30 :screen :wind-map}
           {:duration 20 :screen :traffic}
           {:duration 40 :screen :flights}
           {:duration 20 :screen :taf}
           {:duration 20 :screen :airports}
           {:duration 20 :screen :traffic}])))


;Home airport configuration
(def airport "KUGN")
(def airport-lat-lon [42.4221486 -87.8679161])
(def airport-elevation 728.1)
(def pattern-altitude 1728)
(def bearing-center "UGN")
(def nearby-altitude-range [0 4000])
(def nearby-distance 15)

;Wind map configuration
(def wind-map-radius-nm 200)
(def wind-map-particle-count 300)

(def time-zone "America/Chicago")

;other nearby airports
(def taf-airport "KENW")
(def flight-category-airports ["KUGN" "KMKE" "KUES" "KBUU" "KJVL" "KPIA" "KFWA" "KSBN" "KMSN" "KMDW" "KENW" "KRFD" "KDKB" "KDPA" "KETB" "KVYS" "KBMI"])

;Internet configuration
(def radar-cape-ip "10.10.40.60")
(def seconds-between-internet-polls 60)

;Wider area configuration.
;
;Named geofences each of which describes a cylinder of airspace.
;a flight in one of those cylinders will show the name in the remarks.
(def geofences [{:name "KUGN"
                 :lat 42.4221486
                 :lon -87.8679161
                 :radius 4
                 :min-alt 1000
                 :max-alt 3200}
                {:name "KENW"
                 :lat 42.5960633
                 :lon -87.9273236
                 :radius 4
                 :min-alt 1000
                 :max-alt 3200}
                {:name "C89"
                 :lat 42.7032500
                 :lon -87.9589722
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "57C"
                 :lat 42.7971667
                 :lon -88.3726111
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "KBUU"
                 :lat 42.6907171
                 :lon -88.3046825
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "10C"
                 :lat 42.4028889
                 :lon -88.3751111
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "C81"
                 :lat 42.3246111
                 :lon -88.0740881
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "3CK"
                 :lat 42.2068611
                 :lon -88.3226944
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "KPWK"
                 :lat 42.1142794
                 :lon -87.9015408
                 :radius 3
                 :min-alt 1000
                 :max-alt 3000}
                {:name "KDPA"
                 :lat 41.9070531
                 :lon -88.2479950
                 :radius 3
                 :min-alt 1000
                 :max-alt 3300}
                {:name "KMDW"
                 :lat 41.7856433
                 :lon -87.7527286
                 :radius 3
                 :min-alt 1000
                 :max-alt 3400}
                {:name "KARR"
                 :lat 41.7719167
                 :lon -88.4756667
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "KDKB"
                 :lat 41.9338342
                 :lon -88.7056864
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "KRFD"
                 :lat 42.1953611
                 :lon -89.0972222
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "KJVL"
                 :lat 42.6202528
                 :lon -89.0415669
                 :radius 3
                 :min-alt 1000
                 :max-alt 3300}
                {:name "44C"
                 :lat 42.4977778
                 :lon -88.9676111
                 :radius 3
                 :min-alt 1000
                 :max-alt 3300}
                {:name "C77"
                 :lat 42.3228611
                 :lon -88.8363056
                 :radius 3
                 :min-alt 1000
                 :max-alt 3300}
                {:name "0C0"
                 :lat 42.4024722
                 :lon -88.6323889
                 :radius 3
                 :min-alt 1000
                 :max-alt 3300}
                {:name "7V3"
                 :lat 42.5271648
                 :lon -88.6513921
                 :radius 3
                 :min-alt 1000
                 :max-alt 3300}
                {:name "C59"
                 :lat 42.6341261
                 :lon -88.6011292
                 :radius 3
                 :min-alt 1000
                 :max-alt 3300}
                {:name "C02"
                 :lat 42.6146831
                 :lon -88.3899867
                 :radius 3
                 :min-alt 1000
                 :max-alt 3300}
                {:name "49C"
                 :lat 42.5277972
                 :lon -88.1561972
                 :radius 3
                 :min-alt 1000
                 :max-alt 3300}
                {:name "06C"
                 :lat 41.9893408
                 :lon -88.1012428
                 :radius 3
                 :min-alt 1000
                 :max-alt 1900}
                {:name "KMKE"
                 :lat 42.9469319
                 :lon -87.8970644
                 :radius 3
                 :min-alt 1000
                 :max-alt 4700}
                {:name "C89"
                 :lat 42.7032500
                 :lon -87.9589722
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "KUES"
                 :lat 43.0410278
                 :lon -88.2370556
                 :radius 3
                 :min-alt 1000
                 :max-alt 3400}
                {:name "57C"
                 :lat 42.7971667
                 :lon -88.3726111
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "88C"
                 :lat 42.8835236
                 :lon -88.5993528
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "61C"
                 :lat 42.9632072
                 :lon -88.8176261
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "W11"
                 :lat 43.0072047
                 :lon -88.6028232
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "KRAC"
                 :lat 42.7611803
                 :lon -87.8139094
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "KMWC"
                 :lat 43.1103800
                 :lon -88.0344183
                 :radius 3
                 :min-alt 1000
                 :max-alt 3200}
                {:name "KGYY"
                 :lat 41.6172500
                 :lon -87.4145278
                 :radius 3
                 :min-alt 1000
                 :max-alt 3100}
                {:name "KLOT"
                 :lat 41.6080969
                 :lon -88.0963897
                 :radius 3
                 :min-alt 1000
                 :max-alt 2900}
                {:name "KJOT"
                 :lat 41.5177414
                 :lon -88.1753972
                 :radius 3
                 :min-alt 1000
                 :max-alt 2900}

                {:name "PRAC"
                 :lat 42.439186
                 :lon -88.495541
                 :radius 10
                 :min-alt 1000
                 :max-alt 10000}
                ])

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T10:27:57.044737-05:00", :module-hash "-112379986", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1618957594"} {:id "def/config", :kind "def", :line 3, :end-line 3, :hash "8349297"} {:id "defn/load-config", :kind "defn", :line 5, :end-line 6, :hash "344350491"} {:id "def/version", :kind "def", :line 8, :end-line 8, :hash "-167232095"} {:id "def/display-info", :kind "def", :line 11, :end-line 11, :hash "-344834255"} {:id "def/cols", :kind "def", :line 12, :end-line 12, :hash "-889380344"} {:id "def/header-height-fraction", :kind "def", :line 13, :end-line 13, :hash "256003515"} {:id "def/label-height-fraction", :kind "def", :line 14, :end-line 14, :hash "-2022710601"} {:id "def/sf-char-gap", :kind "def", :line 15, :end-line 15, :hash "1751182992"} {:id "def/sf-line-gap", :kind "def", :line 16, :end-line 16, :hash "-1116154457"} {:id "def/flap-duration", :kind "def", :line 17, :end-line 17, :hash "1968258047"} {:id "def/frame-rate", :kind "def", :line 18, :end-line 18, :hash "-1493516389"} {:id "def/flap-steps-per-update", :kind "def", :line 19, :end-line 19, :hash "1829469947"} {:id "def/in-fleet-color", :kind "def", :line 22, :end-line 22, :hash "1952847522"} {:id "def/out-of-fleet-color", :kind "def", :line 23, :end-line 23, :hash "209004293"} {:id "def/scheduled-flight-color", :kind "def", :line 24, :end-line 24, :hash "-334195183"} {:id "def/unscheduled-flight-color", :kind "def", :line 25, :end-line 25, :hash "519431839"} {:id "def/on-ground-color", :kind "def", :line 26, :end-line 26, :hash "-2022121670"} {:id "def/vfr-color", :kind "def", :line 27, :end-line 27, :hash "-1999154738"} {:id "def/mvfr-color", :kind "def", :line 28, :end-line 28, :hash "-743488135"} {:id "def/ifr-color", :kind "def", :line 29, :end-line 29, :hash "156820"} {:id "def/lifr-color", :kind "def", :line 30, :end-line 30, :hash "-1251237242"} {:id "def/info-color", :kind "def", :line 31, :end-line 31, :hash "-418733014"} {:id "def/screens", :kind "def", :line 34, :end-line 39, :hash "-1673006757"} {:id "def/airport", :kind "def", :line 43, :end-line 43, :hash "1915040766"} {:id "def/airport-lat-lon", :kind "def", :line 44, :end-line 44, :hash "-913795760"} {:id "def/airport-elevation", :kind "def", :line 45, :end-line 45, :hash "-511111945"} {:id "def/pattern-altitude", :kind "def", :line 46, :end-line 46, :hash "1165022277"} {:id "def/bearing-center", :kind "def", :line 47, :end-line 47, :hash "1251930368"} {:id "def/nearby-altitude-range", :kind "def", :line 48, :end-line 48, :hash "920037168"} {:id "def/nearby-distance", :kind "def", :line 49, :end-line 49, :hash "-560738597"} {:id "def/time-zone", :kind "def", :line 51, :end-line 51, :hash "1056167227"} {:id "def/taf-airport", :kind "def", :line 54, :end-line 54, :hash "-825426005"} {:id "def/flight-category-airports", :kind "def", :line 55, :end-line 55, :hash "1786265111"} {:id "def/radar-cape-ip", :kind "def", :line 58, :end-line 58, :hash "-502411287"} {:id "def/seconds-between-internet-polls", :kind "def", :line 59, :end-line 59, :hash "677342447"} {:id "def/geofences", :kind "def", :line 65, :end-line 282, :hash "-675454822"}]}
;; clj-mutate-manifest-end
