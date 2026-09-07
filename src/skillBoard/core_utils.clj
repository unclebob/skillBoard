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

(defn log
  ([arg] (log :status arg))
  ([level arg]
   (let [entry (log-entry arg)
         file (log-file-path level)]
     (ensure-log-directory!)
     (prune-old-logs!)
     (spit file entry :append true)
     (when @atoms/log-stdout? (print entry)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-07T10:16:42.299696-05:00", :module-hash "1606500877", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "-1885602896"} {:id "defn-/timestamped-line", :kind "defn-", :line 10, :end-line 11, :hash "1827877983"} {:id "defn-/exception-header", :kind "defn-", :line 13, :end-line 14, :hash "-453797604"} {:id "defn-/exception-entry", :kind "defn-", :line 16, :end-line 18, :hash "-1086302256"} {:id "defn-/log-entry", :kind "defn-", :line 20, :end-line 30, :hash "1982159340"} {:id "def/log-directory", :kind "def", :line 32, :end-line 32, :hash "-138915610"} {:id "def/log-file-suffixes", :kind "def", :line 34, :end-line 36, :hash "452430116"} {:id "def/log-file-pattern", :kind "def", :line 38, :end-line 38, :hash "-1206732266"} {:id "def/log-date-formatter", :kind "def", :line 40, :end-line 41, :hash "615142313"} {:id "defn/log-file-path", :kind "defn", :line 43, :end-line 48, :hash "1494849457"} {:id "defn-/ensure-log-directory!", :kind "defn-", :line 50, :end-line 51, :hash "-991959002"} {:id "defn-/log-file-date", :kind "defn-", :line 53, :end-line 55, :hash "1939016530"} {:id "defn-/old-log-file?", :kind "defn-", :line 57, :end-line 59, :hash "1369111099"} {:id "defn/prune-old-logs!", :kind "defn", :line 61, :end-line 66, :hash "636322715"} {:id "defn/log", :kind "defn", :line 68, :end-line 76, :hash "-1712663649"}]}
;; clj-mutate-manifest-end
