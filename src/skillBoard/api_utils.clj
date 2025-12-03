(ns skillBoard.api-utils
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]))

(defn get-json [url args save-atom com-errors error-name]
  (try
    (let [{:keys [status body]} (http/get url args)]
      (if (= status 200)
        (do
          (reset! save-atom (json/read-str body :key-fn keyword))
          (reset! com-errors 0)
          @save-atom)
        (throw (ex-info (str "Failed to fetch " error-name) {:status status}))))
    (catch Exception e
      (prn (str "Error fetching " error-name ": " (.getMessage e)))
      (swap! com-errors inc)
      @save-atom)))
