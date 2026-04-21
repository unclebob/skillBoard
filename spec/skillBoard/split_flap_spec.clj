(ns skillBoard.split-flap-spec
  (:require
    [skillBoard.atoms :as atoms]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.presenters.main :as presenter]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.split-flap :as split-flap]
    [quil.core :as q]
    [speclj.core :refer :all]))

(defn- make-lines [& strings]
  (map (fn [s] {:line s}) strings))

(defn- fake-graphics
  ([calls] (fake-graphics calls 80 40))
  ([calls width height]
   (let [graphics (proxy [processing.core.PGraphics] []
                    (beginDraw [] (swap! calls conj [:begin-draw]))
                    (endDraw [] (swap! calls conj [:end-draw]))
                    (clear [] (swap! calls conj [:clear]))
                    (noStroke [] (swap! calls conj [:no-stroke]))
                    (noSmooth [] (swap! calls conj [:no-smooth]))
                    (textFont [font] (swap! calls conj [:layer-text-font font]))
                    (textSize [size] (swap! calls conj [:layer-text-size size]))
                    (textAlign
                      ([align-x] (swap! calls conj [:layer-text-align align-x]))
                      ([align-x align-y] (swap! calls conj [:layer-text-align align-x align-y])))
                    (fill
                      ([gray] (swap! calls conj [:layer-fill gray]))
                      ([r g b] (swap! calls conj [:layer-fill r g b]))
                      ([r g b a] (swap! calls conj [:layer-fill r g b a])))
                    (rect [x y w h] (swap! calls conj [:layer-rect x y w h]))
                    (text [text x y] (swap! calls conj [:layer-text text x y])))]
     (set! (.-width graphics) width)
     (set! (.-height graphics) height)
     graphics)))

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

(describe "rendering helpers"
  (before
    (reset! split-flap/lines-layer-cache {:key nil :layer nil})
    (reset! split-flap/flapper-glyph-cache {:key nil :glyphs nil})
    (reset! comm/reservation-com-errors 0)
    (reset! comm/adsb-com-errors 2)
    (reset! comm/weather-com-errors 4)
    (reset! atoms/clock-pulse true)
    (reset! presenter/screen-type :test))

  (it "renders lines, glyphs, flappers, status lights, and header elements"
    (let [calls (atom [])
          created-layers (atom [])
          make-layer (fn []
                       (let [layer (fake-graphics calls)]
                         (swap! created-layers conj layer)
                         layer))
          state {:sf-font nil
                 :sf-font-size 12
                 :clock-font nil
                 :clock-font-size 8
                 :lines [{:line "AB" :color :red}
                         {:line "" :color :blue}]
                 :flappers [{:at [0 0] :from \A :to \B}
                            {:at [1 0] :from \space :to \C}]
                 :font-width 10
                 :font-height 20
                 :header-font nil
                 :header-font-size 14
                 :label-font-size 6
                 :departure-icon :departure-icon}]
      (with-redefs [config/display-info (atom {:sf-char-gap 2
                                               :top-margin 30
                                               :label-height 10})
                    q/background (fn [& args] (swap! calls conj (into [:background] args)))
                    q/create-graphics (fn [_ _] (make-layer))
                    q/fill (fn [& args] (swap! calls conj (into [:fill] args)))
                    q/image (fn [& args] (swap! calls conj (into [:image] args)))
                    q/rect (fn [& args] (swap! calls conj (into [:rect] args)))
                    q/text (fn [& args] (swap! calls conj (into [:text] args)))
                    q/text-align (fn [& args] (swap! calls conj (into [:text-align] args)))
                    q/text-font (fn [& args] (swap! calls conj (into [:text-font] args)))
                    q/text-size (fn [& args] (swap! calls conj (into [:text-size] args)))
                    q/text-width (fn [_] 20)
                    q/ellipse (fn [& args] (swap! calls conj (into [:ellipse] args)))
                    q/width (fn [] 100)
                    q/height (fn [] 50)
                    screen/header-text (fn [_] "HEADER")
                    screen/display-column-headers (fn [& args] (swap! calls conj (into [:headers] args)))]
        (split-flap/draw state)
        (split-flap/draw state)
        (should= 2 (count (filter #(= [:background 30] %) @calls)))
        (should-contain [:text "HEADER" 40 15] @calls)
        (should-contain [:ellipse 20 20 10 10] @calls)
        (should-contain [:ellipse 20 35 10 10] @calls)
        (should-contain [:ellipse 20 50 10 10] @calls)
        (should (some #(= :layer-rect (first %)) @calls))
        (should (some #(= :layer-text (first %)) @calls))
        (should (some #(and (= :image (first %))
                            (= (first @created-layers) (second %)))
                      @calls)))))

  (it "exercises private rendering branches directly"
    (let [calls (atom [])
          layer (fake-graphics calls)
          geometry {:flap-width 12
                    :flap-height 20
                    :label-margin 40
                    :backing-rect-top-left-x 1
                    :backing-rect-top-left-y 2
                    :backing-rect-width 8
                    :backing-rect-height 16}]
      (with-redefs [config/display-info (atom {:sf-char-gap 2
                                               :top-margin 30
                                               :label-height 10})
                    config/cols 3
                    q/create-graphics (fn [_ _] (fake-graphics calls))
                    q/width (fn [] 80)
                    q/height (fn [] 40)
                    q/image (fn [& args] (swap! calls conj (into [:image] args)))
                    q/fill (fn [& args] (swap! calls conj (into [:fill] args)))
                    q/rect (fn [& args] (swap! calls conj (into [:rect] args)))
                    q/text (fn [& args] (swap! calls conj (into [:text] args)))
                    q/text-align (fn [& args] (swap! calls conj (into [:text-align] args)))
                    q/text-font (fn [& args] (swap! calls conj (into [:text-font] args)))
                    q/text-size (fn [& args] (swap! calls conj (into [:text-size] args)))]
        (should= 0 (#'split-flap/render-report-line! layer "" :white 0 12 20 40 1 2 8 16))
        (should= 2 (#'split-flap/render-line-chars! layer :white "A B" 5 12 1 2 8 16))
        (should= {:line-char-count 2 :line-rect-count 2 :line-text-count 2}
                 (#'split-flap/render-lines-layer! layer [{:line "A " :color :white}
                                                          {:line " B" :color :yellow}]
                                                   nil 10 12 20 40 1 2 8 16))
        (reset! split-flap/lines-layer-cache {:key :old :layer (fake-graphics calls 80 40)})
        (should= true (:size-match? (#'split-flap/current-lines-layer)))
        (should= [24 80] (#'split-flap/flapper-position [2 2] geometry))
        (#'split-flap/draw-uncached-flapper! :cyan \Z 3 4 nil 10 geometry)
        (#'split-flap/draw-flapper! [:cyan] {} nil 10 geometry {:at [0 0] :from \Z})
        (should= [255 0 0] (#'split-flap/status-light-rgb 4))
        (should= [255 165 0] (#'split-flap/status-light-rgb 2))
        (should= [0 255 0] (#'split-flap/status-light-rgb 0))
        (should (some #(= :layer-rect (first %)) @calls))
        (should (some #(= :rect (first %)) @calls))))))

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
