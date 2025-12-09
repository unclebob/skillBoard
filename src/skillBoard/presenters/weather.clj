(ns skillBoard.presenters.weather
  (:require
    [clojure.string :as str]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.presenters.utils :as utils]))

(defn parse-taf-visibility [line]
  (if-let [[_ vis-str] (re-find #"(\d+(?:/\d+)?)SM" line)]
    (let [vis (if (str/includes? vis-str "/")
                (/ (Double/parseDouble (first (str/split vis-str #"/")))
                   (Double/parseDouble (second (str/split vis-str #"/"))))
                (Double/parseDouble vis-str))]
      vis)
    10.0)) ; default to 10 if not found

(defn parse-taf-ceiling [line]
  (if (str/includes? line "CLR")
    10000 ; clear, high ceiling
    (if-let [[_ alt-str] (re-find #"(?:BKN|OVC)(\d{3})" line)]
      (* (Integer/parseInt alt-str) 100) ; convert to feet
      10000))) ; default high if no clouds

(defn weather-color [line]
  (if (re-find #"^TAF" line)
    :white ; header line
    (let [vis (parse-taf-visibility line)
          ceiling (parse-taf-ceiling line)]
      (utils/flight-category vis ceiling))))

(defn split-taf [raw-taf]
  (let [[_ taf-name tafs] (re-find #"(TAF (?:COR )?(?:AMD )?\w+)(.*)" raw-taf)
        tafs (str/replace tafs #"PROB30 TEMPO" "PROB30 TEMPX")
        tafs (str/replace tafs #"PROB40 TEMPO" "PROB40 TEMPX")
        taf-items (map str/trim (str/split tafs #"(?=FM|BECMG|PROB[34]0 TEMPX|PROB[34]0 \d|TEMPO)"))
        taf-items (map #(str/replace % "TEMPX" "TEMPO") taf-items)
        taf-lines (concat [taf-name] taf-items)]
    (map (fn [line] {:line line :color (weather-color line)}) taf-lines)))

(defn make-taf-screen []
  (let [taf-response (get @comm/polled-tafs config/taf-airport)
        primary-metar (utils/get-short-metar config/airport)
        secondary-metars (map utils/get-short-metar config/secondary-metar-airports)
        raw-tafs [(:rawTAF taf-response)]
        tafs (flatten (map #(->> % split-taf (take 8)) raw-tafs))
        blank-line {:line "" :color :white}]
    (concat tafs [blank-line primary-metar] secondary-metars)))

(defmethod screen/make :taf [_]
  (make-taf-screen))

(defmethod screen/header-text :taf [_]
  "WEATHER")

(defmethod screen/display-column-headers :taf [_ _flap-width _header-font]
  nil)