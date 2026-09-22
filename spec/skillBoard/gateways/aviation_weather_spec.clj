(ns skillBoard.gateways.aviation-weather-spec
  (:require
    [clj-http.client :as http]
    [skillBoard.foundation.config :as config]
    [skillBoard.foundation.core-utils :as core-utils]
    [skillBoard.gateways.aviation-weather :as weather]
    [skillBoard.gateways.comm-utils :as comm]
    [speclj.core :refer :all])
  (:import
    (java.io ByteArrayOutputStream)
    (java.util.zip GZIPOutputStream)))

(defn- gzip-bytes [text]
  (let [out (ByteArrayOutputStream.)
        bytes (.getBytes text "UTF-8")]
    (with-open [gzip (GZIPOutputStream. out)]
      (.write gzip bytes 0 (alength bytes)))
    (.toByteArray out)))

(describe "aviation weather fetches"
  (it "formats aviation weather ICAO queries"
    (should= "KUGN" (weather/icao-query "kugn"))
    (should= "KUGN,KORD" (weather/icao-query ["kugn" "kord"])))

  (it "keys single and sequential aviation weather responses by ICAO"
    (should= {"KUGN" {:icaoId "KUGN" :rawOb "METAR KUGN"}}
             (weather/keyed-weather-response {:icaoId "KUGN" :rawOb "METAR KUGN"}))
    (should= {"KUGN" {:icaoId "KUGN"}
              "KORD" {:icaoId "KORD"}}
             (weather/keyed-weather-response [{:icaoId "KUGN"} {:icaoId "KORD"}]))))

  (it "fetches keyed aviation weather and stores it in the requested atom"
    (let [polled (atom {})
          captured-url (atom nil)
          captured-args (atom nil)
          captured-save-atom (atom nil)
          captured-source (atom nil)]
      (with-redefs [comm/get-json (fn [url args save-atom _error-handler source]
                                    (reset! captured-url url)
                                    (reset! captured-args args)
                                    (reset! captured-save-atom save-atom)
                                    (reset! captured-source source)
                                    [{:icaoId "KUGN"} {:icaoId "KORD"}])]
        (should= {"KUGN" {:icaoId "KUGN"}
                  "KORD" {:icaoId "KORD"}}
                 (weather/get-keyed-aviation-weather "metar" polled ["kugn" "kord"]))
        (should= "https://aviationweather.gov/api/data/metar?ids=KUGN,KORD&format=json"
                 @captured-url)
        (should= {:accept :text :with-credentials? false} @captured-args)
        (should= polled @captured-save-atom)
        (should= "METAR" @captured-source)
        (should= {"KUGN" {:icaoId "KUGN"}
                  "KORD" {:icaoId "KORD"}}
                 @polled))))

  (it "routes METAR and TAF requests through the keyed weather fetcher"
    (let [calls (atom [])]
      (with-redefs [weather/get-keyed-aviation-weather (fn [& args]
                                                         (swap! calls conj args)
                                                         {:ok true})]
        (should= {:ok true} (weather/get-metars "kugn"))
        (should= {:ok true} (weather/get-tafs ["kugn"]))
        (should= [["metar" weather/polled-metars "kugn"]
                  ["taf" weather/polled-tafs ["kugn"]]]
                 @calls))))

  (it "fetches METAR history for the requested airport"
    (let [captured-url (atom nil)
          captured-args (atom nil)
          captured-save-atom (atom nil)
          captured-source (atom nil)]
      (with-redefs [comm/get-json (fn [url args save-atom _error-handler source]
                                    (reset! captured-url url)
                                    (reset! captured-args args)
                                    (reset! captured-save-atom save-atom)
                                    (reset! captured-source source)
                                    [{:icaoId "KUGN"}])]
        (should= [{:icaoId "KUGN"}] (weather/get-metar-history "kugn"))
        (should= "https://aviationweather.gov/api/data/metar?ids=KUGN&format=json&hours=4"
                 @captured-url)
        (should= {:accept :text :with-credentials? false} @captured-args)
        (should= weather/polled-metar-history @captured-save-atom)
        (should= "METAR" @captured-source))))

(describe "nearby METAR cache"
  (it "converts METAR cache records to the display shape"
    (should= {:icaoId "KUGN"
              :lat 42.4255
              :lon -87.8634
              :fltCat "MVFR"
              :clouds [{:cover "FEW" :base 1200}
                       {:cover "BKN" :base 2500}
                       {:cover "VV" :base 300}]
              :rawOb "METAR KUGN"}
             (weather/metar-cache-record->metar {:station_id "KUGN"
                                                 :latitude "42.4255"
                                                 :longitude "-87.8634"
                                                 :flight_category "MVFR"
                                                 :sky_cover "FEW"
                                                 :cloud_base_ft_agl "1200"
                                                 :sky_cover_2 "BKN"
                                                 :cloud_base_ft_agl_2 "2500"
                                                 :vert_vis_ft "300"
                                                 :raw_text "METAR KUGN"})))

  (it "filters METARs to the configured radius"
    (let [metars [{:icaoId "KUGN" :lat 42.4255 :lon -87.8634}
                  {:icaoId "KORD" :lat 41.9786 :lon -87.9048}
                  {:icaoId "KLAX" :lat 33.9425 :lon -118.4081}]]
      (should= ["KORD" "KUGN"]
               (map :icaoId (weather/nearby-metars metars config/airport-lat-lon 200)))))

  (it "fetches the compressed METAR cache and stores nearby airports"
    (let [csv "raw_text,station_id,observation_time,latitude,longitude,sky_cover,cloud_base_ft_agl,sky_cover,cloud_base_ft_agl,flight_category\n\"METAR KUGN\",KUGN,2026-04-21T18:00:00.000Z,42.4255,-87.8634,FEW,1200,OVC,900,VFR\n\"METAR KLAX\",KLAX,2026-04-21T18:00:00.000Z,33.9425,-118.4081,OVC,500,,,,IFR\n"
          captured-url (atom nil)]
      (with-redefs [http/get (fn [url _args]
                               (reset! captured-url url)
                               {:status 200
                                :body (gzip-bytes csv)})
                    weather/polled-nearby-metars (atom {})
                    weather/weather-com-errors (atom 1)]
        (should= {"KUGN" {:icaoId "KUGN"
                          :lat 42.4255
                          :lon -87.8634
                          :fltCat "VFR"
                          :clouds [{:cover "FEW" :base 1200}
                                   {:cover "OVC" :base 900}]
                          :rawOb "METAR KUGN"}}
                 (weather/get-nearby-metars))
        (should= weather/nearby-metar-cache-url @captured-url)
        (should= 0 @weather/weather-com-errors))))

  (it "marks METAR cache failures as communication issues"
    (let [events (atom [])]
      (with-redefs [http/get (fn [& _] {:status 503})
                    weather/polled-nearby-metars (atom {:last :good})
                    weather/weather-com-errors (atom 0)
                    core-utils/log-event (fn [& args] (swap! events conj args))]
        (should= {:last :good} (weather/get-nearby-metars))
        (should= 1 @weather/weather-com-errors)
        (should= [[:error
                   :communication-issue
                   "Error fetching nearby METAR cache: Failed to fetch nearby METAR cache"]]
                 @events)))))
