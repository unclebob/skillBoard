(ns skillBoard.heartbeat
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [skillBoard.config :as config]
    [skillBoard.core-utils :as core-utils])
  (:import
    (java.nio.file Files)
    (java.time ZoneId ZonedDateTime)
    (java.time.format DateTimeFormatter)))

(def heartbeat-interval-ms (* config/heartbeat-interval-minutes 60 1000))
(def heartbeat-timeout-ms 10000)
(def retry-offsets-ms [(* 60 1000) (* 5 60 1000)])

(def scheduler-state (atom nil))

(defn local-now []
  (ZonedDateTime/now (ZoneId/of config/time-zone)))

(defn- log-message [line]
  (second (re-matches #"^\S+ (.*)$" line)))

(defn- matching-lines [file predicate]
  (if-not (.exists file)
    0
    (with-open [reader (io/reader file)]
      (count (filter (comp predicate log-message) (line-seq reader))))))

(defn daily-counts [date]
  (let [status-log (io/file (core-utils/log-file-path :status date))
        error-log (io/file (core-utils/log-file-path :error date))]
    {:aircraft-reports
     (matching-lines status-log #(str/starts-with? (or % "") "Traffic:"))

     :communication-issues
     (matching-lines error-log #(str/starts-with? (or % "") "Error fetching "))

     :application-starts
     (matching-lines status-log #(boolean (re-matches #"skillBoard v.+ has begun\." (or % ""))))}))

(defn disk-capacity [path]
  (let [store (Files/getFileStore (.toPath (.getCanonicalFile (io/file path))))]
    {:usable-bytes (.getUsableSpace store)
     :total-bytes (.getTotalSpace store)}))

(defn disk-summary [{:keys [usable-bytes total-bytes]}]
  {:usable_bytes usable-bytes
   :total_bytes total-bytes
   :usable_percent (when (pos? total-bytes)
                     (/ (double (Math/round (* 1000.0 (/ usable-bytes total-bytes))))
                        10.0))})

(defn snapshot
  ([] (snapshot (local-now)))
  ([reported-at]
   (let [{:keys [aircraft-reports communication-issues application-starts]}
         (daily-counts (.toLocalDate reported-at))]
     {:application "skillBoard"
      :version config/version
      :reported_at (.format reported-at DateTimeFormatter/ISO_OFFSET_DATE_TIME)
      :disk (disk-summary (disk-capacity core-utils/log-directory))
      :today {:aircraft_reports aircraft-reports
              :communication_issues communication-issues
              :application_starts application-starts}})))

(defn- configured-url []
  (:heartbeat-url @config/config))

(defn- valid-url? [url]
  (and (not (str/blank? url))
       (str/starts-with? url "https://")))

(defn send-heartbeat! []
  (let [url (configured-url)
        response (http/post url
                            {:body (json/write-str (snapshot))
                             :content-type :json
                             :accept :text
                             :connection-timeout heartbeat-timeout-ms
                             :socket-timeout heartbeat-timeout-ms
                             :throw-exceptions false})
        status (:status response)]
    (and (number? status) (<= 200 status 299))))

(defn submit! [work]
  (future (work)))

(defn- safe-log [level message]
  (try
    (core-utils/log level message)
    (catch Exception _)))

(defn- next-regular-time [scheduled-at now]
  (loop [next-at (+ scheduled-at heartbeat-interval-ms)]
    (if (> next-at now)
      next-at
      (recur (+ next-at heartbeat-interval-ms)))))

(defn- regular-due? [state now]
  (<= (:next-regular-at state) now))

(defn- retry-due? [state now]
  (and (:retry-at state) (<= (:retry-at state) now)))

(defn- claim-regular [state now]
  (let [scheduled-at (:next-regular-at state)]
    [(assoc state
       :next-regular-at (next-regular-time scheduled-at now)
       :scheduled-at scheduled-at
       :attempt-number 0
       :retry-at nil
       :in-flight? true)
     {:generation (:generation state)
      :scheduled-at scheduled-at
      :attempt-number 0}]))

(defn- claim-retry [state]
  [(assoc state :retry-at nil :in-flight? true)
   (select-keys state [:generation :scheduled-at :attempt-number])])

(defn- claim-due! [now]
  (locking scheduler-state
    (let [state @scheduler-state]
      (when (and state (not (:in-flight? state)))
        (let [[next-state attempt]
              (cond
                (regular-due? state now) (claim-regular state now)
                (retry-due? state now) (claim-retry state)
                :else [state nil])]
          (when attempt
            (reset! scheduler-state next-state)
            attempt))))))

(defn- same-attempt? [state {:keys [generation scheduled-at attempt-number]}]
  (and (= generation (:generation state))
       (= scheduled-at (:scheduled-at state))
       (= attempt-number (:attempt-number state))))

(defn- complete-attempt! [{:keys [scheduled-at attempt-number] :as attempt} success?]
  (locking scheduler-state
    (when-let [state @scheduler-state]
      (when (same-attempt? state attempt)
        (if success?
          (reset! scheduler-state
                  (assoc state
                    :scheduled-at nil
                    :attempt-number nil
                    :retry-at nil
                    :in-flight? false))
          (if-let [retry-offset (get retry-offsets-ms attempt-number)]
            (reset! scheduler-state
                    (assoc state
                      :attempt-number (inc attempt-number)
                      :retry-at (+ scheduled-at retry-offset)
                      :in-flight? false))
            (reset! scheduler-state
                    (assoc state
                      :scheduled-at nil
                      :attempt-number nil
                      :retry-at nil
                      :in-flight? false))))))))

(defn- execute-attempt! [attempt]
  (let [success? (try
                   (boolean (send-heartbeat!))
                   (catch Exception _
                     false))]
    (when-not success?
      (safe-log :error "Heartbeat reporting failed."))
    (complete-attempt! attempt success?)))

(defn run-due! [now]
  (if-let [attempt (claim-due! now)]
    (do
      (try
        (submit! #(execute-attempt! attempt))
        (catch Exception _
          (safe-log :error "Heartbeat reporting failed.")
          (complete-attempt! attempt false)))
      true)
    false))

(defn start!
  ([] (start! (System/currentTimeMillis)))
  ([now]
   (let [url (configured-url)]
     (cond
       (str/blank? url)
       (do
         (reset! scheduler-state nil)
         (safe-log :status "Remote heartbeat reporting is disabled.")
         false)

       (not (valid-url? url))
       (do
         (reset! scheduler-state nil)
         (safe-log :error "Remote heartbeat reporting requires an HTTPS URL.")
         false)

       :else
       (do
         (reset! scheduler-state
                 {:generation (random-uuid)
                  :next-regular-at now
                  :scheduled-at nil
                  :attempt-number nil
                  :retry-at nil
                  :in-flight? false})
         true)))))

(defn stop! []
  (reset! scheduler-state nil))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-07T10:16:26.839485-05:00", :module-hash "-223124729", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 12, :hash "-1586015403"} {:id "def/heartbeat-interval-ms", :kind "def", :line 14, :end-line 14, :hash "1183444860"} {:id "def/heartbeat-timeout-ms", :kind "def", :line 15, :end-line 15, :hash "-179596928"} {:id "def/retry-offsets-ms", :kind "def", :line 16, :end-line 16, :hash "1162488674"} {:id "def/scheduler-state", :kind "def", :line 18, :end-line 18, :hash "-1737427802"} {:id "defn/local-now", :kind "defn", :line 20, :end-line 21, :hash "1818775650"} {:id "defn-/log-message", :kind "defn-", :line 23, :end-line 24, :hash "1020742345"} {:id "defn-/matching-lines", :kind "defn-", :line 26, :end-line 30, :hash "-1804152265"} {:id "defn/daily-counts", :kind "defn", :line 32, :end-line 42, :hash "-1927004901"} {:id "defn/disk-capacity", :kind "defn", :line 44, :end-line 47, :hash "1298799151"} {:id "defn/disk-summary", :kind "defn", :line 49, :end-line 54, :hash "-1140016522"} {:id "defn/snapshot", :kind "defn", :line 56, :end-line 67, :hash "1995987567"} {:id "defn-/configured-url", :kind "defn-", :line 69, :end-line 70, :hash "-1904467403"} {:id "defn-/valid-url?", :kind "defn-", :line 72, :end-line 74, :hash "757071960"} {:id "defn/send-heartbeat!", :kind "defn", :line 76, :end-line 86, :hash "-1098909284"} {:id "defn/submit!", :kind "defn", :line 88, :end-line 89, :hash "-1600722565"} {:id "defn-/safe-log", :kind "defn-", :line 91, :end-line 94, :hash "-2077579327"} {:id "defn-/next-regular-time", :kind "defn-", :line 96, :end-line 100, :hash "-1183622838"} {:id "defn-/regular-due?", :kind "defn-", :line 102, :end-line 103, :hash "1637673728"} {:id "defn-/retry-due?", :kind "defn-", :line 105, :end-line 106, :hash "453287397"} {:id "defn-/claim-regular", :kind "defn-", :line 108, :end-line 118, :hash "-90245119"} {:id "defn-/claim-retry", :kind "defn-", :line 120, :end-line 122, :hash "-2007369310"} {:id "defn-/claim-due!", :kind "defn-", :line 124, :end-line 135, :hash "1974499644"} {:id "defn-/same-attempt?", :kind "defn-", :line 137, :end-line 140, :hash "-1280084248"} {:id "defn-/complete-attempt!", :kind "defn-", :line 142, :end-line 164, :hash "-627278326"} {:id "defn-/execute-attempt!", :kind "defn-", :line 166, :end-line 173, :hash "-364683936"} {:id "defn/run-due!", :kind "defn", :line 175, :end-line 184, :hash "1412700921"} {:id "defn/start!", :kind "defn", :line 186, :end-line 212, :hash "-472256434"} {:id "defn/stop!", :kind "defn", :line 214, :end-line 215, :hash "-521990757"}]}
;; clj-mutate-manifest-end
