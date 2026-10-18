(ns skillBoard.adapters.web.view
  (:require
    [skillBoard.adapters.split-flap :as split-flap]
    [skillBoard.foundation.config :as config]
    [skillBoard.presenters.airports :as airports]
    [skillBoard.presenters.flights :as flights]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.presenters.traffic :as traffic]
    [skillBoard.presenters.weather :as weather]
    [skillBoard.presenters.wind-map :as wind-map]
    [skillBoard.adapters.web.wind :as wind]))

(def flap-advance-chance 0.8)

(defn- row-count [rows]
  (max 3 (int (or rows 3))))

(defn- with-line-count [rows f]
  (let [previous (:line-count @config/display-info)]
    (swap! config/display-info assoc :line-count (row-count rows))
    (try
      (f)
      (finally
        (if (some? previous)
          (swap! config/display-info assoc :line-count previous)
          (swap! config/display-info dissoc :line-count))))))

(defn- paint-line [{:keys [line color]}]
  {:text (or line "")
   :rgb (or (get split-flap/color-rgb color) split-flap/default-color-rgb)})

(defn- columns [screen-key]
  (mapv (fn [[label col]] {:label label :col col})
        (or (screen/column-headers screen-key) [])))

(defn- text-screen [screen-key lines]
  {:header (screen/header-text screen-key)
   :columns (columns screen-key)
   :lines (mapv paint-line lines)})

(defn- lights [com-errors]
  (let [com-errors (or com-errors {})]
    {:reservations (split-flap/status-light-rgb (or (:reservations com-errors) 0))
     :adsb (split-flap/status-light-rgb (or (:adsb com-errors) 0))
     :weather (split-flap/weather-status-light-rgb com-errors)}))

(defn- sequence-view []
  (mapv (fn [{:keys [screen duration]}]
          {:screen (name screen) :duration duration})
        (take 8 @config/screens)))

(defn- flap-chars []
  (loop [character \A
         seen []]
    (if (some #(= % character) seen)
      (mapv str seen)
      (recur (split-flap/get-next-char character) (conj seen character)))))

(defn- flap []
  {:chars (flap-chars)
   :duration-ms config/flap-duration
   :frame-ms (max 1 (quot 1000 config/frame-rate))
   :steps config/flap-steps-per-update
   :chance flap-advance-chance})

(defn- screens [snapshot]
  {:flights (text-screen :flights (flights/make-flights-screen
                                    (:reservations snapshot)
                                    (:flights snapshot)
                                    snapshot))
   :taf (text-screen :taf (weather/make-taf-screen snapshot))
   :traffic (text-screen :traffic (traffic/traffic-lines
                                    (:nearby-adsbs snapshot)
                                    (:aircraft snapshot)
                                    snapshot))
   :airports (text-screen :airports (airports/make-airports-screen snapshot))
   :wind-map (text-screen :wind-map (wind-map/make-wind-map-screen))})

(defn board-view
  ([snapshot rows width height]
   (let [snapshot (or snapshot {})
         width (max 1 (int (or width 1)))
         height (max 1 (int (or height 1)))]
     (with-line-count rows
       (fn []
         {:version config/version
          :cols config/cols
          :rows (row-count rows)
          :width width
          :height height
          :sequence (sequence-view)
          :flap (flap)
          :lights (lights (:com-errors snapshot))
          :screens (assoc-in (screens snapshot)
                             [:wind-map :scene]
                             (wind/scene snapshot width height))})))))
