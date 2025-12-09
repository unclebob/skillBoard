(ns skillBoard.presenters.main-spec
  (:require
    [quil.core :as q]
    [skillBoard.config :as config]
    [skillBoard.presenters.airports :as airports]
    [skillBoard.presenters.flights :as flights]
    [skillBoard.presenters.main :as main]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.presenters.utils :as utils]
    [skillBoard.presenters.weather :as weather]
    [speclj.core :refer :all]))

(defmethod screen/make :next-screen [_] nil)
(defmethod screen/make :timeout-screen [_] nil)

(describe "make-screen"
  (it "returns flights screen when screen-type is :flights and mouse not pressed"
    (with-redefs [q/mouse-pressed? (fn [] false)
                  utils/get-now (fn [] 1000)
                  flights/make-flights-screen (fn [_res _fl] "mocked flights screen")]
      (reset! main/screen-type :flights)
      (reset! main/screen-duration 10)
      (reset! main/screen-start-time 0)
      (should= "mocked flights screen" (main/make-screen))))

  (it "returns taf screen when screen-type is :taf and mouse not pressed"
    (with-redefs [q/mouse-pressed? (fn [] false)
                  utils/get-now (fn [] 1000)
                  weather/make-taf-screen (fn [] "mocked taf screen")]
      (reset! main/screen-type :taf)
      (reset! main/screen-duration 10)
      (reset! main/screen-start-time 0)
      (should= "mocked taf screen" (main/make-screen))))

  (it "returns flight-category screen when screen-type is :airports and mouse not pressed"
    (with-redefs [q/mouse-pressed? (fn [] false)
                  utils/get-now (fn [] 1000)
                  airports/make-airports-screen (fn [] "mocked flight-category screen")]
      (reset! main/screen-type :airports)
      (reset! main/screen-duration 10)
      (reset! main/screen-start-time 0)
      (should= "mocked flight-category screen" (main/make-screen))))

  (it "updates atoms when mouse is pressed"
    (let [original-screens [{:screen :next-screen :duration 15} {:screen :another :duration 25}]]
      (reset! config/screens original-screens)
      (reset! main/screen-type :flights)
      (reset! main/screen-duration 10)
      (reset! main/screen-start-time 1000)
      (with-redefs [q/mouse-pressed? (fn [] true)
                    utils/get-now (fn [] 2000)]
        (main/make-screen)
        (should= :next-screen @main/screen-type)
        (should= 15 @main/screen-duration)
        (should= 2000 @main/screen-start-time)
        (should= (rest original-screens) @config/screens))))

  (it "updates atoms when screen duration times out"
    (let [original-screens [{:screen :timeout-screen :duration 20} {:screen :next :duration 30}]]
      (reset! config/screens original-screens)
      (reset! main/screen-type :flights)
      (reset! main/screen-duration 5)
      (reset! main/screen-start-time 1000)
      (with-redefs [q/mouse-pressed? (fn [] false)
                    utils/get-now (fn [] 7100)]
        (main/make-screen)
        (should= :timeout-screen @main/screen-type)
        (should= 20 @main/screen-duration)
        (should= 7100 @main/screen-start-time)
        (should= (rest original-screens) @config/screens)))))