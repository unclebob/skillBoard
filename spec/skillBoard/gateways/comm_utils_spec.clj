(ns skillBoard.gateways.comm-utils-spec
  (:require
    [clj-http.client :as http]
    [skillBoard.foundation.core-utils :as core-utils]
    [skillBoard.gateways.comm-utils :as comm]
    [speclj.core :refer :all]))

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
        (should= {:key "value"} (comm/get-json :url :args @save-atom @com-errors "test data"))
        (should-have-invoked :get
                             {:times 1
                              :with [:* :*]})
        (should-not-have-invoked :prn)
        (should= 0 @@com-errors)))

    (it "prints and counts API errors, returns saved data"
      (with-redefs [http/get (stub :get {:return {:status 500 :body "Server Error"}})
                    core-utils/log-event (stub :log-event)]
        (reset! @save-atom :none)
        (should= :none (comm/get-json :url :args @save-atom @com-errors "test data"))
        (should-have-invoked :get
                             {:times 1
                              :with [:* :*]})
        (should-have-invoked :log-event
                             {:times 1
                              :with [:error :communication-issue :*]})
        (should= 1 @@com-errors)))))

(describe "CSV"
  (it "parses quoted CSV fields"
    (should= ["METAR KUGN 211853Z, AUTO" "KUGN" "42.4255"]
             (comm/csv-fields "\"METAR KUGN 211853Z, AUTO\",KUGN,42.4255")))

  (it "preserves duplicate CSV headers"
    (should= [:sky_cover :cloud_base_ft_agl :sky_cover_2 :cloud_base_ft_agl_2]
             (comm/unique-csv-headers ["sky_cover" "cloud_base_ft_agl"
                                       "sky_cover" "cloud_base_ft_agl"]))))
