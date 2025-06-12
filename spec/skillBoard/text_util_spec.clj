(ns skillBoard.text-util-spec
  (:require [skillBoard.text-util :as text]
            [speclj.core :refer :all]))

(describe "text utilities"
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

