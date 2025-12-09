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

(defn make-traffic-screen [adsb-aircraft scheduled-aircraft]
  (let [adsb-aircraft (if @atoms/test?
                        [{:fli "N12345" :lat 42.5 :lon -87.8 :alt 2500 :gs 120}
                         {:fli "N98765" :lat 42.4 :lon -87.9 :alt 728 :gs 2}
                         {:fli "N345TS" :lat 42.5960633 :lon -87.9273236 :alt 3000 :gs 100}
                         {:fli "N378MA" :lat 42.4221486 :lon -87.8679161 :alt 2000}]
                        adsb-aircraft)
        airport-lat-lon config/airport-lat-lon
        [airport-lat airport-lon] airport-lat-lon
        aircraft-lines (for [aircraft adsb-aircraft]
                         (let [scheduled-aircraft (set scheduled-aircraft)
                               lat (:lat aircraft)
                               lon (:lon aircraft)
                               alt (:alt aircraft 0)
                               gs (:gs aircraft 0)
                               {:keys [bearing distance]} (nav/dist-and-bearing airport-lat airport-lon lat lon)
                               brg (math/round bearing)
                               gs-rounded (math/round gs)
                               dist (math/round distance)
                               alt-hundreds (math/round (/ alt 100.0))
                               close-to-ground? (< (abs (- alt config/airport-elevation)) 10)
                               alt-str (if close-to-ground? "GND" (format "%03d" alt-hundreds))
                               brg-alt-gs (format "%s%03d%03d/%s/%03d" config/bearing-center brg dist alt-str gs-rounded)
                               tail-number (:fli aircraft)

                               generate-remark
                               (fn []
                                 (let [nearby? (< distance 6)
                                       low (+ config/airport-elevation 30)
                                       on-ground? (or close-to-ground? (< alt low))
                                       [position-remark base-color]
                                       (cond
                                         (and nearby? on-ground? (< gs 2)) ["RAMP" :green]
                                         (and nearby? on-ground? (<= 2 gs 25)) ["TAXI" :green]
                                         (< distance 2) ["NEAR" :white]
                                         :else [(utils/find-location lat lon alt config/geofences) :white])
                                       rogue? (not (scheduled-aircraft tail-number))
                                       color (if rogue? :blue base-color)]
                                   [position-remark color]))

                               [remarks color] (generate-remark)
                               line (format "%-8s %-16s %-8s" tail-number brg-alt-gs remarks)]
                           {:line line :color color :distance dist}))
        sorted-aircraft (sort-by :distance aircraft-lines)
        total-lines (:line-count @config/display-info)
        blank-line {:line "" :color :white}
        padded-aircraft (concat sorted-aircraft (repeat total-lines blank-line))
        displayed-items (take (dec total-lines) padded-aircraft)
        short-metar (utils/get-short-metar)
        final-screen (concat displayed-items [short-metar])]
    final-screen))

(defmethod screen/make :traffic [_]
  (make-traffic-screen @comm/polled-nearby-adsbs @comm/polled-aircraft))

(defmethod screen/header-text :traffic [_]
  "TRAFFIC")

(defmethod screen/display-column-headers :traffic [_ flap-width header-font]
  (let [baseline (screen/setup-headers header-font)]
    (q/text "AIRCRAFT" 0 baseline)
    (q/text "BRG/ALT/GS" (* flap-width 9) baseline)
    (q/text "REMARKS" (* flap-width 27) baseline)))