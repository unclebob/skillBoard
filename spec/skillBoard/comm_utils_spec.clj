(ns skillBoard.comm-utils-spec
  (:require
    [clj-http.client :as http]
    [java-time.api :as time]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.core-utils :as core-utils]
    [speclj.core :refer :all]
    )
  (:import
    (java.io ByteArrayOutputStream)
    (java.util.zip GZIPOutputStream ZipEntry ZipOutputStream)))


(declare save-atom com-errors)
(describe "API Utils"
  (context "get-json"
    (with save-atom (atom nil))
    (with com-errors (atom 0))
    (with-stubs)
    (it "accesses the API, converts to JSON, and saves"
      (with-redefs [http/get (stub :get {:return {:status 200 :body "{\"key\": \"value\"}"}})
                    prn (stub :prn)]
        (reset! @save-atom :none)
        (should= {:key "value"} (comm/get-json :url :args @save-atom @com-errors "test data"))
        (should-have-invoked :get
                             {:times 1
                              :with [:* :*]})
        (should-not-have-invoked :prn)
        (should= 0 @@com-errors)
        ))

    (it "prints and counts API errors, returns saved data"
      (with-redefs [http/get (stub :get {:return {:status 500 :body "Server Error"}})
                    core-utils/log (stub :log)]
        (reset! @save-atom :none)
        (should= :none (comm/get-json :url :args @save-atom @com-errors "test data"))
        (should-have-invoked :get
                             {:times 1
                              :with [:* :*]})
        (should-have-invoked :log
                             {:times 1})
        (should= 1 @@com-errors)
        ))
    )
  )

(def frozen-today (time/local-date 2025 12 03))

(describe "get-reservations"
  (with-stubs)

  (it "calls comm/get-json with correct arguments including save-atom and error handler"
    (let [captured-url (atom nil)
          captured-args (atom nil)
          captured-save-atom (atom nil)
          captured-error-handler (atom nil)
          captured-source (atom nil)]
      (with-redefs [time/local-date (constantly frozen-today)
                    config/config (atom {:fsp-operator-id "OP123"
                                         :fsp-key "fake-key"})
                    comm/get-json (fn [url args save-atom error-handler source]
                                    (reset! captured-url url)
                                    (reset! captured-args args)
                                    (reset! captured-save-atom save-atom)
                                    (reset! captured-error-handler error-handler)
                                    (reset! captured-source source)
                                    {:data :mocked})]
        (let [result (comm/get-reservations)]
          (should= {:data :mocked} result)
          (should-contain "?startTime=gte:2025-12-02" @captured-url)
          (should-contain "&endTime=lt:2025-12-04" @captured-url)
          (should-contain "/operators/OP123/reservations?" @captured-url)
          (should-contain "&limit=200" @captured-url)
          (should= comm/polled-reservations @captured-save-atom)
          (should= comm/reservation-com-errors @captured-error-handler)
          (should= "reservations" @captured-source)
          (should= {:headers {"x-subscription-key" "fake-key"},
                    :socket-timeout 2000,
                    :connection-timeout 2000}
                   @captured-args)))))
  )

(describe "get-flights"
  (with-stubs)
  (it "calls comm/get-json with correct arguments including save-atom and error handler"
    (let [captured-url (atom nil)
          captured-args (atom nil)
          captured-save-atom (atom nil)
          captured-error-handler (atom nil)
          captured-source (atom nil)]
      (with-redefs [time/local-date (constantly frozen-today)
                    config/config (atom {:fsp-operator-id "OP123"
                                         :fsp-key "fake-key"})
                    comm/get-json (fn [url args save-atom error-handler source]
                                    (reset! captured-url url)
                                    (reset! captured-args args)
                                    (reset! captured-save-atom save-atom)
                                    (reset! captured-error-handler error-handler)
                                    (reset! captured-source source)
                                    {:data :mocked})]
        (let [result (comm/get-flights)]
          (should= {:data :mocked} result)
          (should-contain "/operators/OP123/flights?" @captured-url)
          (should-contain "&limit=200" @captured-url)
          (should-contain "flightDate=gte:2025-12-03" @captured-url)
          (should-contain "flightDateRangeEndDate=lt:2025-12-04" @captured-url)
          (should-contain "limit=200" @captured-url)
          (should= comm/polled-flights @captured-save-atom)
          (should= comm/reservation-com-errors @captured-error-handler)
          (should= "flights" @captured-source)
          (should= {:headers {"x-subscription-key" "fake-key"},
                    :socket-timeout 2000,
                    :connection-timeout 2000}
                   @captured-args)))))
  )

(describe "get-aircraft"
  (with-stubs)
  (it "calls comm/get-json with correct arguments including save-atom and error handler"
    (let [captured-url (atom nil)
          captured-args (atom nil)
          captured-error-handler (atom nil)
          captured-source (atom nil)]
      (with-redefs [time/local-date (constantly frozen-today)
                    config/config (atom {:fsp-operator-id "OP123"
                                         :fsp-key "fake-key"})
                    comm/get-json (fn [url args _save-atom error-handler source]
                                    (reset! captured-url url)
                                    (reset! captured-args args)
                                    (reset! captured-error-handler error-handler)
                                    (reset! captured-source source)
                                    {:items [{:status {:name "Active"}
                                              :tailNumber "tail1"}]})]
        (let [result (comm/get-aircraft)]
          (should= ["tail1"] result)
          (should= ["tail1"] @comm/polled-aircraft)
          (should-contain "/operators/OP123/aircraft" @captured-url)
          (should= comm/reservation-com-errors @captured-error-handler)
          (should= "aircraft" @captured-source)
          (should= {:headers {"x-subscription-key" "fake-key"},
                    :socket-timeout 2000,
                    :connection-timeout 2000}
                   @captured-args)))))
  )

(defn gzip-bytes [text]
  (let [out (ByteArrayOutputStream.)
        bytes (.getBytes text "UTF-8")]
    (with-open [gzip (GZIPOutputStream. out)]
      (.write gzip bytes 0 (alength bytes)))
    (.toByteArray out)))

(defn zip-bytes [entry-name text]
  (let [out (ByteArrayOutputStream.)
        bytes (.getBytes text "UTF-8")]
    (with-open [zip (ZipOutputStream. out)]
      (.putNextEntry zip (ZipEntry. entry-name))
      (.write zip bytes 0 (alength bytes))
      (.closeEntry zip))
    (.toByteArray out)))

(describe "nearby METAR cache"
  (with-stubs)

  (it "parses quoted CSV fields"
    (should= ["METAR KUGN 211853Z, AUTO" "KUGN" "42.4255"]
             (comm/csv-fields "\"METAR KUGN 211853Z, AUTO\",KUGN,42.4255")))

  (it "converts METAR cache records to the display shape"
    (should= {:icaoId "KUGN"
              :lat 42.4255
              :lon -87.8634
              :fltCat "MVFR"
              :rawOb "METAR KUGN"}
             (comm/metar-cache-record->metar {:station_id "KUGN"
                                               :latitude "42.4255"
                                               :longitude "-87.8634"
                                               :flight_category "MVFR"
                                               :raw_text "METAR KUGN"})))

  (it "filters METARs to the configured radius"
    (let [metars [{:icaoId "KUGN" :lat 42.4255 :lon -87.8634}
                  {:icaoId "KORD" :lat 41.9786 :lon -87.9048}
                  {:icaoId "KLAX" :lat 33.9425 :lon -118.4081}]]
      (should= ["KORD" "KUGN"]
               (map :icaoId (comm/nearby-metars metars config/airport-lat-lon 200)))))

  (it "fetches the compressed METAR cache and stores nearby airports"
    (let [csv "raw_text,station_id,observation_time,latitude,longitude,flight_category\n\"METAR KUGN\",KUGN,2026-04-21T18:00:00.000Z,42.4255,-87.8634,VFR\n\"METAR KLAX\",KLAX,2026-04-21T18:00:00.000Z,33.9425,-118.4081,IFR\n"
          captured-url (atom nil)]
      (with-redefs [http/get (fn [url _args]
                               (reset! captured-url url)
                               {:status 200
                                :body (gzip-bytes csv)})
                    comm/polled-nearby-metars (atom {})
                    comm/weather-com-errors (atom 1)]
        (should= {"KUGN" {:icaoId "KUGN"
                          :lat 42.4255
                          :lon -87.8634
                          :fltCat "VFR"
                          :rawOb "METAR KUGN"}}
                 (comm/get-nearby-metars))
        (should= comm/nearby-metar-cache-url @captured-url)
        (should= 0 @comm/weather-com-errors)))))

(describe "class airspace cache"
  (it "normalizes NASR airport ids to METAR ids"
    (should= "KORD" (comm/airport-id->icao-id "ORD"))
    (should= "K06C" (comm/airport-id->icao-id "K06C"))
    (should= "CYYZ" (comm/airport-id->icao-id "CYYZ")))

  (it "selects B, C, and D airspace classes in priority order"
    (should= "B" (comm/airspace-record->class {:CLASS_B_AIRSPACE "Y"
                                                :CLASS_C_AIRSPACE "Y"
                                                :CLASS_D_AIRSPACE "Y"}))
    (should= "C" (comm/airspace-record->class {:CLASS_C_AIRSPACE "Y"
                                                :CLASS_D_AIRSPACE "Y"}))
    (should= "D" (comm/airspace-record->class {:CLASS_D_AIRSPACE "Y"}))
    (should-be-nil (comm/airspace-record->class {:CLASS_E_AIRSPACE "Y"})))

  (it "builds a lookup of B, C, and D airports"
    (should= {"KORD" "B"
              "KMKE" "C"
              "KUGN" "D"}
             (comm/class-airspace-records->classes
               [{:ARPT_ID "ORD" :CLASS_B_AIRSPACE "Y"}
                {:ARPT_ID "MKE" :CLASS_C_AIRSPACE "Y"}
                {:ARPT_ID "UGN" :CLASS_D_AIRSPACE "Y"}
                {:ARPT_ID "ENW" :CLASS_E_AIRSPACE "Y"}])))

  (it "fetches the compressed class airspace cache"
    (let [csv "\"EFF_DATE\",\"ARPT_ID\",\"CLASS_B_AIRSPACE\",\"CLASS_C_AIRSPACE\",\"CLASS_D_AIRSPACE\",\"CLASS_E_AIRSPACE\"\n\"2026/04/16\",\"ORD\",\"Y\",\"\",\"\",\"\"\n\"2026/04/16\",\"MKE\",\"\",\"Y\",\"\",\"\"\n\"2026/04/16\",\"UGN\",\"\",\"\",\"Y\",\"\"\n"
          captured-url (atom nil)]
      (with-redefs [http/get (fn [url _args]
                               (reset! captured-url url)
                               {:status 200
                                :body (zip-bytes "CLS_ARSP.csv" csv)})
                    comm/polled-airspace-classes (atom {})
                    comm/weather-com-errors (atom 1)]
        (should= {"KORD" "B"
                  "KMKE" "C"
                  "KUGN" "D"}
                 (comm/get-airspace-classes))
        (should= comm/class-airspace-cache-url @captured-url)
        (should= 0 @comm/weather-com-errors)))))
