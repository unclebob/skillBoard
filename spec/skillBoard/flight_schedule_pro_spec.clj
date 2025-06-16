(ns flight-schedule-pro-spec
  (:require [java-time.api :as time]
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
      (should= {"id"
                {:reservationId "id"
                 :tail-number "tail-number"
                 :activity-type "Flight type"
                 :start-time @now
                 :pilot-name ["pilot first name" "pilot last name"]
                 :instructor-name ["instructor first name" "instructor last name"]
                 :reservation-status "reservation status"
                 :checked-in-on @now
                 :checked-out-on @now
                 }
                }

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
      (should= {"id" {:reservationId "id"
                      :tail-number "tail-number"
                      :activity-type "Flight type"
                      :start-time @now
                      :pilot-name nil
                      :instructor-name nil
                      :reservation-status "reservation status"
                      :checked-in-on nil
                      :checked-out-on nil
                      }}

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
      (should= [{
                 :reservation-id "reservation-id"
                 :checked-out-on @now
                 :checked-in-on @now
                 }]

               (fsp/unpack-flights
                 {:items [
                          {:reservationId "reservation-id"
                           :checkedOutOn @now-str
                           :checkedInOn @now-str
                           }
                          ]})
               )

      )

    (it "unpacks a single flight with missing times"
          (should= [{
                     :reservation-id "reservation-id"
                     :checked-out-on nil
                     :checked-in-on nil
                     }]

                   (fsp/unpack-flights
                     {:items [
                              {:reservationId "reservation-id"
                               }
                              ]})
                   )

          )
    )

  (context "utilities"
    (with now (time/local-date-time "yyyy-MM-dd'T'HH:mm:ss.SS" "2025-06-16T09:55:24.00"))

    (it "gets HH:mm from time"
      (should= "09:55"
               (fsp/get-HHmm @now)))

    (it "converts time strings to times"
      (should= @now (fsp/parse-time "2025-06-16T09:55:24.00"))
      (should= @now (fsp/parse-time "2025-06-16T09:55:24"))
      )
    )
  )
