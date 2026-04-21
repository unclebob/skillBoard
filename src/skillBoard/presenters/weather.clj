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
    10.0))                                                  ; default to 10 if not found

(defn parse-taf-ceiling [line]
  (if (str/includes? line "CLR")
    10000                                                   ; clear, high ceiling
    (if-let [[_ alt-str] (re-find #"(?:BKN|OVC)(\d{3})" line)]
      (* (Integer/parseInt alt-str) 100)                    ; convert to feet
      10000)))                                              ; default high if no clouds

(defn weather-color [line]
  (if (re-find #"^TAF" line)
    config/info-color                                       ; header line
    (let [vis (parse-taf-visibility line)
          ceiling (parse-taf-ceiling line)]
      (utils/flight-category-color vis ceiling))))

(defn split-taf [raw-taf]
  (if (nil? raw-taf)
    {}
    (let [[_ taf-name tafs] (re-find #"(TAF (?:COR )?(?:AMD )?\w+)(.*)" raw-taf)
          tafs (str/replace tafs #"PROB30 TEMPO" "PROB30 TEMPX")
          tafs (str/replace tafs #"PROB40 TEMPO" "PROB40 TEMPX")
          taf-items (map str/trim (str/split tafs #"(?=FM|BECMG|PROB[34]0 TEMPX|PROB[34]0 \d|TEMPO)"))
          taf-items (map #(str/replace % "TEMPX" "TEMPO") taf-items)
          taf-lines (concat [taf-name] taf-items)]
      (map (fn [line] {:line line :color (weather-color line)}) taf-lines))))

(defn remove-airport-code [metar-line]
  (if (<= (count (:line metar-line)) 9)
    metar-line
    (let [line (:line metar-line)
          prefix (subs line 0 5)
          suffix (subs line 11)
          new-line (str prefix " " suffix)]
      (assoc metar-line :line new-line))
    ))

(defn make-taf-screen []
  (let [taf-response (get @comm/polled-tafs config/taf-airport)
        metar-history (map utils/shorten-metar @comm/polled-metar-history)
        metar-history (map remove-airport-code metar-history)
        raw-tafs [(:rawTAF taf-response)]
        tafs (flatten (map #(->> % split-taf (take 8)) raw-tafs))
        blank-line {:line "" :color config/info-color}
        airport-id {:line (str config/airport " METAR HISTORY") :color config/info-color}]
    (concat tafs [blank-line airport-id] metar-history)))

(defmethod screen/make :taf [_]
  (make-taf-screen))

(defmethod screen/header-text :taf [_]
  "WEATHER")

(defmethod screen/display-column-headers :taf [_ _flap-width _header-font _label-font-size]
  nil)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T10:30:05.220056-05:00", :module-hash "-37358912", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "1580021120"} {:id "defn/parse-taf-visibility", :kind "defn", :line 9, :end-line 16, :hash "-1921450894"} {:id "defn/parse-taf-ceiling", :kind "defn", :line 18, :end-line 23, :hash "430755056"} {:id "defn/weather-color", :kind "defn", :line 25, :end-line 30, :hash "-1648758696"} {:id "defn/split-taf", :kind "defn", :line 32, :end-line 41, :hash "-659386333"} {:id "defn/remove-airport-code", :kind "defn", :line 43, :end-line 51, :hash "-519134946"} {:id "defn/make-taf-screen", :kind "defn", :line 53, :end-line 61, :hash "-398287311"} {:id "defmethod/screen/make/:taf", :kind "defmethod", :line 63, :end-line 64, :hash "-289838186"} {:id "defmethod/screen/header-text/:taf", :kind "defmethod", :line 66, :end-line 67, :hash "840642812"} {:id "defmethod/screen/display-column-headers/:taf", :kind "defmethod", :line 69, :end-line 70, :hash "290734131"}]}
;; clj-mutate-manifest-end
