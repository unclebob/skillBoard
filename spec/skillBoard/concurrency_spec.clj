(ns skillBoard.concurrency-spec
  (:require
    [java-time.api :as time]
    [skillBoard.atoms :as atoms]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.core :as core]
    [skillBoard.core-utils :as core-utils]
    [skillBoard.presenters.airports]
    [skillBoard.presenters.flights]
    [skillBoard.presenters.main :as main]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.presenters.traffic]
    [skillBoard.presenters.weather]
    [skillBoard.presenters.wind-map :as wind-map]
    [skillBoard.wind-data :as wind-data]
    [speclj.core :refer :all]))

(defn- jitter! [^java.util.Random rng max-ms]
  (when (zero? (.nextInt rng 3))
    (Thread/yield))
  (let [delay (.nextInt rng (inc max-ms))]
    (when (pos? delay)
      (Thread/sleep delay))))

(defn- metar [airport n]
  {:icaoId airport
   :fltCat (nth ["VFR" "MVFR" "IFR" "LIFR"] (mod n 4))
   :visib 10
   :cover "BKN"
   :clouds [{:base (+ 1000 (* 100 (mod n 20)))}]
   :wspd (+ 5 (mod n 20))
   :wgst (when (odd? n) (+ 15 (mod n 15)))
   :lat (+ 42.0 (/ (mod n 20) 100.0))
   :lon (- -87.8 (/ (mod n 20) 100.0))
   :rawOb (str "METAR " airport " 011200Z 26008KT 10SM BKN030 18/12 A2992")})

(defn- reservation [n]
  {:reservationId (str "reservation-" n)
   :aircraft {:tailNumber (str "N" (+ 100 n) "TS")}
   :activityType {:name "Flight"}
   :startTime (time/format "yyyy-MM-dd'T'HH:mm:ss.SS"
                           (time/plus (time/local-date-time 2025 12 3 16 0)
                                      (time/minutes n)))
   :pilots [{:firstName "Pat" :lastName (str "Pilot" n)}]
   :instructor {:firstName "Chris" :lastName "Coach"}})

(defn- flight [n]
  {:reservationId (str "reservation-" n)
   :checkedOutOn (time/format "yyyy-MM-dd'T'HH:mm:ss.SS"
                              (time/local-date-time 2025 12 3 16 0))})

(defn- adsb [n]
  {:reg (str "N" (+ 100 n) "TS")
   :lat (+ 42.45 (/ (mod n 10) 100.0))
   :lon (- -87.85 (/ (mod n 10) 100.0))
   :alt (+ 1200 (* 100 (mod n 8)))
   :altg (+ 1200 (* 100 (mod n 8)))
   :spd (+ 80 (mod n 30))
   :gda "A"
   :dis (+ 1 (mod n 8))})

(defn- wind-grid [n]
  {:source :synthetic
   :generated-at-ms n
   :center config/airport-lat-lon
   :radius-nm config/wind-map-radius-nm
   :points [{:lat 42.4 :lon -87.8 :u 10.0 :v 4.0}
            {:lat 42.5 :lon -87.9 :u 12.0 :v 5.0}]})

(defn- valid-screen? [screen]
  (and (sequential? screen)
       (every? #(and (map? %)
                     (contains? % :line)
                     (contains? % :color))
               screen)))

(defn- future-result [label f]
  (future
    (try
      (f)
      {:label label :status :ok}
      (catch Throwable t
        {:label label :status :error :throwable t}))))

(defn- assert-future-result [seed result]
  (should-not= ::timeout result)
  (when (= :error (:status result))
    (throw (ex-info (str "race stress future failed for seed " seed
                         " in " (:label result))
                    {:seed seed :label (:label result)}
                    (:throwable result)))))

(defn- run-race-stress! [seed]
  (let [max-ms 5
        iterations 50
        writer-rng (java.util.Random. seed)
        reader-rng (java.util.Random. (+ seed 100000))
        toggler-rng (java.util.Random. (+ seed 200000))
        polled-reservations (atom {:items []})
        polled-flights (atom {:items []})
        polled-aircraft (atom [])
        polled-metars (atom {})
        polled-nearby-metars (atom {})
        polled-airspace-classes (atom {})
        polled-metar-history (atom [])
        polled-tafs (atom {})
        polled-adsbs (atom [])
        polled-nearby-adsbs (atom [])
        polled-wind-grid (atom nil)
        display-info (atom {:line-count 8
                            :top-margin 20
                            :label-height 10
                            :sf-char-gap 2
                            :font-width 10
                            :font-height 20})
        screens (atom (cycle [{:screen :flights :duration 1}
                              {:screen :traffic :duration 1}
                              {:screen :taf :duration 1}
                              {:screen :airports :duration 1}
                              {:screen :wind-map :duration 1}]))
        test? (atom false)
        change-screen? (atom false)
        screen-changed? (atom true)
        log-traffic? (atom false)]
    (with-redefs [comm/polled-reservations polled-reservations
                  comm/polled-flights polled-flights
                  comm/polled-aircraft polled-aircraft
                  comm/polled-metars polled-metars
                  comm/polled-nearby-metars polled-nearby-metars
                  comm/polled-airspace-classes polled-airspace-classes
                  comm/polled-metar-history polled-metar-history
                  comm/polled-tafs polled-tafs
                  comm/polled-adsbs polled-adsbs
                  comm/polled-nearby-adsbs polled-nearby-adsbs
                  comm/reservation-com-errors (atom 0)
                  comm/weather-com-errors (atom 0)
                  comm/adsb-com-errors (atom 0)
                  comm/open-meteo-ok? (atom true)
                  wind-data/polled-wind-grid polled-wind-grid
                  config/display-info display-info
                  config/screens screens
                  atoms/test? test?
                  atoms/change-screen? change-screen?
                  atoms/screen-changed? screen-changed?
                  atoms/log-traffic? log-traffic?
                  core-utils/log (fn [& _])
                  main/screen-type (atom :flights)
                  main/screen-duration (atom 1)
                  main/screen-start-time (atom 0)
                  comm/get-aircraft (fn []
                                      (jitter! writer-rng max-ms)
                                      (reset! polled-aircraft [(str "N" (+ 100 (.nextInt writer-rng 50)) "TS")]))
                  comm/get-adsb-by-tail-numbers (fn [_]
                                                  (jitter! writer-rng max-ms)
                                                  (reset! polled-adsbs (mapv adsb (range (.nextInt writer-rng 20)))))
                  comm/get-flights (fn []
                                     (jitter! writer-rng max-ms)
                                     (reset! polled-flights {:items (mapv flight (range (.nextInt writer-rng 20)))}))
                  comm/get-reservations (fn []
                                          (jitter! writer-rng max-ms)
                                          (reset! polled-reservations {:items (mapv reservation (range (.nextInt writer-rng 20)))}))
                  comm/get-metars (fn [_]
                                    (jitter! writer-rng max-ms)
                                    (reset! polled-metars {"KUGN" (metar "KUGN" (.nextInt writer-rng 100))
                                                           "KMDW" (metar "KMDW" (.nextInt writer-rng 100))}))
                  comm/get-nearby-metars (fn []
                                           (jitter! writer-rng max-ms)
                                           (reset! polled-nearby-metars {"KUGN" (metar "KUGN" (.nextInt writer-rng 100))
                                                                         "KPWK" (metar "KPWK" (.nextInt writer-rng 100))}))
                  comm/get-airspace-classes (fn []
                                              (jitter! writer-rng max-ms)
                                              (reset! polled-airspace-classes {"KUGN" "D" "KMDW" "C" "KPWK" "D"}))
                  comm/get-metar-history (fn [_]
                                           (jitter! writer-rng max-ms)
                                           (reset! polled-metar-history [(metar "KUGN" (.nextInt writer-rng 100))
                                                                         (metar "KUGN" (.nextInt writer-rng 100))]))
                  comm/get-tafs (fn [_]
                                  (jitter! writer-rng max-ms)
                                  (reset! polled-tafs {"KUGN" {:rawTAF "TAF KUGN 011130Z 0112/0212 26008KT P6SM BKN030"}}))
                  comm/get-nearby-adsb (fn []
                                         (jitter! writer-rng max-ms)
                                         (reset! polled-nearby-adsbs (mapv adsb (range (.nextInt writer-rng 20)))))
                  wind-data/refresh-wind-grid-if-due! (fn []
                                                        (jitter! writer-rng max-ms)
                                                        (reset! polled-wind-grid (wind-grid (.nextInt writer-rng 100000))))]
      (let [writers [(future-result :poll-writer
                                    #(dotimes [_ iterations]
                                       (jitter! writer-rng max-ms)
                                       (core/poll)))
                     (future-result :screen-toggle-writer
                                    #(dotimes [_ iterations]
                                       (jitter! toggler-rng max-ms)
                                       (reset! change-screen? (zero? (.nextInt toggler-rng 4)))
                                       (reset! screen-changed? (zero? (.nextInt toggler-rng 2)))
                                       (reset! log-traffic? false)))]
            readers [(future-result :screen-reader
                                    #(dotimes [_ iterations]
                                       (jitter! reader-rng max-ms)
                                       (doseq [screen-type [:flights :traffic :taf :airports :wind-map]]
                                         (should (valid-screen? (screen/make screen-type))))))
                     (future-result :main-reader
                                    #(dotimes [_ iterations]
                                       (jitter! reader-rng max-ms)
                                       (should (valid-screen? (main/make-screen)))))
                     (future-result :wind-reader
                                    #(dotimes [_ iterations]
                                       (jitter! reader-rng max-ms)
                                       (should (vector? (wind-map/flight-category-airport-markers)))
                                       (should (map? (wind-data/current-grid)))))]
            results (mapv #(deref % 10000 ::timeout) (concat writers readers))]
        (doseq [result results]
          (assert-future-result seed result))))))

(describe "concurrent polling and screen generation"
  (it "does not throw while polling updates race with screen reads"
    (doseq [seed [10001 10002 10003]]
      (run-race-stress! seed))))
