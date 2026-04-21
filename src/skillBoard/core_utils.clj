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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T10:36:28.466228-05:00", :module-hash "-1641494220", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-1793980449"} {:id "defn-/log-entry", :kind "defn-", :line 7, :end-line 18, :hash "-1832508901"} {:id "def/log-files", :kind "def", :line 20, :end-line 22, :hash "445016118"} {:id "defn/log", :kind "defn", :line 24, :end-line 30, :hash "1256156038"}]}
;; clj-mutate-manifest-end
