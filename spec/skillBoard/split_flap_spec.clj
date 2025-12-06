(ns skillBoard.split-flap-spec
  (:require
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
