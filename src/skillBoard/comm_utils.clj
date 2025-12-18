(ns skillBoard.comm-utils
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [java-time.api :as time]
    [skillBoard.config :as config]
    [skillBoard.core-utils :as core-utils]))

(defn get-json [url args save-atom com-errors error-name]
  (try
    (let [{:keys [status body]} (http/get url args)
          body (json/read-str body :key-fn keyword)]
      (if (= status 200)
        (do
          (reset! save-atom body)
          (reset! com-errors 0)
          @save-atom)
        (throw (ex-info (str "Failed to fetch " error-name) {:status status}))))
    (catch Exception e
      (core-utils/log (str "Error fetching " error-name ": " (.getMessage e)))
      (swap! com-errors inc)
      @save-atom)))

(def polled-reservations (atom {}))
(def reservation-com-errors (atom 0))

(def polled-flights (atom {}))

(def polled-aircraft (atom {}))

(def polled-metars (atom {}))
(def polled-metar-history (atom {}))
(def polled-tafs (atom {}))
(def weather-com-errors (atom 0))

(def polled-adsbs (atom {}))
(def adsb-com-errors (atom 0))

(def polled-nearby-adsbs (atom {}))

(defn get-reservations []
  (let [operator-id (:fsp-operator-id @config/config)
        fsp-key (:fsp-key @config/config)
        today (time/local-date)
        tomorrow (time/plus today (time/days 1))
        yesterday (time/minus today (time/days 1))
        start-time (time/format "yyyy-MM-dd" yesterday)
        end-time (time/format "yyyy-MM-dd" tomorrow)
        url (str "https://usc-api.flightschedulepro.com/scheduling/v1.0/operators/" operator-id
                 "/reservations"
                 "?startTime=gte:" start-time
                 "&endTime=lt:" end-time
                 "&limit=200")
        args {:headers {"x-subscription-key" fsp-key}
              :socket-timeout 2000
              :connection-timeout 2000}]
    (get-json url args polled-reservations reservation-com-errors "reservations")))

(defn get-flights []
  (let [operator-id (:fsp-operator-id @config/config)
        fsp-key (:fsp-key @config/config)
        today (time/local-date)
        tomorrow (time/plus today (time/days 1))
        start-time (time/format "yyyy-MM-dd" today)
        end-time (time/format "yyyy-MM-dd" tomorrow)
        url (str "https://usc-api.flightschedulepro.com/reports/v1.0/operators/" operator-id
                 "/flights" "?flightDate=gte:" start-time
                 "&flightDateRangeEndDate=lt:" end-time
                 "&limit=200"
                 )
        args {:headers {"x-subscription-key" fsp-key}
              :socket-timeout 2000
              :connection-timeout 2000}]
    (get-json url args polled-flights reservation-com-errors "flights")))

(def last-aircraft (atom []))
(defn get-aircraft []
  (let [operator-id (:fsp-operator-id @config/config)
        fsp-key (:fsp-key @config/config)
        url (str "https://usc-api.flightschedulepro.com/core/v1.0/operators/" operator-id "/aircraft")
        args {:headers {"x-subscription-key" fsp-key}
              :socket-timeout 2000
              :connection-timeout 2000}
        response (get-json url args last-aircraft reservation-com-errors "aircraft")
        aircraft (filter #(= "Active" (get-in % [:status :name])) (:items response))
        tail-numbers (map #(get % :tailNumber) aircraft)]
    (reset! polled-aircraft tail-numbers)))

(def last-metars (atom {}))
(defn get-metars [icao]
  (let [icao-str (if (sequential? icao)
                   (str/join "," (map str/upper-case icao))
                   (str/upper-case icao))
        url (str "https://aviationweather.gov/api/data/metar?ids=" icao-str "&format=json")
        args {:accept :text :with-credentials? false}
        metar-response (get-json url args last-metars weather-com-errors "METAR")]
    (let [metar-dict (if (sequential? metar-response)
                       (into {} (map (fn [m] [(:icaoId m) m]) metar-response))
                       {(:icaoId metar-response) metar-response})]
      (reset! polled-metars metar-dict)
      metar-dict)))

;{"KBMI" {:rawOb "METAR KBMI 181456Z 18023KT 1 1/2SM BR OVC003 08/08 A2951 RMK AO2 PK WND 17028/1456 SLP996 60001 T00830083 58026", :wdir 180, :qcField 4, :temp 8.3, :visib 1.5, :wspd 23, :name "Bloomington Rgnl, IL, US", :wxString "BR", :cover "OVC", :metarType "METAR", :obsTime 1766069760, :elev 262, :receiptTime "2025-12-18T14:57:17.197Z", :reportTime "2025-12-18T15:00:00.000Z", :pcp3hr 0.01, :lon -88.9144, :icaoId "KBMI", :lat 40.4777, :clouds [{:cover "OVC", :base 300}], :presTend -2.6, :slp 999.6, :dewp 8.3, :altim 999.4, :fltCat "LIFR"},
; "KMDW" {:rawOb "METAR KMDW 181453Z 19022G33KT 8SM -RA OVC008 08/07 A2950 RMK AO2 PK WND 21037/1354 SLP995 P0000 60002 T00830072 58025", :wdir 190, :qcField 4, :temp 8.3, :visib 8, :wspd 22, :name "Chicago/Midway Intl, IL, US", :wxString "-RA", :cover "OVC", :metarType "METAR", :wgst 33, :obsTime 1766069580, :elev 186, :receiptTime "2025-12-18T14:56:31.777Z", :reportTime "2025-12-18T15:00:00.000Z", :pcp3hr 0.02, :lon -87.7552, :icaoId "KMDW", :lat 41.7841, :clouds [{:cover "OVC", :base 800}], :presTend -2.5, :slp 999.5, :precip 0.005, :dewp 7.2, :altim 999.1, :fltCat "IFR"}}

(defn get-metar-history [icao]
  (let [icao-str (str/upper-case icao)
        url (str "https://aviationweather.gov/api/data/metar?ids=" icao-str "&format=json&hours=4")
        args {:accept :text :with-credentials? false}
        metar-response (get-json url args polled-metar-history weather-com-errors "METAR")]
    metar-response))

;[{:rawOb "METAR KUGN 181451Z 20018G26KT 8SM OVC006 08/07 A2943 RMK AO2 PK WND 20032/1434 SLP973 T00830072 56030", :wdir 200, :qcField 4, :temp 8.3, :visib 8, :wspd 18, :name "Waukegan Rgnl, IL, US", :cover "OVC", :metarType "METAR", :wgst 26, :obsTime 1766069460, :elev 217, :receiptTime "2025-12-18T14:53:15.647Z", :reportTime "2025-12-18T15:00:00.000Z", :lon -87.8634, :icaoId "KUGN", :lat 42.4255, :clouds [{:cover "OVC", :base 600}], :presTend -3, :slp 997.3, :dewp 7.2, :altim 996.7, :fltCat "IFR"}
; {:rawOb "METAR KUGN 181351Z 19019G33KT 3SM BR OVC005 07/07 A2945 RMK AO2 PK WND 18033/1344 SLP978 T00720072", :wdir 190, :qcField 4, :temp 7.2, :visib 3, :wspd 19, :name "Waukegan Rgnl, IL, US", :wxString "BR", :cover "OVC", :metarType "METAR", :wgst 33, :obsTime 1766065860, :elev 217, :receiptTime "2025-12-18T13:56:33.087Z", :reportTime "2025-12-18T14:00:00.000Z", :lon -87.8634, :icaoId "KUGN", :lat 42.4255, :clouds [{:cover "OVC", :base 500}], :slp 997.8, :dewp 7.2, :altim 997.4, :fltCat "IFR"}
; {:rawOb "SPECI KUGN 181321Z 19017G25KT 2SM BR OVC005 07/07 A2947 RMK AO2 PK WND 19026/1300 T00670067", :wdir 190, :qcField 4, :temp 6.7, :visib 2, :wspd 17, :name "Waukegan Rgnl, IL, US", :wxString "BR", :cover "OVC", :metarType "SPECI", :wgst 25, :obsTime 1766064060, :elev 217, :receiptTime "2025-12-18T13:24:08.626Z", :reportTime "2025-12-18T13:21:00.000Z", :lon -87.8634, :icaoId "KUGN", :lat 42.4255, :clouds [{:cover "OVC", :base 500}], :dewp 6.7, :altim 998.1, :fltCat "IFR"}
; {:rawOb "METAR KUGN 181251Z 19015KT 4SM BR OVC005 06/06 A2950 RMK AO2 SLP998 T00610061", :wdir 190, :qcField 4, :temp 6.1, :visib 4, :wspd 15, :name "Waukegan Rgnl, IL, US", :wxString "BR", :cover "OVC", :metarType "METAR", :obsTime 1766062260, :elev 217, :receiptTime "2025-12-18T12:56:34.626Z", :reportTime "2025-12-18T13:00:00.000Z", :lon -87.8634, :icaoId "KUGN", :lat 42.4255, :clouds [{:cover "OVC", :base 500}], :slp 999.8, :dewp 6.1, :altim 999.1, :fltCat "IFR"}
; {:rawOb "METAR KUGN 181151Z 19015G22KT 6SM BR OVC005 06/06 A2952 RMK AO2 PK WND 17026/1106 SLP005 60000 T00610056 10061 20022 58032", :wdir 190, :qcField 4, :temp 6.1, :visib 6, :wspd 15, :name "Waukegan Rgnl, IL, US", :wxString "BR", :cover "OVC", :metarType "METAR", :wgst 22, :obsTime 1766058660, :elev 217, :receiptTime "2025-12-18T11:56:23.298Z", :reportTime "2025-12-18T12:00:00.000Z", :lon -87.8634, :minT 2.2, :icaoId "KUGN", :lat 42.4255, :maxT 6.1, :clouds [{:cover "OVC", :base 500}], :pcp6hr 0.005, :presTend -3.2, :slp 1000.5, :dewp 5.6, :altim 999.7, :fltCat "IFR"}
; {:rawOb "SPECI KUGN 181109Z AUTO 18014G26KT 9SM OVC008 06/06 A2953 RMK AO2 PK WND 17026/1106 CIG 006V010 T00610056", :wdir 180, :qcField 6, :temp 6.1, :visib 9, :wspd 14, :name "Waukegan Rgnl, IL, US", :cover "OVC", :metarType "SPECI", :wgst 26, :obsTime 1766056140, :elev 217, :receiptTime "2025-12-18T11:12:07.629Z", :reportTime "2025-12-18T11:09:00.000Z", :lon -87.8634, :icaoId "KUGN", :lat 42.4255, :clouds [{:cover "OVC", :base 800}], :dewp 5.6, :altim 1000.1, :fltCat "IFR"}]

(defn get-tafs [icao]
  (let [icao-str (if (sequential? icao)
                   (str/join "," (map str/upper-case icao))
                   (str/upper-case icao))
        url (str "https://aviationweather.gov/api/data/taf?ids=" icao-str "&format=json")
        args {:accept :text :with-credentials? false}
        taf-response (get-json url args polled-tafs weather-com-errors "TAF")]
    (let [taf-dict (if (sequential? taf-response)
                     (into {} (map (fn [m] [(:icaoId m) m]) taf-response))
                     {(:icaoId taf-response) taf-response})]
      (reset! polled-tafs taf-dict)
      taf-dict)))

(defn get-adsb-by-tail-numbers [tail-numbers]
  (let [tails (map #(str "icao=" %) tail-numbers)
        tails (str/join \& (set tails))
        url (str "http://" config/radar-cape-ip "/aircraftlist.json?" tails)
        args {:accept :text
              :with-credentials? false
              :socket-timeout 2000
              :connection-timeout 2000}
        adsb-response (get-json url args polled-adsbs adsb-com-errors "ADSB")]
    adsb-response))

(defn get-nearby-adsb []
  (let [[min-alt max-alt] config/nearby-altitude-range
        url (str "http://" config/radar-cape-ip "/aircraftlist.json")
        args {:accept :text
              :with-credentials? false
              :socket-timeout 2000
              :connection-timeout 2000}
        all-adsb (get-json url args polled-nearby-adsbs adsb-com-errors "nearby ADSB")
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