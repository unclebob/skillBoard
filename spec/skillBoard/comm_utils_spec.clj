(ns skillBoard.comm-utils-spec
  (:require
    [clj-http.client :as http]
    [java-time.api :as time]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.core-utils :as core-utils]
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
        (should= {:key "value"} (comm/get-json :url :args @save-atom @com-errors "test data"))
        (should-have-invoked :get
                             {:times 1
                              :with [:* :*]})
        (should-not-have-invoked :prn)
        (should= 0 @@com-errors)
        ))

    (it "prints and counts API errors, returns saved data"
      (with-redefs [http/get (stub :get {:return {:status 500 :body "Server Error"}})
                    core-utils/log (stub :log)]
        (reset! @save-atom :none)
        (should= :none (comm/get-json :url :args @save-atom @com-errors "test data"))
        (should-have-invoked :get
                             {:times 1
                              :with [:* :*]})
        (should-have-invoked :log
                             {:times 1})
        (should= 1 @@com-errors)
        ))
    )
  )

(def frozen-today (time/local-date 2025 12 03))

(describe "get-reservations"
  (with-stubs)

  (it "calls comm/get-json with correct arguments including save-atom and error handler"
    (let [captured-url (atom nil)
          captured-args (atom nil)
          captured-save-atom (atom nil)
          captured-error-handler (atom nil)
          captured-source (atom nil)]
      (with-redefs [time/local-date (constantly frozen-today)
                    config/config (atom {:fsp-operator-id "OP123"
                                         :fsp-key "fake-key"})
                    comm/get-json (fn [url args save-atom error-handler source]
                                    (reset! captured-url url)
                                    (reset! captured-args args)
                                    (reset! captured-save-atom save-atom)
                                    (reset! captured-error-handler error-handler)
                                    (reset! captured-source source)
                                    {:data :mocked})]
        (let [result (comm/get-reservations)]
          (should= {:data :mocked} result)
          (should-contain "?startTime=gte:2025-12-02" @captured-url)
          (should-contain "&endTime=lt:2025-12-04" @captured-url)
          (should-contain "/operators/OP123/reservations?" @captured-url)
          (should-contain "&limit=200" @captured-url)
          (should= comm/polled-reservations @captured-save-atom)
          (should= comm/reservation-com-errors @captured-error-handler)
          (should= "reservations" @captured-source)
          (should= {:headers {"x-subscription-key" "fake-key"},
                    :socket-timeout 2000,
                    :connection-timeout 2000}
                   @captured-args)))))
  )

(describe "get-flights"
  (with-stubs)
  (it "calls comm/get-json with correct arguments including save-atom and error handler"
    (let [captured-url (atom nil)
          captured-args (atom nil)
          captured-save-atom (atom nil)
          captured-error-handler (atom nil)
          captured-source (atom nil)]
      (with-redefs [time/local-date (constantly frozen-today)
                    config/config (atom {:fsp-operator-id "OP123"
                                         :fsp-key "fake-key"})
                    comm/get-json (fn [url args save-atom error-handler source]
                                    (reset! captured-url url)
                                    (reset! captured-args args)
                                    (reset! captured-save-atom save-atom)
                                    (reset! captured-error-handler error-handler)
                                    (reset! captured-source source)
                                    {:data :mocked})]
        (let [result (comm/get-flights)]
          (should= {:data :mocked} result)
          (should-contain "/operators/OP123/flights?" @captured-url)
          (should-contain "&limit=200" @captured-url)
          (should-contain "flightDate=gte:2025-12-03" @captured-url)
          (should-contain "flightDateRangeEndDate=lt:2025-12-04" @captured-url)
          (should-contain "limit=200" @captured-url)
          (should= comm/polled-flights @captured-save-atom)
          (should= comm/reservation-com-errors @captured-error-handler)
          (should= "flights" @captured-source)
          (should= {:headers {"x-subscription-key" "fake-key"},
                    :socket-timeout 2000,
                    :connection-timeout 2000}
                   @captured-args)))))
  )

(describe "get-aircraft"
  (with-stubs)
  (it "calls comm/get-json with correct arguments including save-atom and error handler"
    (let [captured-url (atom nil)
          captured-args (atom nil)
          captured-error-handler (atom nil)
          captured-source (atom nil)]
      (with-redefs [time/local-date (constantly frozen-today)
                    config/config (atom {:fsp-operator-id "OP123"
                                         :fsp-key "fake-key"})
                    comm/get-json (fn [url args _save-atom error-handler source]
                                    (reset! captured-url url)
                                    (reset! captured-args args)
                                    (reset! captured-error-handler error-handler)
                                    (reset! captured-source source)
                                    {:items [{:status {:name "Active"}
                                              :tailNumber "tail1"}]})]
        (let [result (comm/get-aircraft)]
          (should= ["tail1"] result)
          (should= ["tail1"] @comm/polled-aircraft)
          (should-contain "/operators/OP123/aircraft" @captured-url)
          (should= comm/reservation-com-errors @captured-error-handler)
          (should= "aircraft" @captured-source)
          (should= {:headers {"x-subscription-key" "fake-key"},
                    :socket-timeout 2000,
                    :connection-timeout 2000}
                   @captured-args)))))
  )
