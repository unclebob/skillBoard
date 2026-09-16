(ns skillBoard.adapters.heartbeat
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [skillBoard.foundation.atoms :as atoms]
    [skillBoard.foundation.config :as config]
    [skillBoard.foundation.core-utils :as core-utils])
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

(def reported-tails-path "private/reported-tails")

(defn daily-counts [date]
  {:aircraft-reports
   (core-utils/count-log-events :status date :aircraft-report)

   :communication-issues
   (core-utils/count-log-events :error date :communication-issue)

   :application-starts
   (core-utils/count-log-events :status date :application-start)})

(defn- traffic-tail [message]
  (when message
    (second (re-find #"^Traffic:\s+(\S+)" message))))

(defn- parse-reported-tail-line [line]
  (let [trimmed (str/trim line)]
    (when (and (not (str/blank? trimmed))
               (not (str/starts-with? trimmed "#")))
      trimmed)))

(defn load-reported-tails
  ([] (load-reported-tails reported-tails-path))
  ([path]
   (let [file (io/file path)]
     (if-not (.exists file)
       []
       (with-open [reader (io/reader file)]
         (->> (line-seq reader)
              (keep parse-reported-tail-line)
              distinct
              vec))))))

(defn tail-metrics [date]
  (let [frequencies (->> (core-utils/log-event-messages :status date :aircraft-report)
                         (keep traffic-tail)
                         frequencies)
        listed (load-reported-tails)]
    {:unique-tail-numbers (count frequencies)
     :reported-tails (into {}
                           (keep (fn [tail]
                                   (let [n (get frequencies tail 0)]
                                     (when (pos? n)
                                       [tail n])))
                                 listed))}))

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
   (let [date (.toLocalDate reported-at)
         {:keys [aircraft-reports communication-issues application-starts]}
         (daily-counts date)
         {:keys [unique-tail-numbers reported-tails]}
         (tail-metrics date)]
     (apply array-map
            (concat
              [:application "skillBoard"
               :version config/version]
              (when @atoms/test? [:test true])
              [:reported_at (.format reported-at DateTimeFormatter/ISO_OFFSET_DATE_TIME)
               :disk (disk-summary (disk-capacity core-utils/log-directory))
               :today {:aircraft_reports aircraft-reports
                       :unique_tail_numbers unique-tail-numbers
                       :reported_tails reported-tails
                       :communication_issues communication-issues
                       :application_starts application-starts}])))))

(defn json-body [payload]
  (with-out-str (json/pprint payload)))

(defn- configured-url []
  (:heartbeat-url @config/config))

(defn- valid-url? [url]
  (and (not (str/blank? url))
       (str/starts-with? url "https://")))

(defn send-heartbeat! []
  (let [url (configured-url)
        response (http/post url
                            {:body (json-body (snapshot))
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
