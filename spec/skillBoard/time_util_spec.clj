(ns skillBoard.time-util-spec
  (:require [java-time.api :as time]
            [skillBoard.config :as config]
            [skillBoard.time-util :as time-util]
            [speclj.core :refer :all]
            ))

(declare now)

(describe "utilities"
  (with now (time/local-date-time "yyyy-MM-dd'T'HH:mm:ss.SS" "2025-06-16T09:55:24.00"))

  (it "gets HH:mm from time"
    (should= "09:55"
             (time-util/get-HHmm @now)))

  (it "converts time strings to times"
    (should= @now (time-util/parse-time "2025-06-16T09:55:24.00"))
    (should= @now (time-util/parse-time "2025-06-16T09:55:24"))
    (should= nil (time-util/parse-time nil))
    (should= nil (time-util/parse-time ""))
    (should= nil (time-util/parse-time time-util/epoch-str))
    (should= nil (time-util/parse-time (str time-util/epoch-str ".000"))))

  (it "converts local time to UTC"
    (reset! config/config {:time-zone "America/Chicago"})
    (should= "14:55" (time-util/get-HHmm (time-util/local-to-utc @now)))
    )
  )