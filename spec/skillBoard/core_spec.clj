(ns skillBoard.core-spec
  (:require
    [java-time.api :as time]
    [skillBoard.config :as config]
    [skillBoard.core :refer :all]
    [skillBoard.core-utils :as core-utils]
    [skillBoard.heartbeat :as heartbeat]
    [speclj.core :refer :all]))

(defn make-status-item [{:keys [tail-number
                                start-time
                                pilot-name
                                instructor-name
                                checked-out-on]}
                        flight]
  (let [item {:aircraft tail-number
              :time start-time
              :crew {:pilot pilot-name
                     :instructor instructor-name}
              :remarks "Reserved"
              }]
    (cond
      (and (nil? flight) (nil? checked-out-on))
      item

      (and (nil? flight) (some? checked-out-on))
      (assoc item
        :remarks "Checked out"
        :time checked-out-on)
      )
    )
  )

(declare now now-str reservation)

(describe "skillBoard"
  (with now (time/local-date-time "yyyy-MM-dd'T'HH:mm:ss.SS" "2025-06-16T09:55:24.49"))
  (with now-str (time/format "yyyy-MM-dd'T'HH:mm:ss.SS" @now))

  (context "status items"
    (with reservation {:reservation-id "id1"
                       :tail-number "N345TS"
                       :activity-type "Flight"
                       :start-time @now
                       :pilot-name ["John" "Doe"]
                       :instructor-name ["Jane" "Smith"]
                       :reservation-status "Reserved"
                       })
    (it "builds a status item with no check-out or flight"
      (should=
        {:time @now
         :aircraft "N345TS"
         :crew {:pilot ["John" "Doe"]
                :instructor ["Jane" "Smith"]}
         :remarks "Reserved"}
        (make-status-item @reservation nil)))

    (it "builds a status item with a check-out time but no flight"
      (let [check-out-time (time/plus @now (time/days 1))
            reservation (assoc @reservation :checked-out-on check-out-time)]
        (should=
          {:time (time/plus @now (time/days 1))
           :aircraft "N345TS"
           :crew {:pilot ["John" "Doe"]
                  :instructor ["Jane" "Smith"]}
           :remarks "Checked out"}
          (make-status-item reservation nil))))
    )

  (context "background polling coordination"
    (it "starts heartbeat reporting after the initial data poll"
      (let [calls (atom [])]
        (with-redefs [poll (fn [] (swap! calls conj :poll))
                      heartbeat/start! (fn [] (swap! calls conj :heartbeat))]
          (initialize-polling!)
          (should= [:poll :heartbeat] @calls))))

    (it "checks data polling and heartbeat schedules on every coordinator step"
      (let [calls (atom [])]
        (with-redefs [run-due-poll! (fn [now] (swap! calls conj [:poll now]))
                      heartbeat/run-due! (fn [now] (swap! calls conj [:heartbeat now]))
                      update-clock-pulse! (fn [now] (swap! calls conj [:clock now]))]
          (run-polling-step! 12345)
          (should= [[:poll 12345] [:heartbeat 12345] [:clock 12345]] @calls)))))

  (context "operational events"
    (it "marks application starts independently of the readable message"
      (let [events (atom [])]
        (with-redefs [core-utils/log-event
                      (fn [level event message]
                        (swap! events conj [level event message]))]
          (log-application-start!)
          (should= [[:status
                     :application-start
                     (str "skillBoard v" config/version " has begun.")]]
                   @events)))))
  )

