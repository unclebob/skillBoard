(ns skillBoard.radar-cape
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.set :as set]
    [clojure.string :as str]
    [java-time.api :as time]
    [skillBoard.sources :as sources]
    ))

(defn get-adsb-raw
  [tail-numbers]
  (try
    (let [tails (map #(str "icao=" %) tail-numbers)
          tails (str/join \& (set tails))
          url (str "http://10.10.30.90/aircraftlist.json?" tails)
          response (http/get url {:accept :text
                                  :with-credentials? false
                                  :socket-timeout 1000
                                  :connection-timeout 1000})]
      (if (= (:status response) 200)
        (json/read-str (:body response) :key-fn keyword)
        (throw (ex-info "Failed to fetch ADSB" {:status (:status response)}))))
    (catch Exception e
      (prn (str "Error fetching ADSB: " (.getMessage e))))))

(defn get-adsb [source tail-numbers]
  (let [raw (sources/get-adsb-raw source tail-numbers)]
    (apply hash-map
           (flatten
             (for [adsb raw]
               [(get adsb :reg)
                adsb]))))
  )

(defn update-with-adsb [reservations adsbs]
  (let [tails (set (keys adsbs))]
    (loop [tails tails
           reservations reservations
           updated-reservations []]
      (if (empty? reservations)
        updated-reservations
        (let [res (first reservations)
              tail-number (:tail-number res)
              adsb (get adsbs tail-number)]
          (if (tails tail-number)
            (recur (disj tails tail-number)
                   (rest reservations)
                   (conj updated-reservations
                         (assoc res :altitude (:alt adsb)
                                    :lat-lon [(:lat adsb) (:lon adsb)]
                                    :track (:trk adsb)
                                    :ground-speed (:spd adsb))))
            (recur tails
                   (rest reservations)
                   (conj updated-reservations res))))))))

(defn get-now [] (time/local-date-time))

(defn include-unreserved-flights [reservations adsbs]
  (let [tails-with-co (set (map :tail-number (filter #(some? (:co %)) reservations)))
        flying-tails (set (map :reg (vals adsbs)))
        rogue-tails (set/difference flying-tails tails-with-co)
        rogue-reservations (for [tail rogue-tails
                                 :let [adsb (get adsbs tail)]
                                 :when (some? adsb)]
                             {:tail-number tail
                              :altitude (:alt adsb)
                              :lat-lon [(:lat adsb) (:lon adsb)]
                              :track (:trk adsb)
                              :ground-speed (:spd adsb)
                              :start-time (get-now)
                              :rogue? true})
        inclusive-reservations (concat reservations rogue-reservations)]
    (sort #(time/before? (:start-time %1) (:start-time %2)) inclusive-reservations)))


(def source {:type :radar-cape})

(defmethod sources/get-adsb-raw :radar-cape [_source tail-numbers]
  (get-adsb-raw tail-numbers))
