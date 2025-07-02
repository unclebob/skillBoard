(ns skillBoard.split-flap-spec
  (:require [skillBoard.split-flap :as split-flap]
            [speclj.core :refer :all]))

(describe "flapper creation"
  (it "handles degenerate cases"
    (should= [] (split-flap/make-flappers [] [])))

  (it "handles identical strings producing no flappers"
    (should= [] (split-flap/make-flappers ["a" "a"] ["a" "a"])))

  (it "creates a flapper if one character on one line is different"
    (should= [{:at [0 0] :from \b :to \a}]
             (split-flap/make-flappers ["a"] ["b"]))
    (should= [{:at [1 0] :from \b :to \a}]
             (split-flap/make-flappers ["xa"] ["xb"]))
    )

  (it "creates many flappers if many characters on one line are different"
    (should= [{:at [0 0] :from \R :to \B}
              {:at [5 0] :from \o :to \a}
              {:at [8 0] :from \o :to \i}]
             (split-flap/make-flappers ["Bob Martin"] ["Rob Morton"]))
    )

  (it "creates extra flappers for single lines of differing length"
    (should= [{:at [1 0] :from \b :to \space}]
             (split-flap/make-flappers ["a"] ["ab"]))
    (should= [{:at [1 0] :from \space :to \b}]
             (split-flap/make-flappers ["ab"] ["a"]))
    )

  (it "creates flappers for more than one line"
    (should= [{:at [0 0] :from \R :to \B}
              {:at [1 1] :from \o :to \a}
              {:at [4 1] :from \o :to \i}]
             (split-flap/make-flappers ["Bob" "Martin"]
                                       ["Rob" "Morton"]))
    )

  (it "handles reports with different numbers of rows."
    (should= [{:at [0 1] :from \space :to \B}]
             (split-flap/make-flappers ["A" "B"]
                                       ["A"]))
    (should= [{:at [0 1] :from \B :to \space}]
             (split-flap/make-flappers ["A"]
                                       ["A" "B"]))
    (should= [{:at [0 1] :from \B :to \space}
              {:at [0 2] :from \C :to \space}]
             (split-flap/make-flappers ["A"]
                                       ["A" "B" "C"]))
    (should= [{:at [1 1] :from \space :to \a}
              {:at [1 2] :from \x :to \space}
              {:at [0 3] :from \space :to \D}
              {:at [1 3] :from \space :to \b}
              ]
             (split-flap/make-flappers ["A" "Ba" "C" "Db"]
                                       ["A" "B" "Cx"]))
    )
  )

(describe "flappers"
  (it "increments the flappers"
    (should= [{:at [0 0] :from \C :to \A}]
             (split-flap/update-flappers [{:at [0 0] :from \B :to \A}]))
    )

  (it "deletes a finished flapper"
    (should= []
             (split-flap/update-flappers [{:at [0 0] :from \A :to \A}])))
  )