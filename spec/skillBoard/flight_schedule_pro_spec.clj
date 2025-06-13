(ns flight-schedule-pro-spec
  (:require [skillBoard.flight-schedule-pro :as fsp]
            [speclj.core :refer :all]))
;reservationStatus {:id 1, :name "Checked Out"} {:id 2, :name "Completed"} {:id 0, :name "Reserved"}

(describe "Flight Schedule Pro"
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
                 :start-time "start-time"
                 :pilot-name ["pilot first name" "pilot last name"]
                 :instructor-name ["instructor first name" "instructor last name"]
                 :reservationStatus "reservation status"
                 }
                }

               (fsp/unpack-reservations
                 {:items [{:reservationId "id"
                           :aircraft {:tail-number "tail-number"}
                           :activityType {:name "Flight type"}
                           :startTime "start-time"
                           :pilots [{:firstName "pilot first name" :lastName "pilot last name"}]
                           :instructor {:firstName "instructor first name" :lastName "instructor last name"}
                           :reservationStatus {:name "reservation status"}
                           }]})
               )
      )

    (it "unpacks a single reservation with missing names"
      (should= {"id" {:reservationId "id"
                      :tail-number "tail-number"
                      :activity-type "Flight type"
                      :start-time "start-time"
                      :pilot-name nil
                      :instructor-name nil
                      :reservationStatus "reservation status"
                      }}

               (fsp/unpack-reservations
                 {:items [{:reservationId "id"
                           :aircraft {:tail-number "tail-number"}
                           :activityType {:name "Flight type"}
                           :startTime "start-time"
                           :reservationStatus {:name "reservation status"}
                           }]})
               )
      )
    )
  )
