(ns skillBoard.domain.time-spec
  (:require [java-time.api :as time]
            [skillBoard.domain.time :as domain-time]
            [speclj.core :refer :all]))

(declare now)

(describe "domain time"
  (with now (time/local-date-time "yyyy-MM-dd'T'HH:mm:ss.SS" "2025-06-16T09:55:24.00"))

  (it "converts time strings to times"
    (should= @now (domain-time/parse-time "2025-06-16T09:55:24.00"))
    (should= @now (domain-time/parse-time "2025-06-16T09:55:24"))
    (should= nil (domain-time/parse-time nil))
    (should= nil (domain-time/parse-time ""))
    (should= nil (domain-time/parse-time domain-time/epoch-str))
    (should= nil (domain-time/parse-time (str domain-time/epoch-str ".000")))))
