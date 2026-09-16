(ns skillBoard.adapters.heartbeat-spec
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [skillBoard.foundation.atoms :as atoms]
    [skillBoard.foundation.config :as config]
    [skillBoard.foundation.core-utils :as core-utils]
    [skillBoard.adapters.heartbeat :as heartbeat]
    [speclj.core :refer :all])
  (:import
    (java.nio.file Files)
    (java.time LocalDate ZonedDateTime)))

(defn- temp-directory []
  (.toFile (Files/createTempDirectory
             "skillBoard-heartbeat"
             (make-array java.nio.file.attribute.FileAttribute 0))))

(defn- log-line
  ([message]
   (str "2026-09-07T12:34:56.78 " message "\n"))
  ([event message]
   (log-line (str "[event:" (name event) "] " message))))

(describe "heartbeat metrics"
  (it "counts today's aircraft reports, communication issues, and application starts"
    (let [directory (temp-directory)
          date (LocalDate/parse "2026-09-07")]
      (with-redefs [core-utils/log-directory (.getPath directory)]
        (spit (core-utils/log-file-path :status date)
              (str (log-line :application-start "startup wording can change")
                   (log-line :aircraft-report "aircraft wording can change")
                   (log-line :aircraft-report "another aircraft")
                   (log-line :aircraft-report "third aircraft")
                   (log-line "Traffic: unmarked legacy prose")
                   (log-line "Setup...")))
        (spit (core-utils/log-file-path :error date)
              (str (log-line :communication-issue "METAR timed out")
                   (log-line :communication-issue "ADSB connection refused")
                   (log-line "Error fetching unmarked legacy prose")
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
      (reset! atoms/test? false)
      (with-redefs [heartbeat/daily-counts (fn [_]
                                             {:aircraft-reports 17
                                              :communication-issues 3
                                              :application-starts 2})
                    heartbeat/tail-metrics (fn [_]
                                             {:unique-tail-numbers 6
                                              :reported-tails {"N12345" 4}})
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
                          :unique_tail_numbers 6
                          :reported_tails {"N12345" 4}
                          :communication_issues 3
                          :application_starts 2}}
                 (heartbeat/snapshot reported-at)))))

  (it "includes test true when launched with -t"
    (let [reported-at (ZonedDateTime/parse "2026-09-07T14:00:00-05:00")]
      (reset! atoms/test? true)
      (with-redefs [heartbeat/daily-counts (fn [_]
                                             {:aircraft-reports 0
                                              :communication-issues 0
                                              :application-starts 0})
                    heartbeat/tail-metrics (fn [_]
                                             {:unique-tail-numbers 0
                                              :reported-tails {}})
                    heartbeat/disk-capacity (fn [_]
                                              {:usable-bytes 250
                                               :total-bytes 1000})]
        (try
          (let [payload (heartbeat/snapshot reported-at)]
            (should= true (:test payload))
            (should= [:application :version :test :reported_at :disk :today]
                     (keys payload)))
          (finally
            (reset! atoms/test? false)))))))

(describe "heartbeat tail metrics"
  (it "counts unique tails and instances of listed tails from today's aircraft reports"
    (let [directory (temp-directory)
          date (LocalDate/parse "2026-09-07")
          tails-file (io/file directory "reported-tails")]
      (spit tails-file (str "# school aircraft\n"
                            "N12345\n"
                            "\n"
                            "N67890\n"
                            "N12345\n"))
      (with-redefs [core-utils/log-directory (.getPath directory)
                    heartbeat/reported-tails-path (.getPath tails-file)]
        (spit (core-utils/log-file-path :status date)
              (str (log-line :aircraft-report "Traffic: N12345   C000001/GND/001  RAMP    ")
                   (log-line :aircraft-report "Traffic: N12345   C000002/GND/001  TAXI    ")
                   (log-line :aircraft-report "Traffic: N99999   C000003/012/080  NEAR    ")
                   (log-line :aircraft-report "wording can change")
                   (log-line :application-start "startup")))
        (should= {:unique-tail-numbers 2
                  :reported-tails {"N12345" 2}}
                 (heartbeat/tail-metrics date)))))

  (it "returns an empty reported-tails map when the file is missing"
    (let [directory (temp-directory)
          date (LocalDate/parse "2026-09-07")]
      (with-redefs [core-utils/log-directory (.getPath directory)
                    heartbeat/reported-tails-path (.getPath (io/file directory "missing-tails"))]
        (spit (core-utils/log-file-path :status date)
              (log-line :aircraft-report "Traffic: N12345   C000001/GND/001  RAMP    "))
        (should= {:unique-tail-numbers 1
                  :reported-tails {}}
                 (heartbeat/tail-metrics date)))))

  (it "omits listed tails that did not appear today"
    (let [directory (temp-directory)
          date (LocalDate/parse "2026-09-08")
          tails-file (io/file directory "reported-tails")]
      (spit tails-file "N12345\n")
      (with-redefs [core-utils/log-directory (.getPath directory)
                    heartbeat/reported-tails-path (.getPath tails-file)]
        (should= {:unique-tail-numbers 0
                  :reported-tails {}}
                 (heartbeat/tail-metrics date))))))

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

  (it "posts a JSON payload in the documented healthcheck format"
    (let [request (atom nil)
          reported-at (ZonedDateTime/parse "2026-09-07T14:00:00-05:00")]
      (reset! atoms/test? true)
      (with-redefs [config/config (atom {:heartbeat-url "https://hc-ping.com/private-id"})
                    heartbeat/daily-counts (fn [_]
                                             {:aircraft-reports 17
                                              :communication-issues 3
                                              :application-starts 2})
                    heartbeat/tail-metrics (fn [_]
                                             {:unique-tail-numbers 6
                                              :reported-tails {"N12345" 4}})
                    heartbeat/disk-capacity (fn [_]
                                              {:usable-bytes 250
                                               :total-bytes 1000})
                    heartbeat/local-now (fn [] reported-at)
                    http/post (fn [& args]
                                (reset! request args)
                                {:status 200})]
        (try
          (should (heartbeat/send-heartbeat!))
          (let [body (:body (second @request))
                parsed (json/read-str body)]
            (should= {"application" "skillBoard"
                      "version" config/version
                      "test" true
                      "reported_at" "2026-09-07T14:00:00-05:00"
                      "disk" {"usable_bytes" 250
                              "total_bytes" 1000
                              "usable_percent" 25.0}
                      "today" {"aircraft_reports" 17
                               "unique_tail_numbers" 6
                               "reported_tails" {"N12345" 4}
                               "communication_issues" 3
                               "application_starts" 2}}
                     parsed)
            (should (boolean? (get parsed "test"))))
          (finally
            (reset! atoms/test? false))))))

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
