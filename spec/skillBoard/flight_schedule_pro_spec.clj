(ns skillBoard.flight-schedule-pro-spec
  (:require [java-time.api :as time]
            [skillBoard.config :as config]
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
