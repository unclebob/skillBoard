(ns skillBoard.foundation.time-util-spec
  (:require [java-time.api :as time]
            [skillBoard.foundation.config :as config]
            [skillBoard.foundation.time-util :as time-util]
            [speclj.core :refer :all]
            ))

(declare now)

(describe "utilities"
  (with now (time/local-date-time "yyyy-MM-dd'T'HH:mm:ss.SS" "2025-06-16T09:55:24.00"))

  (it "gets HH:mm from time"
    (should= "09:55"
             (time-util/get-HHmm @now)))

  (it "converts local time to UTC"
    (reset! config/config {:time-zone "America/Chicago"})
    (should= "14:55" (time-util/get-HHmm (time-util/local-to-utc @now)))
    )
  )