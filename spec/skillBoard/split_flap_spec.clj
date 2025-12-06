(ns skillBoard.split-flap-spec
  (:require
    [quil.core :as q]
    [skillBoard.presenter :as presenter]
    [skillBoard.split-flap :as split-flap]
    [speclj.core :refer :all]))

(defn- make-lines [& strings]
  (map (fn [s] {:line s}) strings))

(describe "flapper creation"
  (it "handles degenerate cases"
    (should= [] (split-flap/make-flappers [] [])))

  (it "handles identical strings producing no flappers"
    (should= [] (split-flap/make-flappers (make-lines "a" "a")
                                          (make-lines "a" "a"))))

  (it "creates a flapper if one character on one line is different"
    (should= [{:at [0 0] :from \b :to \a}]
             (split-flap/make-flappers (make-lines "a") (make-lines "b")))
    (should= [{:at [1 0] :from \b :to \a}]
             (split-flap/make-flappers (make-lines "xa") (make-lines "xb"))))

  (it "creates many flappers if many characters on one line are different"
    (should= [{:at [0 0] :from \R :to \B}
              {:at [5 0] :from \o :to \a}
              {:at [8 0] :from \o :to \i}]
             (split-flap/make-flappers (make-lines "Bob Martin") (make-lines "Rob Morton"))))

  (it "creates extra flappers for single lines of differing length"
    (should= [{:at [1 0] :from \b :to \space}]
             (split-flap/make-flappers (make-lines "a") (make-lines "ab")))
    (should= [{:at [1 0] :from \space :to \b}]
             (split-flap/make-flappers (make-lines "ab") (make-lines "a")))
    )

  (it "creates flappers for more than one line"
    (should= [{:at [0 0] :from \R :to \B}
              {:at [1 1] :from \o :to \a}
              {:at [4 1] :from \o :to \i}]
             (split-flap/make-flappers (make-lines "Bob" "Martin")
                                       (make-lines "Rob" "Morton")))
    )

  (it "handles reports with different numbers of rows."
    (should= [{:at [0 1] :from \space :to \B}]
             (split-flap/make-flappers (make-lines "A" "B")
                                       (make-lines "A")))
    (should= [{:at [0 1] :from \B :to \space}]
             (split-flap/make-flappers (make-lines "A")
                                       (make-lines "A" "B")))
    (should= [{:at [0 1] :from \B :to \space}
              {:at [0 2] :from \C :to \space}]
             (split-flap/make-flappers (make-lines "A")
                                       (make-lines "A" "B" "C")))
    (should= [{:at [1 1] :from \space :to \a}
              {:at [1 2] :from \x :to \space}
              {:at [0 3] :from \space :to \D}
              {:at [1 3] :from \space :to \b}
              ]
             (split-flap/make-flappers (make-lines "A" "Ba" "C" "Db")
                                       (make-lines "A" "B" "Cx")))
    )
  )

(describe "flappers"
  (it "increments the flappers"
    (with-redefs [rand (fn [] 0)]
      (should= [{:at [0 0] :from \C :to \A}]
               (split-flap/update-flappers [{:at [0 0] :from \B :to \A}]))))

  (it "deletes a finished flapper"
    (should= []
             (split-flap/update-flappers [{:at [0 0] :from \A :to \A}]))))

(describe "utilities"
  (it "should pad and trim lines"
    (should= "   " (split-flap/pad-and-trim-line "" 3))
    (should= "abc" (split-flap/pad-and-trim-line "abc" 3))
    (should= "ab " (split-flap/pad-and-trim-line "ab" 3))
    (should= "abc" (split-flap/pad-and-trim-line "abcd" 3))
    ))

(describe "do-update"
  (it "does not poll when time is recent and no mouse press"
    (let [now (System/currentTimeMillis)
          recent-time (- now 10000) ; 10 seconds ago, less than 20
          old-lines (make-lines "OLD LINE")
          state {:time recent-time :flappers [] :lines old-lines :pulse false}]
      (with-redefs [q/mouse-pressed? (fn [] false)
                    presenter/make-screen (fn [] (throw (Exception. "Should not poll")))]
        (let [new-state (split-flap/do-update state)]
          (should= old-lines (:lines new-state))
          (should= recent-time (:time new-state)) ; time not updated
          (should= true (:pulse new-state))))))

  (it "polls when mouse is pressed"
    (let [now (System/currentTimeMillis)
          recent-time (- now 10000)
          old-lines (make-lines "OLD LINE")
          new-lines (make-lines "NEW LINE")
          state {:time recent-time :flappers [] :lines old-lines :pulse false}]
      (with-redefs [q/mouse-pressed? (fn [] true)
                    presenter/make-screen (fn [] new-lines)]
        (let [new-state (split-flap/do-update state)]
          (should= new-lines (:lines new-state))
          (should-not= recent-time (:time new-state)) ; time updated
          (should-not (empty? (:flappers new-state)))
          (should= true (:pulse new-state))))))

  (it "polls when time since last poll exceeds threshold"
    (let [now (System/currentTimeMillis)
          old-time (- now (* 21 1000)) ; 21 seconds ago
          old-lines (make-lines "OLD LINE")
          new-lines (make-lines "NEW LINE")
          state {:time old-time :flappers [] :lines old-lines :pulse false}]
      (with-redefs [q/mouse-pressed? (fn [] false)
                    presenter/make-screen (fn [] new-lines)]
        (let [new-state (split-flap/do-update state)]
          (should= new-lines (:lines new-state))
          (should-not= old-time (:time new-state))
          (should-not (empty? (:flappers new-state)))
          (should= true (:pulse new-state))))))
  )