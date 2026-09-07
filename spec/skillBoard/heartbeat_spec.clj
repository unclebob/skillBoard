(ns skillBoard.heartbeat-spec
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [skillBoard.config :as config]
    [skillBoard.core-utils :as core-utils]
    [skillBoard.heartbeat :as heartbeat]
    [speclj.core :refer :all])
  (:import
    (java.nio.file Files)
    (java.time LocalDate ZonedDateTime)))

(defn- temp-directory []
  (.toFile (Files/createTempDirectory
             "skillBoard-heartbeat"
             (make-array java.nio.file.attribute.FileAttribute 0))))

(defn- log-line [message]
  (str "2026-09-07T12:34:56.78 " message "\n"))

(describe "heartbeat metrics"
  (it "counts today's aircraft reports, communication issues, and application starts"
    (let [directory (temp-directory)
          date (LocalDate/parse "2026-09-07")]
      (with-redefs [core-utils/log-directory (.getPath directory)]
        (spit (core-utils/log-file-path :status date)
              (str (log-line "skillBoard v20260509 has begun.")
                   (log-line "Traffic: N12345 UGN090010/025/100 NEAR")
                   (log-line "Traffic: N12345 UGN091009/025/101 NEAR")
                   (log-line "Traffic: N54321 UGN180005/020/090 NEAR")
                   (log-line "Setup...")))
        (spit (core-utils/log-file-path :error date)
              (str (log-line "Error fetching METAR: timed out")
                   (log-line "Error fetching nearby ADSB: connection refused")
                   (log-line "Heartbeat reporting failed.")
                   (log-line "Error drawing wind source label")))
        (should= {:aircraft-reports 3
                  :communication-issues 2
                  :application-starts 1}
                 (heartbeat/daily-counts date)))))

  (it "reports zero counts when today's logs do not exist"
    (let [directory (temp-directory)]
      (with-redefs [core-utils/log-directory (.getPath directory)]
        (should= {:aircraft-reports 0
                  :communication-issues 0
                  :application-starts 0}
                 (heartbeat/daily-counts (LocalDate/parse "2026-09-08"))))))

  (it "calculates usable disk percentage to one decimal place"
    (should= {:usable_bytes 251
              :total_bytes 1000
              :usable_percent 25.1}
             (heartbeat/disk-summary {:usable-bytes 251 :total-bytes 1000})))

  (it "uses null percentage when total disk size is zero"
    (should= {:usable_bytes 0
              :total_bytes 0
              :usable_percent nil}
             (heartbeat/disk-summary {:usable-bytes 0 :total-bytes 0})))

  (it "builds the public status payload"
    (let [reported-at (ZonedDateTime/parse "2026-09-07T14:00:00-05:00")]
      (with-redefs [heartbeat/daily-counts (fn [_]
                                             {:aircraft-reports 17
                                              :communication-issues 3
                                              :application-starts 2})
                    heartbeat/disk-capacity (fn [_]
                                              {:usable-bytes 250
                                               :total-bytes 1000})]
        (should= {:application "skillBoard"
                  :version config/version
                  :reported_at "2026-09-07T14:00:00-05:00"
                  :disk {:usable_bytes 250
                         :total_bytes 1000
                         :usable_percent 25.0}
                  :today {:aircraft_reports 17
                          :communication_issues 3
                          :application_starts 2}}
                 (heartbeat/snapshot reported-at))))))

(describe "heartbeat transport"
  (it "posts the snapshot as JSON with bounded timeouts"
    (let [request (atom nil)
          url "https://hc-ping.com/private-id"]
      (with-redefs [config/config (atom {:heartbeat-url url})
                    heartbeat/snapshot (fn [] {:application "skillBoard"})
                    http/post (fn [& args]
                                (reset! request args)
                                {:status 200})]
        (should (heartbeat/send-heartbeat!))
        (let [[actual-url options] @request]
          (should= url actual-url)
          (should= {:application "skillBoard"}
                   (json/read-str (:body options) :key-fn keyword))
          (should= :json (:content-type options))
          (should= 10000 (:connection-timeout options))
          (should= 10000 (:socket-timeout options))
          (should= false (:throw-exceptions options))))))

  (it "rejects a non-success response"
    (with-redefs [config/config (atom {:heartbeat-url "https://hc-ping.com/private-id"})
                  heartbeat/snapshot (fn [] {})
                  http/post (fn [& _] {:status 503})]
      (should-not (heartbeat/send-heartbeat!)))))

(describe "heartbeat scheduling"
  (it "posts immediately and once per anchored hour"
    (let [posts (atom 0)]
      (with-redefs [config/config (atom {:heartbeat-url "https://hc-ping.com/private-id"})
                    heartbeat/scheduler-state (atom nil)
                    heartbeat/submit! (fn [work] (work))
                    heartbeat/send-heartbeat! (fn [] (swap! posts inc) true)]
        (should (heartbeat/start! 1000))
        (should (heartbeat/run-due! 1000))
        (should= 1 @posts)
        (should-not (heartbeat/run-due! 1000))
        (should-not (heartbeat/run-due! 3600999))
        (should (heartbeat/run-due! 3601000))
        (should= 2 @posts))))

  (it "does not overlap heartbeat attempts"
    (let [submitted (atom [])]
      (with-redefs [config/config (atom {:heartbeat-url "https://hc-ping.com/private-id"})
                    heartbeat/scheduler-state (atom nil)
                    heartbeat/submit! (fn [work] (swap! submitted conj work))
                    heartbeat/send-heartbeat! (constantly true)]
        (heartbeat/start! 0)
        (should (heartbeat/run-due! 0))
        (should-not (heartbeat/run-due! 3600000))
        (should= 1 (count @submitted))
        ((first @submitted))
        (should (heartbeat/run-due! 3600000)))))

  (it "retries at one and five minutes without shifting the next regular post"
    (let [results (atom [false false true true])
          post-times (atom [])
          now (atom 0)]
      (with-redefs [config/config (atom {:heartbeat-url "https://hc-ping.com/private-id"})
                    heartbeat/scheduler-state (atom nil)
                    heartbeat/submit! (fn [work] (work))
                    heartbeat/send-heartbeat! (fn []
                                                (swap! post-times conj @now)
                                                (let [result (first @results)]
                                                  (swap! results rest)
                                                  result))
                    core-utils/log (fn [& _])]
        (heartbeat/start! 0)
        (heartbeat/run-due! @now)
        (reset! now 59999)
        (should-not (heartbeat/run-due! @now))
        (reset! now 60000)
        (should (heartbeat/run-due! @now))
        (reset! now 299999)
        (should-not (heartbeat/run-due! @now))
        (reset! now 300000)
        (should (heartbeat/run-due! @now))
        (reset! now 3600000)
        (should (heartbeat/run-due! @now))
        (should= [0 60000 300000 3600000] @post-times))))

  (it "disables reporting when the URL is absent"
    (let [messages (atom [])]
      (with-redefs [config/config (atom {})
                    heartbeat/scheduler-state (atom :old-state)
                    core-utils/log (fn [level message]
                                     (swap! messages conj [level message]))]
        (should-not (heartbeat/start! 0))
        (should-be-nil @heartbeat/scheduler-state)
        (should= [[:status "Remote heartbeat reporting is disabled."]]
                 @messages))))

  (it "stops future heartbeat scheduling"
    (with-redefs [heartbeat/scheduler-state (atom {:next-regular-at 0})]
      (heartbeat/stop!)
      (should-be-nil @heartbeat/scheduler-state)
      (should-not (heartbeat/run-due! 0))))

  (it "requires HTTPS without logging the configured URL"
    (let [url "http://secret.example/private-id"
          messages (atom [])]
      (with-redefs [config/config (atom {:heartbeat-url url})
                    heartbeat/scheduler-state (atom nil)
                    core-utils/log (fn [_ message] (swap! messages conj message))]
        (should-not (heartbeat/start! 0))
        (should-not (str/includes? (str/join " " @messages) url)))))

  (it "does not disclose the URL when posting throws"
    (let [url "https://hc-ping.com/private-id"
          messages (atom [])]
      (with-redefs [config/config (atom {:heartbeat-url url})
                    heartbeat/scheduler-state (atom nil)
                    heartbeat/submit! (fn [work] (work))
                    heartbeat/send-heartbeat! (fn [] (throw (ex-info url {})))
                    core-utils/log (fn [_ message] (swap! messages conj message))]
        (heartbeat/start! 0)
        (should (heartbeat/run-due! 0))
        (should-not (str/includes? (str/join " " @messages) url))))))
