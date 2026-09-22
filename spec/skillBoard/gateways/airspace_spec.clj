(ns skillBoard.gateways.airspace-spec
  (:require
    [clj-http.client :as http]
    [skillBoard.foundation.core-utils :as core-utils]
    [skillBoard.gateways.airspace :as airspace]
    [speclj.core :refer :all])
  (:import
    (java.io ByteArrayOutputStream)
    (java.util.zip ZipEntry ZipOutputStream)))

(defn- zip-bytes [entry-name text]
  (let [out (ByteArrayOutputStream.)
        bytes (.getBytes text "UTF-8")]
    (with-open [zip (ZipOutputStream. out)]
      (.putNextEntry zip (ZipEntry. entry-name))
      (.write zip bytes 0 (alength bytes))
      (.closeEntry zip))
    (.toByteArray out)))

(describe "class airspace cache"
  (it "normalizes NASR airport ids to METAR ids"
    (should= "KORD" (airspace/airport-id->icao-id "ORD"))
    (should= "K06C" (airspace/airport-id->icao-id "K06C"))
    (should= "CYYZ" (airspace/airport-id->icao-id "CYYZ")))

  (it "selects B, C, and D airspace classes in priority order"
    (should= "B" (airspace/airspace-record->class {:CLASS_B_AIRSPACE "Y"
                                                   :CLASS_C_AIRSPACE "Y"
                                                   :CLASS_D_AIRSPACE "Y"}))
    (should= "C" (airspace/airspace-record->class {:CLASS_C_AIRSPACE "Y"
                                                   :CLASS_D_AIRSPACE "Y"}))
    (should= "D" (airspace/airspace-record->class {:CLASS_D_AIRSPACE "Y"}))
    (should-be-nil (airspace/airspace-record->class {:CLASS_E_AIRSPACE "Y"})))

  (it "builds a lookup of B, C, and D airports"
    (should= {"KORD" "B"
              "KMKE" "C"
              "KUGN" "D"}
             (airspace/class-airspace-records->classes
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
                    airspace/polled-airspace-classes (atom {})
                    airspace/airspace-com-errors (atom 1)]
        (should= {"KORD" "B"
                  "KMKE" "C"
                  "KUGN" "D"}
                 (airspace/get-airspace-classes))
        (should= airspace/class-airspace-cache-url @captured-url)
        (should= 0 @airspace/airspace-com-errors))))

  (it "marks class airspace cache failures as communication issues"
    (let [events (atom [])]
      (with-redefs [http/get (fn [& _] {:status 503})
                    airspace/polled-airspace-classes (atom {:last :good})
                    airspace/airspace-com-errors (atom 0)
                    core-utils/log-event (fn [& args] (swap! events conj args))]
        (should= {:last :good} (airspace/get-airspace-classes))
        (should= 1 @airspace/airspace-com-errors)
        (should= [[:error
                   :communication-issue
                   "Error fetching class airspace cache: Failed to fetch class airspace cache"]]
                 @events)))))
