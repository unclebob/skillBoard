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

(defn- log-date []
  (.format (java.time.LocalDate/now)
           (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd")))

(defn- log-file [level]
  (str log-directory "/" (log-date) (get log-file-suffixes level "status.log")))

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
         file (log-file level)]
     (ensure-log-directory!)
     (prune-old-logs!)
     (spit file entry :append true)
     (when @atoms/log-stdout? (print entry)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T10:36:28.466228-05:00", :module-hash "-1641494220", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-1793980449"} {:id "defn-/log-entry", :kind "defn-", :line 7, :end-line 18, :hash "-1832508901"} {:id "def/log-files", :kind "def", :line 20, :end-line 22, :hash "445016118"} {:id "defn/log", :kind "defn", :line 24, :end-line 30, :hash "1256156038"}]}
;; clj-mutate-manifest-end
