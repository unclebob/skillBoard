(ns skillBoard.core-spec
  (:require
    [java-time.api :as time]
    [skillBoard.foundation.config :as config]
    [skillBoard.adapters.heartbeat :as heartbeat]
    [skillBoard.adapters.text-util :as text]
    [skillBoard.core :refer [key-released log-application-start!]]
    [skillBoard.core.polling :refer [poll poll-due? initialize-polling! run-due-poll!
                                     run-polling-step! update-clock-pulse!]]
    [skillBoard.foundation.atoms :as atoms]
    [skillBoard.foundation.core-utils :as core-utils]
    [skillBoard.gateways.airspace :as airspace]
    [skillBoard.gateways.aviation-weather :as weather]
    [skillBoard.gateways.flight-schedule-pro :as fsp]
    [skillBoard.gateways.radar-cape :as radar]
    [skillBoard.gateways.wind-data :as wind-data]
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

(describe "display measurements"
  (it "fits a split-flap column to the screen width"
    (should= {:font-width 9.2
              :sf-char-gap 0.8}
             (#'skillBoard.core/column-metrics (+ (* config/cols 10) 10))))

  (it "derives header and label margins from the screen height"
    (should= {:top-margin 100.0
              :label-height 30.0}
             (#'skillBoard.core/screen-margins 1000)))

  (it "counts how many split-flap lines fit under the header"
    (should= 34.0
             (#'skillBoard.core/text-line-count 1000 100 30 20)))

  (it "sizes the header, label, and clock fonts from the margins"
    (with-redefs [text/find-font-size-for-height (fn [_font demand] demand)]
      (should= {:header-font-size 70.0
                :label-font-size 24.0
                :clock-font-size 50.0}
               (#'skillBoard.core/fitted-font-sizes :header :clock 100 30))))

  (it "reads launch flags"
    (should= {:args #{}
              :window? false
              :test? false
              :log-stdout? true}
             (#'skillBoard.core/launch-options []))
    (should= {:args #{"-w" "-t" "--no-stdout"}
              :window? true
              :test? true
              :log-stdout? false}
             (#'skillBoard.core/launch-options ["-w" "-t" "--no-stdout"]))))

(describe "key-released"
  (it "requests a poll for p, a screen change for space, and ignores other keys"
    (let [state {:sketch true}
          poll-key @atoms/poll-key
          change-screen? @atoms/change-screen?]
      (try
        (reset! atoms/poll-key false)
        (reset! atoms/change-screen? false)
        (should= state (key-released state {:key :p}))
        (should= true @atoms/poll-key)
        (should= false @atoms/change-screen?)
        (reset! atoms/poll-key false)
        (should= state (key-released state {:key :space}))
        (should= true @atoms/change-screen?)
        (should= false @atoms/poll-key)
        (reset! atoms/change-screen? false)
        (should= state (key-released state {:key :a}))
        (should= false @atoms/poll-key)
        (should= false @atoms/change-screen?)
        (finally
          (reset! atoms/poll-key poll-key)
          (reset! atoms/change-screen? change-screen?))))))

(describe "board snapshot"
  (it "adds aviation weather and airspace failures into the weather count"
    (let [weather-errors @weather/weather-com-errors
          airspace-errors @airspace/airspace-com-errors]
      (try
        (reset! weather/weather-com-errors 2)
        (reset! airspace/airspace-com-errors 3)
        (should= 5 (:weather (:com-errors (skillBoard.core.polling/board-snapshot))))
        (finally
          (reset! weather/weather-com-errors weather-errors)
          (reset! airspace/airspace-com-errors airspace-errors))))))

(describe "polling decisions"
  (around [it]
    (let [poll-key @atoms/poll-key
          poll-time @atoms/poll-time
          clock-pulse @atoms/clock-pulse
          log-traffic? @atoms/log-traffic?]
      (try
        (it)
        (finally
          (reset! atoms/poll-key poll-key)
          (reset! atoms/poll-time poll-time)
          (reset! atoms/clock-pulse clock-pulse)
          (reset! atoms/log-traffic? log-traffic?)))))

  (it "is due when a poll is requested or the interval has elapsed"
    (reset! atoms/poll-key true)
    (reset! atoms/poll-time 40000)
    (should (poll-due? 40000))
    (reset! atoms/poll-key false)
    (should-not (poll-due? 30000))
    (should-not (poll-due? (+ 40000 (* 59 1000))))
    (should (poll-due? (+ 40000 (* 60 1000)))))

  (it "blinks the clock only after the half-second"
    (update-clock-pulse! 0)
    (should-not @atoms/clock-pulse)
    (update-clock-pulse! 500)
    (should-not @atoms/clock-pulse)
    (update-clock-pulse! 501)
    (should @atoms/clock-pulse))

  (it "polls and clears the request only when a poll is due"
    (let [polled (atom 0)]
      (with-redefs [poll (fn [] (swap! polled inc))
                    future-call (fn [f] (f))]
        (reset! atoms/poll-key false)
        (reset! atoms/poll-time 1000)
        (run-due-poll! 1000)
        (should= 0 @polled)
        (should= 1000 @atoms/poll-time)
        (reset! atoms/poll-key true)
        (run-due-poll! 5000)
        (should= 1 @polled)
        (should= false @atoms/poll-key)
        (should= 5000 @atoms/poll-time))))

  (it "marks traffic logging after a successful poll and logs a failed poll"
    (let [logged (atom [])
          calls (atom [])
          quiet (fn [name]
                  (fn [& _] (swap! calls conj name)))]
      (with-redefs [fsp/get-aircraft (quiet :aircraft)
                    radar/get-adsb-by-tail-numbers (quiet :adsb)
                    fsp/get-flights (quiet :flights)
                    fsp/get-reservations (quiet :reservations)
                    weather/get-metars (quiet :metars)
                    weather/get-nearby-metars (quiet :nearby-metars)
                    airspace/get-airspace-classes (quiet :airspace)
                    weather/get-metar-history (quiet :history)
                    weather/get-tafs (quiet :tafs)
                    radar/get-nearby-adsb (quiet :nearby-adsb)
                    wind-data/refresh-wind-grid-if-due! (quiet :wind)
                    core-utils/log (fn [level e]
                                     (swap! logged conj [level (.getMessage e)]))]
        (reset! atoms/log-traffic? false)
        (poll)
        (should= true @atoms/log-traffic?)
        (should= [:aircraft :adsb :flights :reservations :metars :nearby-metars
                  :airspace :history :tafs :nearby-adsb :wind]
                 @calls)
        (with-redefs [fsp/get-aircraft (fn [] (throw (ex-info "down" {})))]
          (poll)
          (should= [:error "down"] (first @logged)))))))

