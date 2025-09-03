(ns skillBoard.text-util-spec
  (:require [skillBoard.text-util :as text]
            [speclj.core :refer :all]))

(defn- mock-get-width [_ font-size]
  font-size)

(describe "text utilities"
  (context "wrap"
    (it "wraps text to a given width"
      (should= [] (text/wrap "" 10))
      (should= ["x"] (text/wrap "x" 1))
      (should= ["x" "x"] (text/wrap "xx" 1))
      (should= ["x" "x" "x"] (text/wrap "xxx" 1))
      (should= ["xx" "x"] (text/wrap "xxx" 2))
      (should= ["x" "x"] (text/wrap "x x" 1))
      (should= ["x x"] (text/wrap "x x" 3))
      (should= ["x x" "x x"] (text/wrap "x x x x" 3))
      (should= ["x x" "x"] (text/wrap "x x x" 3))
      (should= ["xx" "xx"] (text/wrap "xx xx" 2))
      (should= ["xx" "xx"] (text/wrap "xx xx" 3))
      (should= ["xx" "xx"] (text/wrap "xx xx" 4))
      )
    )
  (context "compute-font-size"
    (it "computes font size for a given width"
      (doseq [size (range 1 1001)]
        (should= size (text/compute-font-size-for "AnyFont" size mock-get-width)))
      )
    (it "fails for font sizes greater than 1000"
      (should-not= 1001 (text/compute-font-size-for "AnyFont" 1001 mock-get-width)))
    )
  )


