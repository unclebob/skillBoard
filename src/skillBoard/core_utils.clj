(ns skillBoard.core-utils
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :as st]
            [clojure.string :as str]
            [java-time.api :as time]
            [skillBoard.atoms :as atoms]
            [skillBoard.config :as config]
            [skillBoard.time-util :as time-util]))

(defn- timestamped-line [timestamp line]
  (str timestamp " " line "\n"))

(defn- exception-header [timestamp exception]
  (str timestamp " " (.getMessage exception) " -----\n"))

(defn- exception-entry [timestamp exception]
  (str (exception-header timestamp exception)
       (with-out-str (st/print-stack-trace exception))))

(defn- log-entry [arg]
  (let [timestamp (time-util/format-time (time/local-date-time))]
    (cond
      (string? arg)
      (timestamped-line timestamp arg)

      (instance? Exception arg)
      (exception-entry timestamp arg)

      :else
      (timestamped-line timestamp (str arg)))))

(defn- event-marker [event]
  (str "[event:" (name event) "]"))

(def log-directory "logs")

(def ^:private log-file-suffixes
  {:status "status.log"
   :error "error.log"})

(def ^:private log-file-pattern #"^(\d{8})(status|error)\.log$")

(def ^:private log-date-formatter
  (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd"))

(defn log-file-path
  ([level]
   (log-file-path level (java.time.LocalDate/now)))
  ([level date]
   (str log-directory "/" (.format date log-date-formatter)
        (get log-file-suffixes level "status.log"))))

(defn- ensure-log-directory! []
  (.mkdirs (io/file log-directory)))

(defn- log-file-date [file]
  (when-let [[_ date] (re-matches log-file-pattern (.getName file))]
    (java.time.LocalDate/parse date (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd"))))

(defn- old-log-file? [cutoff file]
  (when-let [file-date (log-file-date file)]
    (.isBefore file-date cutoff)))

(defn prune-old-logs! []
  (let [directory (io/file log-directory)
        cutoff (.minusDays (java.time.LocalDate/now) config/log-retention-days)]
    (doseq [file (file-seq directory)
            :when (and (.isFile file) (old-log-file? cutoff file))]
      (io/delete-file file true))))

(defn- write-log! [level arg]
  (let [entry (log-entry arg)
        file (log-file-path level)]
    (ensure-log-directory!)
    (prune-old-logs!)
    (spit file entry :append true)
    (when @atoms/log-stdout? (print entry))))

(defn log
  ([arg] (log :status arg))
  ([level arg]
   (write-log! level arg)))

(defn log-event [level event message]
  (log level (str (event-marker event) " " message)))

(defn- event-message [event line]
  (let [[_ message] (str/split line #" " 2)
        prefix (str (event-marker event) " ")]
    (when (str/starts-with? (or message "") prefix)
      (subs message (count prefix)))))

(defn log-event-messages [level date event]
  (let [file (io/file (log-file-path level date))]
    (if-not (.exists file)
      []
      (with-open [reader (io/reader file)]
        (into [] (keep #(event-message event %) (line-seq reader)))))))

(defn count-log-events [level date event]
  (count (log-event-messages level date event)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-07T11:29:34.092-05:00", :module-hash "-714646885", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "-1885602896"} {:id "defn-/timestamped-line", :kind "defn-", :line 10, :end-line 11, :hash "1827877983"} {:id "defn-/exception-header", :kind "defn-", :line 13, :end-line 14, :hash "-453797604"} {:id "defn-/exception-entry", :kind "defn-", :line 16, :end-line 18, :hash "-1086302256"} {:id "defn-/log-entry", :kind "defn-", :line 20, :end-line 30, :hash "1982159340"} {:id "defn-/event-marker", :kind "defn-", :line 32, :end-line 33, :hash "-51329600"} {:id "def/log-directory", :kind "def", :line 35, :end-line 35, :hash "-138915610"} {:id "def/log-file-suffixes", :kind "def", :line 37, :end-line 39, :hash "452430116"} {:id "def/log-file-pattern", :kind "def", :line 41, :end-line 41, :hash "-1206732266"} {:id "def/log-date-formatter", :kind "def", :line 43, :end-line 44, :hash "615142313"} {:id "defn/log-file-path", :kind "defn", :line 46, :end-line 51, :hash "1494849457"} {:id "defn-/ensure-log-directory!", :kind "defn-", :line 53, :end-line 54, :hash "-991959002"} {:id "defn-/log-file-date", :kind "defn-", :line 56, :end-line 58, :hash "1939016530"} {:id "defn-/old-log-file?", :kind "defn-", :line 60, :end-line 62, :hash "1369111099"} {:id "defn/prune-old-logs!", :kind "defn", :line 64, :end-line 69, :hash "636322715"} {:id "defn-/write-log!", :kind "defn-", :line 71, :end-line 77, :hash "-591893839"} {:id "defn/log", :kind "defn", :line 79, :end-line 82, :hash "-1664452768"} {:id "defn/log-event", :kind "defn", :line 84, :end-line 85, :hash "765916041"} {:id "defn-/event-line?", :kind "defn-", :line 87, :end-line 89, :hash "425469665"} {:id "defn/count-log-events", :kind "defn", :line 91, :end-line 96, :hash "219457727"}]}
;; clj-mutate-manifest-end
