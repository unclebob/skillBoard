(require '[clj-http.client :as http]
         '[clojure.data.json :as json]
         '[clojure.string :as str])

(import '(java.net ConnectException InetAddress SocketTimeoutException UnknownHostException)
        '(javax.net.ssl SSLException))

(def open-meteo-url "https://api.open-meteo.com/v1/gfs")
(def default-center [42.4221486 -87.8679161])
(def default-radius-nm 200)
(def default-timeout-ms 10000)

(defn radius-bounds [[lat lon] radius-nm]
  (let [lat-delta (/ radius-nm 60.0)
        lon-delta (/ radius-nm (* 60.0 (Math/cos (Math/toRadians lat))))]
    {:top (+ lat lat-delta)
     :bottom (- lat lat-delta)
     :left (- lon lon-delta)
     :right (+ lon lon-delta)}))

(defn nm-distance [[lat lon] [other-lat other-lon]]
  (let [lat-nm (* 60.0 (- other-lat lat))
        lon-nm (* 60.0 (Math/cos (Math/toRadians lat)) (- other-lon lon))]
    (Math/sqrt (+ (* lat-nm lat-nm) (* lon-nm lon-nm)))))

(defn sample-points
  ([center radius-nm]
   (sample-points center radius-nm 11))
  ([center radius-nm steps]
   (let [{:keys [top bottom left right]} (radius-bounds center radius-nm)
         lat-step (/ (- top bottom) (dec steps))
         lon-step (/ (- right left) (dec steps))]
     (vec
       (for [lat (map #(+ bottom (* % lat-step)) (range steps))
             lon (map #(+ left (* % lon-step)) (range steps))
             :when (<= (nm-distance center [lat lon]) radius-nm)]
         {:lat lat :lon lon})))))

(defn fmt2 [n]
  (format "%.2f" (double n)))

(defn query-params [points]
  {:latitude (str/join "," (map (comp fmt2 :lat) points))
   :longitude (str/join "," (map (comp fmt2 :lon) points))
   :hourly "wind_speed_10m,wind_direction_10m"
   :wind_speed_unit "kn"
   :forecast_hours 1
   :cell_selection "nearest"})

(defn arg-map [args]
  (loop [m {:mode :grid
            :center default-center
            :radius-nm default-radius-nm
            :timeout-ms default-timeout-ms}
         xs args]
    (case (first xs)
      nil m
      "--single" (recur (assoc m :mode :single) (rest xs))
      "--grid" (recur (assoc m :mode :grid) (rest xs))
      "--lat" (recur (assoc-in m [:center 0] (Double/parseDouble (second xs))) (nnext xs))
      "--lon" (recur (assoc-in m [:center 1] (Double/parseDouble (second xs))) (nnext xs))
      "--radius-nm" (recur (assoc m :radius-nm (Double/parseDouble (second xs))) (nnext xs))
      "--timeout-ms" (recur (assoc m :timeout-ms (Integer/parseInt (second xs))) (nnext xs))
      "--help" (assoc m :help? true)
      (throw (ex-info (str "Unknown argument: " (first xs)) {:arg (first xs)})))))

(defn usage []
  (println "Usage: clojure -M scripts/open_meteo_diagnostic.clj [options]")
  (println)
  (println "Options:")
  (println "  --grid              Poll the same KUGN-area grid shape used by the app. Default.")
  (println "  --single            Poll only the center point.")
  (println "  --lat N             Center latitude. Default: 42.4221486")
  (println "  --lon N             Center longitude. Default: -87.8679161")
  (println "  --radius-nm N       Grid radius in nautical miles. Default: 200")
  (println "  --timeout-ms N      Socket and connection timeout. Default: 10000")
  (println "  --help              Show this help."))

(defn print-section [title]
  (println)
  (println (str "== " title " ==")))

(defn selected-headers [headers]
  (let [interesting #{"retry-after"
                      "cf-ray"
                      "x-ratelimit-limit"
                      "x-ratelimit-remaining"
                      "x-ratelimit-reset"
                      "content-type"
                      "date"
                      "server"}]
    (into (sorted-map)
          (filter (fn [[k _]]
                    (contains? interesting (str/lower-case (name k)))))
          headers)))

(defn parse-json-body [body]
  (try
    (json/read-str body :key-fn keyword)
    (catch Exception _ nil)))

(defn summarize-body [body]
  (if (str/blank? body)
    "(empty body)"
    (let [parsed (parse-json-body body)
          text (if parsed (pr-str parsed) body)]
      (subs text 0 (min 1200 (count text))))))

(defn diagnosis [status body headers elapsed-ms]
  (let [parsed (parse-json-body body)
        reason (or (:reason parsed) (:error parsed) (:message parsed))
        retry-after (or (get headers "Retry-After") (get headers :retry-after))]
    (cond
      (= status 200)
      (str "Open-Meteo returned HTTP 200 in " elapsed-ms " ms. The connection and request are OK.")

      (= status 400)
      (str "Open-Meteo returned HTTP 400. The request parameters are probably invalid."
           (when reason (str " Reason: " reason)))

      (= status 429)
      (str "Open-Meteo returned HTTP 429. This is rate limiting or quota exhaustion."
           (when retry-after (str " Retry-After: " retry-after "."))
           (when reason (str " Reason: " reason)))

      (<= 500 status)
      (str "Open-Meteo returned HTTP " status ". This is probably a provider-side or edge-server problem."
           (when reason (str " Reason: " reason)))

      :else
      (str "Open-Meteo returned HTTP " status ". Inspect the body and headers below."
           (when reason (str " Reason: " reason))))))

(defn exception-diagnosis [e]
  (cond
    (instance? UnknownHostException e)
    "DNS failed. The Pi cannot resolve api.open-meteo.com."

    (instance? ConnectException e)
    "TCP connection failed. Check internet routing, firewall, DNS result, or captive network."

    (instance? SocketTimeoutException e)
    "The request timed out. The Pi can start the request but the network or server is too slow."

    (instance? SSLException e)
    "TLS/SSL failed. Check the Pi clock, CA certificates, and HTTPS interception."

    :else
    "The request failed before an HTTP response was received. Inspect the exception below."))

(defn print-dns []
  (print-section "DNS")
  (try
    (let [addresses (InetAddress/getAllByName "api.open-meteo.com")]
      (doseq [address addresses]
        (println (.getHostAddress address))))
    (catch Exception e
      (println (exception-diagnosis e))
      (println (.getClass e))
      (println (.getMessage e)))))

(defn wind-summary [body]
  (let [parsed (parse-json-body body)
        responses (if (sequential? parsed) parsed [parsed])
        first-response (first responses)
        hourly (:hourly first-response)
        speed (first (:wind_speed_10m hourly))
        direction (first (:wind_direction_10m hourly))]
    (when (and speed direction)
      (println (str "First wind point: " speed " kt from " direction " degrees")))))

(defn poll-open-meteo [{:keys [mode center radius-nm timeout-ms]}]
  (let [points (if (= :single mode)
                 [{:lat (first center) :lon (second center)}]
                 (sample-points center radius-nm))
        params (query-params points)
        started (System/nanoTime)]
    (print-section "Request")
    (println "URL:" open-meteo-url)
    (println "Mode:" (name mode))
    (println "Point count:" (count points))
    (println "Timeout ms:" timeout-ms)
    (println "Latitude parameter length:" (count (:latitude params)))
    (println "Longitude parameter length:" (count (:longitude params)))
    (try
      (let [response (http/get open-meteo-url
                               {:accept :json
                                :as :text
                                :throw-exceptions false
                                :query-params params
                                :socket-timeout timeout-ms
                                :connection-timeout timeout-ms})
            elapsed-ms (long (/ (- (System/nanoTime) started) 1000000))
            status (:status response)
            body (:body response)
            headers (:headers response)]
        (print-section "Result")
        (println "HTTP status:" status)
        (println "Elapsed ms:" elapsed-ms)
        (println "Diagnosis:" (diagnosis status body headers elapsed-ms))
        (print-section "Selected Headers")
        (doseq [[k v] (selected-headers headers)]
          (println (str k ": " v)))
        (print-section "Body")
        (println (summarize-body body))
        (when (= status 200)
          (print-section "Wind")
          (wind-summary body))
        (System/exit (if (= status 200) 0 2)))
      (catch Exception e
        (print-section "Result")
        (println "Diagnosis:" (exception-diagnosis e))
        (println "Exception:" (.getName (class e)))
        (println "Message:" (.getMessage e))
        (System/exit 1)))))

(let [options (arg-map *command-line-args*)]
  (if (:help? options)
    (usage)
    (do
      (println "Open-Meteo diagnostic")
      (println "Run time:" (str (java.time.OffsetDateTime/now)))
      (print-dns)
      (poll-open-meteo options))))
