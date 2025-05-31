(ns skillBoard.display16-spec
	(:require [speclj.core :refer :all]
						[skillBoard.display16 :as display]))

(describe "sixteen segement display"
  (context "utilities"
    (it "converts descriptors to bits"
      (should= 0 (display/char-desc-to-bits ""))
      (should= 1 (display/char-desc-to-bits "0"))
      (should= 2 (display/char-desc-to-bits "1"))
      (should= 3 (display/char-desc-to-bits "01"))
      (should= 4 (display/char-desc-to-bits "2"))
      (should= 5 (display/char-desc-to-bits "02"))
      (should= 7 (display/char-desc-to-bits "012"))
      (should= 15 (display/char-desc-to-bits "0123"))
      (should= 31 (display/char-desc-to-bits "01234"))
      (should= 65535 (display/char-desc-to-bits "0123456789ABCDEF"))
      )
    )
  )