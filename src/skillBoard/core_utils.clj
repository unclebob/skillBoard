(ns skillBoard.core-utils
  (:require [clojure.stacktrace :as st]
            [java-time.api :as time]
            [skillBoard.time-util :as time-util]))

(defn log [arg]
  (let [timestamp (time-util/format-time (time/local-date-time))
        log-entry (cond
                    (string? arg)
                    (str timestamp " " arg "\n")

                    (instance? Exception arg)
                    (str timestamp " " (.getMessage arg) "\n"
                         (with-out-str (st/print-stack-trace arg)))

                    :else
                    (str timestamp " " (str arg) "\n"))]
    (spit "log.txt" log-entry :append true)
    (print log-entry)))