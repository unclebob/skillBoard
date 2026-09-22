(ns skillBoard.gateways.comm-utils
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [skillBoard.foundation.core-utils :as core-utils])
  (:import
    (java.io ByteArrayInputStream)
    (java.util.zip GZIPInputStream ZipInputStream)))

(defn get-json [url args save-atom com-errors error-name]
  (try
    (let [{:keys [status body]} (http/get url args)
          body (if (nil? body)
                 (throw (ex-info (str "Nil body fetching " error-name) {}))
                 (json/read-str body :key-fn keyword))]
      (if (= status 200)
        (do
          (reset! save-atom body)
          (reset! com-errors 0)
          @save-atom)
        (throw (ex-info (str "Failed to fetch " error-name) {:status status}))))
    (catch Exception e
      (core-utils/log-event :error :communication-issue
                            (str "Error fetching " error-name ": " (.getMessage e)))
      (swap! com-errors inc)
      @save-atom)))

(defn csv-fields [line]
  (loop [chars (seq line)
         field []
         fields []
         quoted? false]
    (if-let [ch (first chars)]
      (cond
        (and (= \" ch) quoted? (= \" (second chars)))
        (recur (nnext chars) (conj field ch) fields quoted?)

        (= \" ch)
        (recur (next chars) field fields (not quoted?))

        (and (= \, ch) (not quoted?))
        (recur (next chars) [] (conj fields (apply str field)) quoted?)

        :else
        (recur (next chars) (conj field ch) fields quoted?))
      (conj fields (apply str field)))))

(defn- header-key [header occurrence]
  (keyword (if (= 1 occurrence)
             header
             (str header "_" occurrence))))

(defn unique-csv-headers [headers]
  (:headers
    (reduce (fn [{:keys [counts] :as state} header]
              (let [occurrence (inc (get counts header 0))]
                (-> state
                    (update :counts assoc header occurrence)
                    (update :headers conj (header-key header occurrence)))))
            {:counts {} :headers []}
            headers)))

(defn csv->maps [csv-text]
  (let [lines (remove str/blank? (str/split-lines csv-text))
        headers (unique-csv-headers (csv-fields (first lines)))]
    (map #(zipmap headers (csv-fields %)) (rest lines))))

(defn gzip-bytes->string [bytes]
  (with-open [stream (GZIPInputStream. (ByteArrayInputStream. bytes))]
    (slurp stream)))

(defn zip-entry->string [bytes entry-name]
  (with-open [zip (ZipInputStream. (ByteArrayInputStream. bytes))]
    (loop [entry (.getNextEntry zip)]
      (cond
        (nil? entry) nil
        (= entry-name (.getName entry)) (slurp zip)
        :else (recur (.getNextEntry zip))))))
