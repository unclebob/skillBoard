(ns skillBoard.foundation.core-utils
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :as st]
            [clojure.string :as str]
            [java-time.api :as time]
            [skillBoard.foundation.atoms :as atoms]
            [skillBoard.foundation.config :as config]
            [skillBoard.foundation.time-util :as time-util]))

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
