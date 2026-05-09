(ns skillBoard.presenters.screen-spec
  (:require
    [quil.core :as q]
    [skillBoard.config :as config]
    [skillBoard.presenters.screen :as screen]
    [speclj.core :refer :all]))

(describe "screen presenter helpers"
  (it "sets up and draws column headers from flap columns"
    (let [calls (atom [])]
      (with-redefs [config/display-info (atom {:label-height 20
                                               :top-margin 40})
                    q/text-font (fn [& args] (swap! calls conj (into [:text-font] args)))
                    q/text-size (fn [& args] (swap! calls conj (into [:text-size] args)))
                    q/text-align (fn [& args] (swap! calls conj (into [:text-align] args)))
                    q/fill (fn [& args] (swap! calls conj (into [:fill] args)))
                    q/text (fn [& args] (swap! calls conj (into [:text] args)))]
        (screen/draw-column-headers 10 :header-font 8 [["A" 0] ["B" 3]])
        (should-contain [:text-font :header-font] @calls)
        (should-contain [:text-size 8] @calls)
        (should-contain [:text-align :left :center] @calls)
        (should-contain [:fill 255 255 255] @calls)
        (should-contain [:text "A" 0 48.0] @calls)
        (should-contain [:text "B" 30 48.0] @calls)))))
