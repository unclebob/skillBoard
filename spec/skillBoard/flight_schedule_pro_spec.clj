(ns skillBoard.flight-schedule-pro-spec
  (:require
    [java-time.api :as time]
    [skillBoard.flight-schedule-pro :as fsp]
    [speclj.core :refer :all]))
;reservationStatus {:id 1, :name "Checked Out"} {:id 2, :name "Completed"} {:id 0, :name "Reserved"}

(declare now now-str)

(describe "Flight Schedule Pro"
  (with now (time/local-date-time "yyyy-MM-dd'T'HH:mm:ss.SS" "2025-06-16T09:55:24.49"))
  (with now-str (time/format "yyyy-MM-dd'T'HH:mm:ss.SS" @now))

  (context "Reservations"
    (it "unpacks degenerate packets"
      (should= [] (fsp/unpack-reservations nil))
      (should= [] (fsp/unpack-reservations {}))
      (should= [] (fsp/unpack-reservations {:items nil}))
      (should= [] (fsp/unpack-reservations {:items []})))

    (it "unpacks a single reservation"
      (should= [
                {:reservation-id "id"
                 :tail-number "tail-number"
                 :activity-type "Flight type"
                 :start-time @now
                 :pilot-name ["pilot first name" "pilot last name"]
                 :instructor-name ["instructor first name" "instructor last name"]
                 :reservation-status "reservation status"
                 :checked-in-on @now
                 :checked-out-on @now
                 }
                ]

               (fsp/unpack-reservations
                 {:items [{:reservationId "id"
                           :aircraft {:tailNumber "tail-number"}
                           :activityType {:name "Flight type"}
                           :startTime @now-str
                           :pilots [{:firstName "pilot first name" :lastName "pilot last name"}]
                           :instructor {:firstName "instructor first name" :lastName "instructor last name"}
                           :reservationStatus {:name "reservation status"}
                           :checkedInOn @now-str
                           :checkedOutOn @now-str
                           }]})
               )
      )

    (it "unpacks a single reservation with missing names and times"
      (should= [{:reservation-id "id"
                 :tail-number "tail-number"
                 :activity-type "Flight type"
                 :start-time @now
                 :pilot-name nil
                 :instructor-name nil
                 :reservation-status "reservation status"
                 :checked-in-on nil
                 :checked-out-on nil
                 }]

               (fsp/unpack-reservations
                 {:items [{:reservationId "id"
                           :aircraft {:tailNumber "tail-number"}
                           :activityType {:name "Flight type"}
                           :startTime @now-str
                           :reservationStatus {:name "reservation status"}
                           }]})
               )
      )
    )

  (context "flights"
    (it "unpacks degenerate packets"
      (should= [] (fsp/unpack-flights nil))
      (should= [] (fsp/unpack-flights {}))
      (should= [] (fsp/unpack-flights {:items nil}))
      (should= [] (fsp/unpack-flights {:items []})))

    (it "unpacks a single flight"
      (should=
        {"reservation-id"
         {
          :reservation-id "reservation-id"
          :checked-out-on @now
          :checked-in-on @now
          }}

        (fsp/unpack-flights
          {:items [
                   {:reservationId "reservation-id"
                    :checkedOutOn @now-str
                    :checkedInOn @now-str
                    }
                   ]}))
      )

    (it "unpacks a single flight with missing times"
      (should=
        {"reservation-id"
         {
          :reservation-id "reservation-id"
          :checked-out-on nil
          :checked-in-on nil
          }}

        (fsp/unpack-flights
          {:items [
                   {:reservationId "reservation-id"
                    }
                   ]}))
      )
    )

  (context "filtering reservations"
    (it "filters empty and simple lists"
      (should= [] (fsp/remove-superceded-reservations []))
      (should= [{:start-time @now}]
               (fsp/remove-superceded-reservations [{:start-time @now}])))

    (it "filters lists with nothing unused"
      (should= [{:tail-number "t1" :co @now}]
               (fsp/remove-superceded-reservations [{:tail-number "t1" :co @now}])))

    (it "filters unused reservations"
      (should= [{:id 2 :tail-number "t2"}
                {:id 3 :tail-number "t1" :co @now}
                {:id 4 :tail-number "t1"}]
               (fsp/remove-superceded-reservations [{:id 1 :tail-number "t1"}
                                                    {:id 2 :tail-number "t2"}
                                                    {:id 3 :tail-number "t1" :co @now}
                                                    {:id 4 :tail-number "t1"}]))
      (should= [{:id 3 :tail-number "t1" :co @now}
                {:id 5 :tail-number "t2" :co @now}
                {:id 6 :tail-number "t1"}
                {:id 7 :tail-number "t2"}]
               (fsp/remove-superceded-reservations
                 [{:id 1 :tail-number "t1"}
                  {:id 2 :tail-number "t2"}
                  {:id 3 :tail-number "t1" :co @now}
                  {:id 4 :tail-number "t2"}
                  {:id 5 :tail-number "t2" :co @now}
                  {:id 6 :tail-number "t1"}
                  {:id 7 :tail-number "t2"}
                  ]))
      )
    )
  )





(def sample-flights
  {111 {:checked-in-on nil
        :checked-out-on nil}
   222 {:checked-in-on "2025-12-03T13:00:00"
        :checked-out-on nil}
   333 {:checked-in-on nil
        :checked-out-on "2025-12-03T12:00:00"}})

(def sample-reservations
  [{:reservation-id 111
    :activity-type "Flight Training"
    :start-time (time/local-date-time 2025 12 03 15 00)     ; 15:00 today
    :checked-in-on nil
    :checked-out-on nil}

   {:reservation-id 222
    :activity-type "Flight Training"
    :start-time (time/local-date-time 2025 12 03 16 00)
    :checked-in-on nil
    :checked-out-on nil}

   {:reservation-id 333
    :activity-type "Flight Training"
    :start-time (time/local-date-time 2025 12 03 10 00)     ; 10:00 this morning
    :checked-in-on nil
    :checked-out-on nil}

   {:reservation-id 444
    :activity-type "Ground School"
    :start-time (time/local-date-time 2025 12 03 15 00)
    :checked-in-on nil}

   {:reservation-id 555
    :activity-type "New Customer"
    :start-time (time/local-date-time 2025 12 03 14 00)
    :checked-in-on nil}

   {:reservation-id 666
    :activity-type "Flight Training"
    :start-time (time/local-date-time 2025 12 03 07 00)     ; 07:00 → more than 6h ago
    :checked-in-on nil}

   {:reservation-id 777
    :activity-type "Flight Training"
    :start-time (time/local-date-time 2025 12 03 15 00)
    :checked-in-on "2025-12-03T14:00:00"}                   ; already checked in
   ])

(describe "filter-active-reservations"
  (with-stubs)
  (with-redefs [time/local-date-time (constantly (time/local-date-time 2025 12 03 16 00))]

    (it "returns only active flight-related reservations that started in the last 6 hours and are not checked in"
      (let [active (fsp/filter-active-reservations sample-reservations sample-flights)
            ids (map :reservation-id active)]

        (should= [111 555] ids)
        ; 111 = Flight Training, recent, not checked in
        ; 555 = New Customer, recent, not checked in
        ))

    (it "includes 'New Customer' reservations even if no flight exists"
      (let [result (fsp/filter-active-reservations sample-reservations sample-flights)
            new-cust (first (filter #(= 555 (:reservation-id %)) result))]
        (should-not-be-nil new-cust)
        (should= "New Customer" (:activity-type new-cust))))

    (it "attaches :co from flight when available (checked-out-on)"
      (let [result (fsp/filter-active-reservations
                     [{:reservation-id 333
                       :activity-type "Flight Training"
                       :start-time (time/local-date-time 2025 12 03 12 00)
                       :checked-in-on nil}]
                     sample-flights)
            res (first result)]
        (should= "2025-12-03T12:00:00" (:co res))))

    (it "excludes reservations that are already checked in (on res or flight)"
      (let [ids (map :reservation-id
                     (fsp/filter-active-reservations sample-reservations sample-flights))]
        (should-not-contain 222 ids)                        ; flight is checked in
        (should-not-contain 777 ids)                        ; reservation is checked in
        ))

    (it "excludes non-flight activities like Ground School"
      (let [ids (map :reservation-id
                     (fsp/filter-active-reservations sample-reservations sample-flights))]
        (should-not-contain 444 ids)))

    (it "excludes flight reservations older than 6 hours"
      (let [ids (map :reservation-id
                     (fsp/filter-active-reservations sample-reservations sample-flights))]
        (should-not-contain 666 ids)))

    (it "prefers flight's checked-in-on if reservation has none"
      (let [reservations [{:reservation-id 222
                           :activity-type "Flight Training"
                           :start-time (time/local-date-time 2025 12 03 16 00)
                           :checked-in-on nil}]
            active (fsp/filter-active-reservations reservations sample-flights)]
        (should-be empty? active)))                         ; because flight 222 is already checked in
    )
  )

(describe "remove-superceded-reservations"
  (it "keeps all reservations when no :co exists for a tail"
    (let [no-checkouts [{:reservation-id 10 :tail-number "N111AA" :co nil}
                        {:reservation-id 11 :tail-number "N111AA" :co nil}
                        {:reservation-id 12 :tail-number "N222BB" :co nil}]
          result (fsp/remove-superceded-reservations no-checkouts)]
      (should= 3 (count result))
      (should= [10 11 12] (sort (map :reservation-id result)))))

  (it "removes the first because a the second is checked out."
    (let [multiple-co [{:reservation-id 20 :tail-number "N555XX" :co nil}
                       {:reservation-id 21 :tail-number "N555XX" :co "2025-12-03T11:00"}
                       {:reservation-id 22 :tail-number "N555XX" :co nil}]
          result (fsp/remove-superceded-reservations multiple-co)
          ids (map :reservation-id result)]
      (should= [21 22] ids)))

  (it "handles nil tail-numbers correctly (no false conflicts)"
    (let [data [{:reservation-id 30 :tail-number nil :co "2025-12-03T09:00"}
                {:reservation-id 31 :tail-number nil :co nil}
                {:reservation-id 32 :tail-number "N333CC" :co "2025-12-03T13:00"}]
          result (fsp/remove-superceded-reservations data)]
      (should= 3 (count result))))

  (it "works with empty input"
    (should-be empty? (fsp/remove-superceded-reservations [])))

  (it "works with single reservation"
    (let [single [{:reservation-id 99 :tail-number "N888QQ" :co nil}]]
      (should= single (fsp/remove-superceded-reservations single))))

  (it "preserves the exact reservation map (including :co) for kept items"
    (let [input [{:reservation-id 100 :tail-number "N777RR" :co "2025-12-03T15:00"}]
          result (fsp/remove-superceded-reservations input)
          kept (first result)]
      (should= "2025-12-03T15:00" (:co kept))
      (should= 100 (:reservation-id kept))))
  )
