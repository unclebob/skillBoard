(ns skillBoard.gateways.radar-cape-spec
  (:require
    [skillBoard.foundation.config :as config]
    [skillBoard.gateways.comm-utils :as comm]
    [skillBoard.gateways.radar-cape :as radar]
    [speclj.core :refer :all]))

(describe "ADSB fetches"
  (it "fetches ADSB data by tail number query"
    (let [captured-url (atom nil)
          captured-args (atom nil)
          captured-save-atom (atom nil)
          captured-source (atom nil)]
      (with-redefs [comm/get-json (fn [url args save-atom _error-handler source]
                                    (reset! captured-url url)
                                    (reset! captured-args args)
                                    (reset! captured-save-atom save-atom)
                                    (reset! captured-source source)
                                    [{:reg "N12345"}])
                    config/radar-cape-ip "radar.local"]
        (should= [{:reg "N12345"}] (radar/get-adsb-by-tail-numbers ["N12345" "N67890"]))
        (should-contain "http://radar.local/aircraftlist.json?" @captured-url)
        (should-contain "icao=N12345" @captured-url)
        (should-contain "icao=N67890" @captured-url)
        (should= {:accept :text
                  :with-credentials? false
                  :socket-timeout 5000
                  :connection-timeout 5000}
                 @captured-args)
        (should= radar/polled-adsbs @captured-save-atom)
        (should= "ADSB" @captured-source))))

  (it "filters nearby ADSB data by distance and altitude"
    (with-redefs [comm/get-json (fn [& _]
                                  [{:reg "KEEP" :alt 2000 :dis 10}
                                   {:reg "TOO-FAR" :alt 2000 :dis 99}
                                   {:reg "TOO-LOW" :alt -1 :dis 10}
                                   {:reg "NO-DIST" :alt 2000}])
                  radar/polled-nearby-adsbs (atom [])
                  config/nearby-altitude-range [0 4000]
                  config/nearby-distance 40]
      (should= [{:reg "KEEP" :alt 2000 :dis 10}]
               (radar/get-nearby-adsb))
      (should= [{:reg "KEEP" :alt 2000 :dis 10}]
               @radar/polled-nearby-adsbs)))

  (it "keeps an aircraft at the bottom of the configured altitude range"
    (with-redefs [comm/get-json (fn [& _]
                                  [{:reg "FLOOR" :alt 0 :dis 10}
                                   {:reg "BELOW" :alt -1 :dis 10}])
                  radar/polled-nearby-adsbs (atom [])]
      (should= [{:reg "FLOOR" :alt 0 :dis 10}]
               (radar/get-nearby-adsb)))))
