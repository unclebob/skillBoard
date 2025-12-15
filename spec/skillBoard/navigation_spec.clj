(ns skillBoard.navigation-spec
  (:require [speclj.core :refer :all]
            [skillBoard.navigation :as nav]))

(describe "Navigation utilities"
  (it "finds distance and direction"
    (let [{:keys [distance bearing]} (nav/dist-and-bearing 42.42240 -87.86810
                                                           42.44133 -88.06391)]
      (should= 9 (Math/round ^double distance))
      (should= 278 (Math/round ^double bearing)))
    )
  (it "handles nil lat/lon gracefully"
    (should= {:distance nil :bearing nil} (nav/dist-and-bearing nil 1 1 1))
    )
  )
