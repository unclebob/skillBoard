(ns skillBoard.config)

(def config (atom nil))

(defn load-config []
  (reset! config (read-string (slurp "private/config"))))

(def version "20251209")

;Display configuration
(def display-info (atom {}))                                ;determined after sketch started.
(def cols 64)                                               ;The number of columns in the display
(def header-height-fraction 0.1)
(def label-height-fraction 0.03)
(def sf-char-gap 0.08)
(def sf-line-gap 0.25)
(def flap-duration 3500)                                    ;milliseconds
(def frame-rate 15)

;screens
(def screens
  (atom (cycle
          [{:duration 40 :screen :flights}
           {:duration 20 :screen :taf}
           {:duration 20 :screen :airports}])))


;Home airport configuration
(def airport "KUGN")
(def airport-lat-lon [42.4221486 -87.8679161])
(def airport-elevation 728.1)
(def pattern-altitude 1728)
(def bearing-center "UGN")
(def nearby-altitude-range [airport-elevation 4000])
(def nearby-distance 3)

(def time-zone "America/Chicago")

;other nearby airports
(def taf-airport "KENW")
(def secondary-metar-airports ["KENW" "KJVL"])
(def all-metars ["KUGN" "KMKE" "KUES" "KBUU" "KJVL" "KPIA" "KFWA" "KSBN" "KMSN" "KMDW" "KENW" "KRFD" "KDKB" "KDPA" "KETB" "KVYS" "KBMI"])


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

