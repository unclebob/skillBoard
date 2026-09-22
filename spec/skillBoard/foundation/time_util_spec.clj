(ns skillBoard.foundation.time-util-spec
  (:require [java-time.api :as time]
            [skillBoard.foundation.time-util :as time-util]
            [speclj.core :refer :all]))

(declare now)

(describe "utilities"
  (with now (time/local-date-time "yyyy-MM-dd'T'HH:mm:ss.SS" "2025-06-16T09:55:24.00"))

  (it "gets HH:mm from time"
    (should= "09:55"
             (time-util/get-HHmm @now)))

  (it "formats a timestamp with fractional seconds"
    (should= "2025-06-16T09:55:24.00"
             (time-util/format-time @now)))

  (it "converts a summer local time to UTC"
    (should= "14:55" (time-util/get-HHmm (time-util/local-to-utc @now))))

  (it "converts a winter local time to UTC"
    (let [winter (time/local-date-time "yyyy-MM-dd'T'HH:mm:ss" "2025-01-15T09:55:24")]
      (should= "15:55" (time-util/get-HHmm (time-util/local-to-utc winter)))))
  )