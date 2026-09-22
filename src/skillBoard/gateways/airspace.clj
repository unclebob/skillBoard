(ns skillBoard.gateways.airspace
  (:require
    [clj-http.client :as http]
    [clojure.string :as str]
    [skillBoard.foundation.core-utils :as core-utils]
    [skillBoard.gateways.comm-utils :as comm]))

(def polled-airspace-classes (atom {}))
(def airspace-com-errors (atom 0))

(def class-airspace-cache-url "https://nfdc.faa.gov/webContent/28DaySub/extra/16_Apr_2026_CLS_ARSP_CSV.zip")

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
        (let [csv (comm/zip-entry->string body "CLS_ARSP.csv")
              classes (class-airspace-records->classes (comm/csv->maps csv))]
          (reset! polled-airspace-classes classes)
          (reset! airspace-com-errors 0)
          classes)
        (throw (ex-info "Failed to fetch class airspace cache" {:status status}))))
    (catch Exception e
      (core-utils/log-event :error :communication-issue
                            (str "Error fetching class airspace cache: " (.getMessage e)))
      (swap! airspace-com-errors inc)
      @polled-airspace-classes)))
