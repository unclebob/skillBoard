(ns skillBoard.api-utils-spec
  (:require
    [clj-http.client :as http]
    [skillBoard.api-utils :as api]
    [speclj.core :refer :all]
    ))


(declare save-atom com-errors)
(describe "API Utils"
  (context "get-json"
    (with save-atom (atom nil))
    (with com-errors (atom 0))
    (with-stubs)
    (it "accesses the API, converts to JSON, and saves"
      (with-redefs [http/get (stub :get {:return {:status 200 :body "{\"key\": \"value\"}"}})
                    prn (stub :prn)]
        (reset! @save-atom :none)
        (should= {:key "value"} (api/get-json :url :args @save-atom @com-errors "test data"))
        (should-have-invoked :get
                             {:times 1
                              :with [:* :*]})
        (should-not-have-invoked :prn)
        (should= 0 @@com-errors)
        ))

    (it "prints and counts API errors, returns saved data"
      (with-redefs [http/get (stub :get {:return {:status 500 :body "Server Error"}})
                    prn (stub :prn)]
        (reset! @save-atom :none)
        (should= :none (api/get-json :url :args @save-atom @com-errors "test data"))
        (should-have-invoked :get
                             {:times 1
                              :with [:* :*]})
        (should-have-invoked :prn
                             {:times 1})
        (should= 1 @@com-errors)
        ))
    )
  )