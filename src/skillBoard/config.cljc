(ns skillBoard.config)

(def config (atom nil))

(defn load-config []
  (reset! config (read-string (slurp "private/config"))))

(def version "20250724")

;Display configuration
(def display-info (atom {})) ;determined after sketch started.
(def cols 63) ;The number of columns in the display
(def header-height-fraction 0.1)
(def label-height-fraction 0.03)
(def sf-char-gap 0.25)
(def sf-line-gap 0.25)
(def font-width-per-point 1.196)
(def font-height-per-point 1.42)

;-- System Configuration

;Home airport configuration
(def airport "KUGN")
(def airport-lat-lon [42.4221486 -87.8679161])
(def time-zone "America/Chicago")
(def airport-elevation 728.1)
(def pattern-altitude 1728)

;ip addresses
(def radar-cape-ip "10.10.40.60")

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

                {:name "PRAC"
                 :lat 42.54
                 :lon -88.49
                 :radius 8
                 :min-alt 1000
                 :max-alt 7000}
                ])

