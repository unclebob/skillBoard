(ns skillBoard.presenters.traffic
  (:require
    [clojure.math :as math]
    [quil.core :as q]
    [skillBoard.atoms :as atoms]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.navigation :as nav]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.presenters.utils :as utils]))

(defn make-traffic-screen [adsb-aircraft fleet-aircraft]
  (let [adsb-aircraft (if @atoms/test?
                        [{:fli "N12345" :lat 42.5 :lon -87.8 :alt 2500 :spd 120}
                         {:fli "N98765" :lat 42.4 :lon -87.9 :alt 728 :spd 2}
                         {:fli "N345TS" :lat 42.5960633 :lon -87.9273236 :alt 3000 :spd 100}
                         {:fli "N378MA" :lat 42.4221486 :lon -87.8679161 :alt 2000}]
                        adsb-aircraft)
        airport-lat-lon config/airport-lat-lon
        [airport-lat airport-lon] airport-lat-lon

        aircraft-lines
        (for [aircraft adsb-aircraft]
          (let [fleet-aircraft (set fleet-aircraft)
                lat (:lat aircraft)
                lon (:lon aircraft)
                alt (:alt aircraft 0)
                gs (:spd aircraft 0)
                {:keys [bearing distance]} (nav/dist-and-bearing airport-lat airport-lon lat lon)
                brg (if (nil? bearing) "---" (format "%03d" (math/round bearing)))
                gs-rounded (if (nil? gs) "---" (format "%03d" (math/round gs)))
                dist (if (nil? distance) "---" (format "%03d" (math/round distance)))
                alt-hundreds (if (nil? alt) "---" (format "%03d" (math/round (/ alt 100.0))))
                close-to-ground? (or (< (abs (- alt config/airport-elevation)) 30)
                                     (#{"G" "g"} (:gda aircraft "")))
                alt-str (if close-to-ground? "GND" alt-hundreds)
                brg-alt-gs (format "%s%s%s/%s/%s" config/bearing-center brg dist alt-str gs-rounded)
                tail-number (:fli aircraft)
                tail-number (if (nil? tail-number) "UNKNOWN" tail-number)

                generate-remark
                (fn []
                  (let [remark (utils/generate-position-remark distance alt gs close-to-ground? lat lon)
                        base-color (if (#{"RAMP" "TAXI"} remark) config/on-ground-color config/in-fleet-color)
                        out-of-fleet (not (fleet-aircraft tail-number))
                        color (if out-of-fleet config/out-of-fleet-color base-color)]
                    [remark color]))

                [remarks color] (generate-remark)
                line (format "%-8s %-16s %-8s" tail-number brg-alt-gs remarks)]
            {:line line :color color :distance dist}))
        sorted-aircraft (sort-by :distance aircraft-lines)
        total-lines (:line-count @config/display-info)
        blank-line {:line "" :color config/info-color}
        padded-aircraft (concat sorted-aircraft (repeat total-lines blank-line))
        displayed-items (take (dec total-lines) padded-aircraft)
        short-metar (utils/get-short-metar)
        final-screen (concat displayed-items [short-metar])]
    final-screen))

(defmethod screen/make :traffic [_]
  (make-traffic-screen @comm/polled-nearby-adsbs @comm/polled-aircraft))

(defmethod screen/header-text :traffic [_]
  "NEARBY TRAFFIC")

(defmethod screen/display-column-headers :traffic [_ flap-width header-font]
  (let [baseline (screen/setup-headers header-font)]
    (q/text "AIRCRAFT" 0 baseline)
    (q/text "BRG/ALT/GS" (* flap-width 9) baseline)
    (q/text "REMARKS" (* flap-width 27) baseline)))