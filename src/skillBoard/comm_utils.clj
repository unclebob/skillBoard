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
      (core-utils/log :error (str "Error fetching " error-name ": " (.getMessage e)))
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
      (core-utils/log :error (str "Error fetching nearby METAR cache: " (.getMessage e)))
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
      (core-utils/log :error (str "Error fetching class airspace cache: " (.getMessage e)))
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-09T09:16:24.404408-05:00", :module-hash "1621461269", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 11, :hash "-2125359585"} {:id "defn/get-json", :kind "defn", :line 13, :end-line 28, :hash "-383794933"} {:id "def/polled-reservations", :kind "def", :line 30, :end-line 30, :hash "-2038606744"} {:id "def/reservation-com-errors", :kind "def", :line 31, :end-line 31, :hash "-703572397"} {:id "def/polled-flights", :kind "def", :line 33, :end-line 33, :hash "-1445651490"} {:id "def/polled-aircraft", :kind "def", :line 35, :end-line 35, :hash "-502410825"} {:id "def/polled-metars", :kind "def", :line 37, :end-line 37, :hash "-254318940"} {:id "def/polled-nearby-metars", :kind "def", :line 38, :end-line 38, :hash "-483957812"} {:id "def/polled-airspace-classes", :kind "def", :line 39, :end-line 39, :hash "-893780428"} {:id "def/polled-metar-history", :kind "def", :line 40, :end-line 40, :hash "262909719"} {:id "def/polled-tafs", :kind "def", :line 41, :end-line 41, :hash "128404539"} {:id "def/weather-com-errors", :kind "def", :line 42, :end-line 42, :hash "314184653"} {:id "def/open-meteo-ok?", :kind "def", :line 43, :end-line 43, :hash "994953994"} {:id "def/nearby-metar-cache-url", :kind "def", :line 45, :end-line 45, :hash "1222060626"} {:id "def/class-airspace-cache-url", :kind "def", :line 46, :end-line 46, :hash "-1540350450"} {:id "def/polled-adsbs", :kind "def", :line 48, :end-line 48, :hash "1142360635"} {:id "def/adsb-com-errors", :kind "def", :line 49, :end-line 49, :hash "-1612519048"} {:id "def/polled-nearby-adsbs", :kind "def", :line 51, :end-line 51, :hash "1028216413"} {:id "defn/get-reservations", :kind "defn", :line 53, :end-line 69, :hash "-804855619"} {:id "defn/get-flights", :kind "defn", :line 71, :end-line 86, :hash "-1348836426"} {:id "def/last-aircraft", :kind "def", :line 88, :end-line 88, :hash "1991563116"} {:id "defn/get-aircraft", :kind "defn", :line 89, :end-line 99, :hash "-271092184"} {:id "def/last-metars", :kind "def", :line 101, :end-line 101, :hash "-740820498"} {:id "defn/icao-query", :kind "defn", :line 102, :end-line 106, :hash "-2083230312"} {:id "defn/keyed-weather-response", :kind "defn", :line 108, :end-line 111, :hash "1952013841"} {:id "defn/get-keyed-aviation-weather", :kind "defn", :line 113, :end-line 120, :hash "1851856986"} {:id "defn/get-metars", :kind "defn", :line 122, :end-line 123, :hash "913355856"} {:id "defn/csv-fields", :kind "defn", :line 125, :end-line 143, :hash "566988855"} {:id "defn-/blank->nil", :kind "defn-", :line 145, :end-line 147, :hash "650081978"} {:id "defn-/parse-double-field", :kind "defn-", :line 149, :end-line 151, :hash "-102605435"} {:id "defn-/header-key", :kind "defn-", :line 153, :end-line 156, :hash "-1027870260"} {:id "defn/unique-csv-headers", :kind "defn", :line 158, :end-line 166, :hash "1298102905"} {:id "defn/csv->maps", :kind "defn", :line 168, :end-line 171, :hash "364338142"} {:id "defn-/parse-int-field", :kind "defn-", :line 173, :end-line 175, :hash "724271242"} {:id "def/cloud-layer-fields", :kind "def", :line 177, :end-line 181, :hash "1870597967"} {:id "defn-/cloud-layer", :kind "defn-", :line 183, :end-line 186, :hash "-1109549921"} {:id "defn/metar-cache-clouds", :kind "defn", :line 188, :end-line 192, :hash "-802594760"} {:id "defn/metar-cache-record->metar", :kind "defn", :line 194, :end-line 203, :hash "-969598968"} {:id "defn-/distance-nm", :kind "defn-", :line 205, :end-line 208, :hash "1510266979"} {:id "defn/nearby-metars", :kind "defn", :line 210, :end-line 213, :hash "-556511224"} {:id "defn-/gzip-bytes->string", :kind "defn-", :line 215, :end-line 217, :hash "1022468683"} {:id "defn-/zip-entry->string", :kind "defn-", :line 219, :end-line 225, :hash "-585721031"} {:id "defn/get-nearby-metars", :kind "defn", :line 227, :end-line 245, :hash "-1192990823"} {:id "defn-/truthy-flag?", :kind "defn-", :line 247, :end-line 248, :hash "-461990189"} {:id "defn/airport-id->icao-id", :kind "defn", :line 250, :end-line 255, :hash "2132908997"} {:id "defn/airspace-record->class", :kind "defn", :line 257, :end-line 262, :hash "318838241"} {:id "defn/airspace-record->entry", :kind "defn", :line 264, :end-line 266, :hash "310748658"} {:id "defn/class-airspace-records->classes", :kind "defn", :line 268, :end-line 269, :hash "-908790267"} {:id "defn/get-airspace-classes", :kind "defn", :line 271, :end-line 288, :hash "424489702"} {:id "defn/get-metar-history", :kind "defn", :line 293, :end-line 298, :hash "1732946021"} {:id "defn/get-tafs", :kind "defn", :line 307, :end-line 308, :hash "1086556519"} {:id "defn/get-adsb-by-tail-numbers", :kind "defn", :line 310, :end-line 319, :hash "-1666675534"} {:id "defn/get-nearby-adsb", :kind "defn", :line 321, :end-line 339, :hash "-81892658"}]}
;; clj-mutate-manifest-end
