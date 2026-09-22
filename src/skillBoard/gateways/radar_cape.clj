(ns skillBoard.gateways.radar-cape
  (:require
    [clojure.string :as str]
    [skillBoard.foundation.config :as config]
    [skillBoard.gateways.comm-utils :as comm]))

(def polled-adsbs (atom {}))
(def polled-nearby-adsbs (atom {}))
(def adsb-com-errors (atom 0))

(defn get-adsb-by-tail-numbers [tail-numbers]
  (let [tails (map #(str "icao=" %) tail-numbers)
        tails (str/join \& (set tails))
        url (str "http://" config/radar-cape-ip "/aircraftlist.json?" tails)
        args {:accept :text
              :with-credentials? false
              :socket-timeout 5000
              :connection-timeout 5000}]
    (comm/get-json url args polled-adsbs adsb-com-errors "ADSB")))

(defn get-nearby-adsb []
  (let [[min-alt max-alt] config/nearby-altitude-range
        url (str "http://" config/radar-cape-ip "/aircraftlist.json")
        args {:accept :text
              :with-credentials? false
              :socket-timeout 5000
              :connection-timeout 5000}
        all-adsb (comm/get-json url args polled-nearby-adsbs adsb-com-errors "nearby ADSB")
        nearby (filter (fn [aircraft]
                         (let [alt (:alt aircraft)
                               dist (:dis aircraft)
                               valid? (and (some? alt) (some? dist))]
                           (and valid?
                                (< dist config/nearby-distance)
                                (>= alt min-alt)
                                (<= alt max-alt))))
                       all-adsb)]
    (reset! polled-nearby-adsbs nearby)
    nearby))
