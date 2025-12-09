(ns skillBoard.presenters.weather
  (:require
    [clojure.string :as str]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.presenters.screen]
    [skillBoard.presenters.utils :as utils]))

(defn split-taf [raw-taf]
  (let [[_ taf-name tafs] (re-find #"(TAF (?:COR )?(?:AMD )?\w+)(.*)" raw-taf)
        tafs (str/replace tafs #"PROB30 TEMPO" "PROB30 TEMPX")
        tafs (str/replace tafs #"PROB40 TEMPO" "PROB40 TEMPX")
        taf-items (map str/trim (str/split tafs #"(?=FM|BECMG|PROB[34]0 TEMPX|PROB[34]0 \d|TEMPO)"))
        taf-items (map #(str/replace % "TEMPX" "TEMPO") taf-items)
        taf-lines (concat [taf-name] taf-items)]
    (map (fn [line] {:line line :color :white}) taf-lines)))

(defn make-taf-screen []
  (let [taf-response (get @comm/polled-tafs config/taf-airport)
        primary-metar (utils/get-short-metar config/airport)
        secondary-metars (map utils/get-short-metar config/secondary-metar-airports)
        raw-tafs [(:rawTAF taf-response)]
        tafs (flatten (map #(->> % split-taf (take 8)) raw-tafs))
        blank-line {:line "" :color :white}]
    (concat tafs [blank-line primary-metar] secondary-metars)))

(defmethod skillBoard.presenters.screen/make :taf [_]
  (make-taf-screen))