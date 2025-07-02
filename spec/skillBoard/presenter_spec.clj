(ns skillBoard.presenter-spec
  (:require [skillBoard.presenter :as presenter]
            [speclj.core :refer :all]
            ))

(describe "flapper creation"
  (it "handles degenerate cases"
    (should= [] (presenter/make-flappers [] [])))

  (it "handles identical strings producing no flappers"
    (should= [] (presenter/make-flappers ["a" "a"] ["a" "a"])))

  (it "creates a flapper if one character on one line is different"
    (should= [{:at [0 0] :from \b :to \a}]
             (presenter/make-flappers ["a"] ["b"]))
    (should= [{:at [1 0] :from \b :to \a}]
             (presenter/make-flappers ["xa"] ["xb"]))
    )

  (it "creates many flappers if many characters on one line are different"
    (should= [{:at [0 0] :from \R :to \B}
              {:at [5 0] :from \o :to \a}
              {:at [8 0] :from \o :to \i}]
             (presenter/make-flappers ["Bob Martin"] ["Rob Morton"]))
    )

  (it "creates extra flappers for single lines of differing length"
    (should= [{:at [1 0] :from \b :to \space}]
             (presenter/make-flappers ["a"] ["ab"]))
    (should= [{:at [1 0] :from \space :to \b}]
             (presenter/make-flappers ["ab"] ["a"]))
    )

  (it "creates flappers for more than one line"
    (should= [{:at [0 0] :from \R :to \B}
              {:at [1 1] :from \o :to \a}
              {:at [4 1] :from \o :to \i}]
             (presenter/make-flappers ["Bob" "Martin"]
                                      ["Rob" "Morton"]))
    )

  (it "handles reports with different numbers of rows."
    (should= [{:at [0 1] :from \space :to \B}]
             (presenter/make-flappers ["A" "B"]
                                      ["A"]))
    (should= [{:at [0 1] :from \B :to \space}]
             (presenter/make-flappers ["A"]
                                      ["A" "B"]))
    (should= [{:at [0 1] :from \B :to \space}
              {:at [0 2] :from \C :to \space}]
             (presenter/make-flappers ["A"]
                                      ["A" "B" "C"]))
    (should= [{:at [1 1] :from \space :to \a}
              {:at [1 2] :from \x :to \space}
              {:at [0 3] :from \space :to \D}
              {:at [1 3] :from \space :to \b}
              ]
             (presenter/make-flappers ["A" "Ba" "C" "Db"]
                                      ["A" "B" "Cx"]))
    )
  )