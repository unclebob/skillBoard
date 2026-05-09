(ns skillBoard.presenters.airports
  (:require
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.navigation :as nav]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.presenters.utils :as utils]))

(defn- flight-category-distance [{:keys [lat lon]}]
  (let [[airport-lat airport-lon] config/airport-lat-lon
        distance (:distance (nav/dist-and-bearing lat lon airport-lat airport-lon))]
    (if (nil? distance) "---" (format "%03d" (int distance)))))

(defn- flight-category-base [{:keys [cover clouds]}]
  (if (= cover "CLR")
    "    "
    (:base (first clouds))))

(defn- flight-category-line-color [flt-cat]
  (get {"VFR" config/vfr-color
        "MVFR" config/mvfr-color
        "IFR" config/ifr-color
        "LIFR" config/lifr-color}
       flt-cat
       config/info-color))

(defn- display-or [value fallback]
  (if (nil? value) fallback value))

(defn- flight-category-wind-gust [wgst]
  (if (nil? wgst) "   " (str "G" wgst)))

(defn make-flight-category-line [{:keys [fltCat icaoId visib cover wspd wgst] :as metar}]
  (let [ctgy-line (format "%4s %4s %3s %5s %4s %2s%3s %s"
                          icaoId
                          (display-or fltCat "    ")
                          (display-or cover "   ")
                          (display-or (flight-category-base metar) "     ")
                          visib
                          (display-or wspd "--")
                          (flight-category-wind-gust wgst)
                          (flight-category-distance metar))]
    {:line ctgy-line :color (flight-category-line-color fltCat)}))

(defn make-airports-screen []
  (let [metars (vals @comm/polled-metars)
        metars (sort utils/by-distance metars)
        fc-lines (map make-flight-category-line metars)]
    fc-lines))

(defmethod screen/make :airports [_]
  (make-airports-screen))

(defmethod screen/header-text :airports [_]
  "FLIGHT CATEGORY")

(defmethod screen/display-column-headers :airports [_ flap-width header-font label-font-size]
  (screen/draw-column-headers flap-width header-font label-font-size
                              [["AIRPORT" 0]
                               ["CATGRY" 5]
                               ["SKY" 10]
                               ["BASE" 14]
                               ["VIS" 20]
                               ["WIND" 25]
                               ["DIST" 31]]))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-09T09:18:13.557664-05:00", :module-hash "1351740696", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "2111620770"} {:id "defn/make-flight-category-line", :kind "defn", :line 9, :end-line 29, :hash "-1105333052"} {:id "defn/make-airports-screen", :kind "defn", :line 31, :end-line 35, :hash "453534776"} {:id "defmethod/screen/make/:airports", :kind "defmethod", :line 37, :end-line 38, :hash "-107903511"} {:id "defmethod/screen/header-text/:airports", :kind "defmethod", :line 40, :end-line 41, :hash "349942679"} {:id "defmethod/screen/display-column-headers/:airports", :kind "defmethod", :line 43, :end-line 51, :hash "-167362968"}]}
;; clj-mutate-manifest-end
