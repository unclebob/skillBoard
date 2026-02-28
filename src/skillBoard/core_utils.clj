(ns skillBoard.core-utils
  (:require [clojure.stacktrace :as st]
            [java-time.api :as time]
            [skillBoard.atoms :as atoms]
            [skillBoard.time-util :as time-util]))

(defn- log-entry [arg]
  (let [timestamp (time-util/format-time (time/local-date-time))]
    (cond
      (string? arg)
      (str timestamp " " arg "\n")

      (instance? Exception arg)
      (str timestamp " " (.getMessage arg) "\n"
           (with-out-str (st/print-stack-trace arg)))

      :else
      (str timestamp " " (str arg) "\n"))))

(def ^:private log-files
  {:status "status.log"
   :error "error.log"})

(defn log
  ([arg] (log :status arg))
  ([level arg]
   (let [entry (log-entry arg)
         file (get log-files level "status.log")]
     (spit file entry :append true)
     (when @atoms/log-stdout? (print entry)))))