(ns skillBoard.comm-utils
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [java-time.api :as time]
    [skillBoard.config :as config]
    [skillBoard.core-utils :as core-utils])
  (:import
    (java.io ByteArrayInputStream)
    (java.util.zip GZIPInputStream ZipInputStream)))

(defn get-json [url args save-atom com-errors error-name]
  (try
    (let [{:keys [status body]} (http/get url args)
          body (if (nil? body)
                 (throw (ex-info (str "Nil body fetching " error-name) {}))
                 (json/read-str body :key-fn keyword))]
      (if (= status 200)
        (do
          (reset! save-atom body)
          (reset! com-errors 0)
          @save-atom)
        (throw (ex-info (str "Failed to fetch " error-name) {:status status}))))
    (catch Exception e
      (core-utils/log-event :error :communication-issue
                            (str "Error fetching " error-name ": " (.getMessage e)))
      (swap! com-errors inc)
      @save-atom)))

(def polled-reservations (atom {}))
(def reservation-com-errors (atom 0))

(def polled-flights (atom {}))

(def polled-aircraft (atom {}))

(def polled-metars (atom {}))
(def polled-nearby-metars (atom {}))
(def polled-airspace-classes (atom {}))
(def polled-metar-history (atom {}))
(def polled-tafs (atom {}))
(def weather-com-errors (atom 0))
(def open-meteo-ok? (atom true))

(def nearby-metar-cache-url "https://aviationweather.gov/data/cache/metars.cache.csv.gz")
(def class-airspace-cache-url "https://nfdc.faa.gov/webContent/28DaySub/extra/16_Apr_2026_CLS_ARSP_CSV.zip")

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
              :socket-timeout 5000
              :connection-timeout 5000}]
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
              :socket-timeout 5000
              :connection-timeout 5000}]
    (get-json url args polled-flights reservation-com-errors "flights")))

(def last-aircraft (atom []))
(defn get-aircraft []
  (let [operator-id (:fsp-operator-id @config/config)
        fsp-key (:fsp-key @config/config)
        url (str "https://usc-api.flightschedulepro.com/core/v1.0/operators/" operator-id "/aircraft")
        args {:headers {"x-subscription-key" fsp-key}
              :socket-timeout 5000
              :connection-timeout 5000}
        response (get-json url args last-aircraft reservation-com-errors "aircraft")
        aircraft (filter #(= "Active" (get-in % [:status :name])) (:items response))
        tail-numbers (map #(get % :tailNumber) aircraft)]
    (reset! polled-aircraft tail-numbers)))

(def last-metars (atom {}))
(defn icao-query [icao]
  (let [icao-str (if (sequential? icao)
                   (str/join "," (map str/upper-case icao))
                   (str/upper-case icao))]
    icao-str))

(defn keyed-weather-response [response]
  (if (sequential? response)
    (into {} (map (fn [m] [(:icaoId m) m]) response))
    {(:icaoId response) response}))

(defn get-keyed-aviation-weather [kind polled-atom icao]
  (let [url (str "https://aviationweather.gov/api/data/" kind
                 "?ids=" (icao-query icao) "&format=json")
        args {:accept :text :with-credentials? false}
        response (get-json url args polled-atom weather-com-errors (str/upper-case kind))
        keyed (keyed-weather-response response)]
    (reset! polled-atom keyed)
    keyed))

(defn get-metars [icao]
  (get-keyed-aviation-weather "metar" polled-metars icao))

(defn csv-fields [line]
  (loop [chars (seq line)
         field []
         fields []
         quoted? false]
    (if-let [ch (first chars)]
      (cond
        (and (= \" ch) quoted? (= \" (second chars)))
        (recur (nnext chars) (conj field ch) fields quoted?)

        (= \" ch)
        (recur (next chars) field fields (not quoted?))

        (and (= \, ch) (not quoted?))
        (recur (next chars) [] (conj fields (apply str field)) quoted?)

        :else
        (recur (next chars) (conj field ch) fields quoted?))
      (conj fields (apply str field)))))

(defn- blank->nil [s]
  (when-not (or (str/blank? s) (= "null" s))
    s))

(defn- parse-double-field [s]
  (when-let [value (blank->nil s)]
    (Double/parseDouble value)))

(defn- header-key [header occurrence]
  (keyword (if (= 1 occurrence)
             header
             (str header "_" occurrence))))

(defn unique-csv-headers [headers]
  (:headers
    (reduce (fn [{:keys [counts] :as state} header]
              (let [occurrence (inc (get counts header 0))]
                (-> state
                    (update :counts assoc header occurrence)
                    (update :headers conj (header-key header occurrence)))))
            {:counts {} :headers []}
            headers)))

(defn csv->maps [csv-text]
  (let [lines (remove str/blank? (str/split-lines csv-text))
        headers (unique-csv-headers (csv-fields (first lines)))]
    (map #(zipmap headers (csv-fields %)) (rest lines))))

(defn- parse-int-field [s]
  (when-let [value (blank->nil s)]
    (Integer/parseInt value)))

(def cloud-layer-fields
  [[:sky_cover :cloud_base_ft_agl]
   [:sky_cover_2 :cloud_base_ft_agl_2]
   [:sky_cover_3 :cloud_base_ft_agl_3]
   [:sky_cover_4 :cloud_base_ft_agl_4]])

(defn- cloud-layer [record [cover-key base-key]]
  (when-let [cover (blank->nil (cover-key record))]
    (cond-> {:cover cover}
      (parse-int-field (base-key record)) (assoc :base (parse-int-field (base-key record))))))

(defn metar-cache-clouds [record]
  (let [clouds (keep #(cloud-layer record %) cloud-layer-fields)]
    (cond-> (vec clouds)
      (parse-int-field (:vert_vis_ft record))
      (conj {:cover "VV" :base (parse-int-field (:vert_vis_ft record))}))))

(defn metar-cache-record->metar [record]
  (when-let [station-id (blank->nil (:station_id record))]
    (when-let [lat (parse-double-field (:latitude record))]
      (when-let [lon (parse-double-field (:longitude record))]
        {:icaoId station-id
         :lat lat
         :lon lon
         :fltCat (blank->nil (:flight_category record))
         :clouds (metar-cache-clouds record)
         :rawOb (:raw_text record)}))))

(defn- distance-nm [[center-lat center-lon] {:keys [lat lon]}]
  (let [lat-nm (* 60.0 (- lat center-lat))
        lon-nm (* 60.0 (Math/cos (Math/toRadians center-lat)) (- lon center-lon))]
    (Math/sqrt (+ (* lat-nm lat-nm) (* lon-nm lon-nm)))))

(defn nearby-metars [metars center radius-nm]
  (->> metars
       (filter #(<= (distance-nm center %) radius-nm))
       (sort-by :icaoId)))

(defn- gzip-bytes->string [bytes]
  (with-open [stream (GZIPInputStream. (ByteArrayInputStream. bytes))]
    (slurp stream)))

(defn- zip-entry->string [bytes entry-name]
  (with-open [zip (ZipInputStream. (ByteArrayInputStream. bytes))]
    (loop [entry (.getNextEntry zip)]
      (cond
        (nil? entry) nil
        (= entry-name (.getName entry)) (slurp zip)
        :else (recur (.getNextEntry zip))))))

(defn get-nearby-metars []
  (try
    (let [{:keys [status body]} (http/get nearby-metar-cache-url
                                          {:accept :octet-stream
                                           :as :byte-array
                                           :socket-timeout 5000
                                           :connection-timeout 5000})]
      (if (= 200 status)
        (let [all-metars (keep metar-cache-record->metar (csv->maps (gzip-bytes->string body)))
              metars (nearby-metars all-metars config/airport-lat-lon config/wind-map-radius-nm)
              metar-dict (into {} (map (fn [m] [(:icaoId m) m]) metars))]
          (reset! polled-nearby-metars metar-dict)
          (reset! weather-com-errors 0)
          metar-dict)
        (throw (ex-info "Failed to fetch nearby METAR cache" {:status status}))))
    (catch Exception e
      (core-utils/log-event :error :communication-issue
                            (str "Error fetching nearby METAR cache: " (.getMessage e)))
      (swap! weather-com-errors inc)
      @polled-nearby-metars)))

(defn- truthy-flag? [value]
  (= "Y" (str/upper-case (or value ""))))

(defn airport-id->icao-id [airport-id]
  (let [airport-id (str/upper-case airport-id)]
    (if (and (= 3 (count airport-id))
             (re-matches #"[A-Z][A-Z0-9]{2}" airport-id))
      (str "K" airport-id)
      airport-id)))

(defn airspace-record->class [record]
  (cond
    (truthy-flag? (:CLASS_B_AIRSPACE record)) "B"
    (truthy-flag? (:CLASS_C_AIRSPACE record)) "C"
    (truthy-flag? (:CLASS_D_AIRSPACE record)) "D"
    :else nil))

(defn airspace-record->entry [record]
  (when-let [airspace-class (airspace-record->class record)]
    [(airport-id->icao-id (:ARPT_ID record)) airspace-class]))

(defn class-airspace-records->classes [records]
  (into {} (keep airspace-record->entry records)))

(defn get-airspace-classes []
  (try
    (let [{:keys [status body]} (http/get class-airspace-cache-url
                                          {:accept :octet-stream
                                           :as :byte-array
                                           :socket-timeout 5000
                                           :connection-timeout 5000})]
      (if (= 200 status)
        (let [csv (zip-entry->string body "CLS_ARSP.csv")
              classes (class-airspace-records->classes (csv->maps csv))]
          (reset! polled-airspace-classes classes)
          (reset! weather-com-errors 0)
          classes)
        (throw (ex-info "Failed to fetch class airspace cache" {:status status}))))
    (catch Exception e
      (core-utils/log-event :error :communication-issue
                            (str "Error fetching class airspace cache: " (.getMessage e)))
      (swap! weather-com-errors inc)
      @polled-airspace-classes)))

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
  (get-keyed-aviation-weather "taf" polled-tafs icao))

(defn get-adsb-by-tail-numbers [tail-numbers]
  (let [tails (map #(str "icao=" %) tail-numbers)
        tails (str/join \& (set tails))
        url (str "http://" config/radar-cape-ip "/aircraftlist.json?" tails)
        args {:accept :text
              :with-credentials? false
              :socket-timeout 5000
              :connection-timeout 5000}
        adsb-response (get-json url args polled-adsbs adsb-com-errors "ADSB")]
    adsb-response))

(defn get-nearby-adsb []
  (let [[min-alt max-alt] config/nearby-altitude-range
        url (str "http://" config/radar-cape-ip "/aircraftlist.json")
        args {:accept :text
              :with-credentials? false
              :socket-timeout 5000
              :connection-timeout 5000}
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-07T11:25:38.864907-05:00", :module-hash "-729723197", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 11, :hash "-2125359585"} {:id "defn/get-json", :kind "defn", :line 13, :end-line 29, :hash "-844154161"} {:id "def/polled-reservations", :kind "def", :line 31, :end-line 31, :hash "-2038606744"} {:id "def/reservation-com-errors", :kind "def", :line 32, :end-line 32, :hash "-703572397"} {:id "def/polled-flights", :kind "def", :line 34, :end-line 34, :hash "-1445651490"} {:id "def/polled-aircraft", :kind "def", :line 36, :end-line 36, :hash "-502410825"} {:id "def/polled-metars", :kind "def", :line 38, :end-line 38, :hash "-254318940"} {:id "def/polled-nearby-metars", :kind "def", :line 39, :end-line 39, :hash "-483957812"} {:id "def/polled-airspace-classes", :kind "def", :line 40, :end-line 40, :hash "-893780428"} {:id "def/polled-metar-history", :kind "def", :line 41, :end-line 41, :hash "262909719"} {:id "def/polled-tafs", :kind "def", :line 42, :end-line 42, :hash "128404539"} {:id "def/weather-com-errors", :kind "def", :line 43, :end-line 43, :hash "314184653"} {:id "def/open-meteo-ok?", :kind "def", :line 44, :end-line 44, :hash "994953994"} {:id "def/nearby-metar-cache-url", :kind "def", :line 46, :end-line 46, :hash "1222060626"} {:id "def/class-airspace-cache-url", :kind "def", :line 47, :end-line 47, :hash "-1540350450"} {:id "def/polled-adsbs", :kind "def", :line 49, :end-line 49, :hash "1142360635"} {:id "def/adsb-com-errors", :kind "def", :line 50, :end-line 50, :hash "-1612519048"} {:id "def/polled-nearby-adsbs", :kind "def", :line 52, :end-line 52, :hash "1028216413"} {:id "defn/get-reservations", :kind "defn", :line 54, :end-line 70, :hash "-804855619"} {:id "defn/get-flights", :kind "defn", :line 72, :end-line 87, :hash "-1348836426"} {:id "def/last-aircraft", :kind "def", :line 89, :end-line 89, :hash "1991563116"} {:id "defn/get-aircraft", :kind "defn", :line 90, :end-line 100, :hash "-271092184"} {:id "def/last-metars", :kind "def", :line 102, :end-line 102, :hash "-740820498"} {:id "defn/icao-query", :kind "defn", :line 103, :end-line 107, :hash "-2083230312"} {:id "defn/keyed-weather-response", :kind "defn", :line 109, :end-line 112, :hash "1952013841"} {:id "defn/get-keyed-aviation-weather", :kind "defn", :line 114, :end-line 121, :hash "1851856986"} {:id "defn/get-metars", :kind "defn", :line 123, :end-line 124, :hash "913355856"} {:id "defn/csv-fields", :kind "defn", :line 126, :end-line 144, :hash "566988855"} {:id "defn-/blank->nil", :kind "defn-", :line 146, :end-line 148, :hash "650081978"} {:id "defn-/parse-double-field", :kind "defn-", :line 150, :end-line 152, :hash "-102605435"} {:id "defn-/header-key", :kind "defn-", :line 154, :end-line 157, :hash "-1027870260"} {:id "defn/unique-csv-headers", :kind "defn", :line 159, :end-line 167, :hash "1298102905"} {:id "defn/csv->maps", :kind "defn", :line 169, :end-line 172, :hash "364338142"} {:id "defn-/parse-int-field", :kind "defn-", :line 174, :end-line 176, :hash "724271242"} {:id "def/cloud-layer-fields", :kind "def", :line 178, :end-line 182, :hash "1870597967"} {:id "defn-/cloud-layer", :kind "defn-", :line 184, :end-line 187, :hash "-1109549921"} {:id "defn/metar-cache-clouds", :kind "defn", :line 189, :end-line 193, :hash "-802594760"} {:id "defn/metar-cache-record->metar", :kind "defn", :line 195, :end-line 204, :hash "-969598968"} {:id "defn-/distance-nm", :kind "defn-", :line 206, :end-line 209, :hash "1510266979"} {:id "defn/nearby-metars", :kind "defn", :line 211, :end-line 214, :hash "-556511224"} {:id "defn-/gzip-bytes->string", :kind "defn-", :line 216, :end-line 218, :hash "1022468683"} {:id "defn-/zip-entry->string", :kind "defn-", :line 220, :end-line 226, :hash "-585721031"} {:id "defn/get-nearby-metars", :kind "defn", :line 228, :end-line 247, :hash "-1463843222"} {:id "defn-/truthy-flag?", :kind "defn-", :line 249, :end-line 250, :hash "-461990189"} {:id "defn/airport-id->icao-id", :kind "defn", :line 252, :end-line 257, :hash "2132908997"} {:id "defn/airspace-record->class", :kind "defn", :line 259, :end-line 264, :hash "318838241"} {:id "defn/airspace-record->entry", :kind "defn", :line 266, :end-line 268, :hash "310748658"} {:id "defn/class-airspace-records->classes", :kind "defn", :line 270, :end-line 271, :hash "-908790267"} {:id "defn/get-airspace-classes", :kind "defn", :line 273, :end-line 291, :hash "-1844126810"} {:id "defn/get-metar-history", :kind "defn", :line 296, :end-line 301, :hash "1732946021"} {:id "defn/get-tafs", :kind "defn", :line 310, :end-line 311, :hash "1086556519"} {:id "defn/get-adsb-by-tail-numbers", :kind "defn", :line 313, :end-line 322, :hash "-1666675534"} {:id "defn/get-nearby-adsb", :kind "defn", :line 324, :end-line 342, :hash "-81892658"}]}
;; clj-mutate-manifest-end
