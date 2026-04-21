(ns skillBoard.split-flap-spec
  (:require
    [skillBoard.atoms :as atoms]
    [skillBoard.presenters.main :as presenter]
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

(describe "state update"
  (before
    (reset! atoms/screen-changed? false))

  (it "builds a blank transition when the screen changed flag is set"
    (let [blank [{:line "blank"}]
          flappers [{:at [0 0] :from \space :to \A}]]
      (reset! atoms/screen-changed? true)
      (with-redefs [split-flap/current-time-ms (fn [] 1000)
                    split-flap/blank-screen (fn [] blank)
                    split-flap/make-flappers (fn [new old] [{:new new :old old}])]
        (should= {:time 1000
                  :lines blank
                  :flappers [{:new blank :old blank}]}
                 (select-keys (split-flap/do-update {:time 500
                                                      :lines [{:line "old"}]
                                                      :flappers flappers})
                              [:time :lines :flappers]))
        (should= false @atoms/screen-changed?))))

  (it "builds flappers from old to new lines when the presenter changes"
    (with-redefs [split-flap/current-time-ms (fn [] 1000)
                  presenter/make-screen (fn [] [{:line "new"}])
                  split-flap/make-flappers (fn [new old] [{:new new :old old}])]
      (should= {:time 1000
                :lines [{:line "new"}]
                :flappers [{:new [{:line "new"}] :old [{:line "old"}]}]}
               (select-keys (split-flap/do-update {:time 500
                                                    :lines [{:line "old"}]
                                                    :flappers [{:existing true}]})
                            [:time :lines :flappers]))))

  (it "clears flappers after the flap duration expires"
    (with-redefs [split-flap/current-time-ms (fn [] 10000)
                  presenter/make-screen (fn [] [{:line "same"}])]
      (should= []
               (:flappers (split-flap/do-update {:time 0
                                                  :lines [{:line "same"}]
                                                  :flappers [{:existing true}]})))))

  (it "advances existing flappers while the screen is unchanged and active"
    (with-redefs [split-flap/current-time-ms (fn [] 1000)
                  presenter/make-screen (fn [] [{:line "same"}])
                  split-flap/update-flappers (fn [flappers] (conj flappers {:advanced true}))]
      (should= [{:existing true} {:advanced true}]
               (:flappers (split-flap/do-update {:time 900
                                                  :lines [{:line "same"}]
                                                  :flappers [{:existing true}]}))))))
