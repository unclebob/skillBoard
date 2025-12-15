(ns skillBoard.presenters.airports
  (:require
    [quil.core :as q]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.navigation :as nav]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.presenters.utils :as utils]))

(defn make-flight-category-line [metar]
  (let [{:keys [fltCat icaoId visib cover clouds wspd wgst lat lon]} metar
        [airport-lat airport-lon] config/airport-lat-lon
        dist (int (:distance (nav/dist-and-bearing lat lon airport-lat airport-lon)))
        dist (if (nil? dist) "---" (format "%03d" dist))
        base (if (= cover "CLR")
               "    "
               (:base (first clouds)))
        color (case fltCat
                "VFR" config/vfr-color
                "MVFR" config/mvfr-color
                "IFR" config/ifr-color
                "LIFR" config/lifr-color
                config/info-color)
        fltCat-display (if (nil? fltCat) "    " fltCat)
        cover (if (nil? cover) "   " cover)
        base (if (nil? base) "     " base)
        wgst (if (nil? wgst) "   " (str "G" wgst))
        ctgy-line (format "%4s %4s %3s %5s %4s %2s%3s %s" icaoId fltCat-display cover base visib wspd wgst dist)]
    {:line ctgy-line :color color}))

(defn make-airports-screen []
  (let [metars (vals @comm/polled-metars)
        metars (sort utils/by-distance metars)
        fc-lines (map make-flight-category-line metars)]
    fc-lines))

(defmethod screen/make :airports [_]
  (make-airports-screen))

(defmethod screen/header-text :airports [_]
  "FLIGHT CATEGORY")

(defmethod screen/display-column-headers :airports [_ flap-width header-font]
  (let [baseline (screen/setup-headers header-font)]
    (q/text "AIRPORT" 0 baseline)
    (q/text "CATGRY" (* flap-width 5) baseline)
    (q/text "SKY" (* flap-width 10) baseline)
    (q/text "BASE" (* flap-width 14) baseline)
    (q/text "VIS" (* flap-width 20) baseline)
    (q/text "WIND" (* flap-width 25) baseline)
    (q/text "DIST" (* flap-width 31) baseline)))