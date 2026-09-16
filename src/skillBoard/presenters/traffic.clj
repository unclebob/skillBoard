(ns skillBoard.presenters.traffic
  (:require
    [clojure.math :as math]
    [quil.core :as q]
    [skillBoard.atoms :as atoms]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.core-utils :as core-utils]
    [skillBoard.navigation :as nav]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.presenters.utils :as utils]))

(def test-adsb-aircraft
  [{:reg "N12345" :lat 42.5 :lon -87.8 :alt 2500 :spd 120}
   {:reg "N98765" :lat 42.4 :lon -87.9 :alt 728 :spd 2}
   {:reg "N345TS" :lat 42.5960633 :lon -87.9273236 :alt 3000 :spd 100}
   {:reg "N378MA" :lat 42.4221486 :lon -87.8679161 :alt 2000}])

(defn- traffic-aircraft-source [adsb-aircraft]
  (if @atoms/test? test-adsb-aircraft adsb-aircraft))

(defn- format-three-digits [value]
  (if (nil? value) "---" (format "%03d" (math/round value))))

(defn- tail-number [aircraft]
  (or (:reg aircraft) "UNKNOWN"))

(defn- close-to-ground? [aircraft alt]
  (or (< (abs (- alt config/airport-elevation)) 30)
      (#{"G" "g"} (:gda aircraft ""))))

(defn- brg-alt-gs [bearing distance alt gs close-to-ground?]
  (format "%s%s%s/%s/%s"
          config/bearing-center
          (format-three-digits bearing)
          (format-three-digits distance)
          (if close-to-ground? "GND" (format-three-digits (/ alt 100.0)))
          (format-three-digits gs)))

(defn- traffic-remark [aircraft fleet-aircraft tail-number distance alt gs close-to-ground?]
  (let [{:keys [lat lon]} aircraft
        remark (utils/generate-position-remark distance alt gs close-to-ground? lat lon)
        base-color (if (#{"RAMP" "TAXI"} remark) config/on-ground-color config/in-fleet-color)
        out-of-fleet (not (fleet-aircraft tail-number))
        color (if out-of-fleet config/out-of-fleet-color base-color)]
    [remark color]))

(defn- aircraft-line [fleet-aircraft airport-lat airport-lon aircraft]
  (let [{:keys [lat lon]} aircraft
        alt (:alt aircraft 0)
        gs (:spd aircraft 0)
        {:keys [bearing distance]} (nav/dist-and-bearing airport-lat airport-lon lat lon)
        grounded? (close-to-ground? aircraft alt)
        tail-number (tail-number aircraft)
        [remarks color] (traffic-remark aircraft fleet-aircraft tail-number distance alt gs grounded?)
        line (format "%-8s %-16s %-8s"
                     tail-number
                     (brg-alt-gs bearing distance alt gs grounded?)
                     remarks)]
    {:line line
     :color color
     :distance (format-three-digits distance)}))

(defn- sorted-aircraft-lines [adsb-aircraft fleet-aircraft]
  (let [[airport-lat airport-lon] config/airport-lat-lon
        fleet-aircraft (set fleet-aircraft)]
    (sort-by :distance
             (map #(aircraft-line fleet-aircraft airport-lat airport-lon %)
                  (traffic-aircraft-source adsb-aircraft)))))

(defn- displayed-traffic [sorted-aircraft short-metar]
  (let [total-lines (:line-count @config/display-info)
        blank-line {:line "" :color config/info-color}
        padded-aircraft (concat sorted-aircraft (repeat total-lines blank-line))
        displayed-items (take (dec total-lines) padded-aircraft)]
    (concat displayed-items [short-metar])))

(defn- log-traffic! [sorted-aircraft]
  (when @atoms/log-traffic?
    (doseq [line (map :line sorted-aircraft)]
      (core-utils/log-event :status :aircraft-report (str "Traffic: " line)))
    (reset! atoms/log-traffic? false)))

(defn make-traffic-screen [adsb-aircraft fleet-aircraft]
  (let [sorted-aircraft (sorted-aircraft-lines adsb-aircraft fleet-aircraft)]
    (log-traffic! sorted-aircraft)
    (displayed-traffic sorted-aircraft (utils/get-short-metar))))

(defmethod screen/make :traffic [_]
  (make-traffic-screen @comm/polled-nearby-adsbs @comm/polled-aircraft))

(defmethod screen/header-text :traffic [_]
  "NEARBY TRAFFIC")

(defmethod screen/display-column-headers :traffic [_ flap-width header-font label-font-size]
  (screen/draw-column-headers flap-width header-font label-font-size
                              [["AIRCRAFT" 0]
                               ["BRG/ALT/GS" 9]
                               ["REMARKS" 27]]))
