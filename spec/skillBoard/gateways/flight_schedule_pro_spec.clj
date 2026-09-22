(ns skillBoard.gateways.flight-schedule-pro-spec
  (:require
    [java-time.api :as time]
    [skillBoard.foundation.config :as config]
    [skillBoard.gateways.comm-utils :as comm]
    [skillBoard.gateways.flight-schedule-pro :as fsp]
    [speclj.core :refer :all]))

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
        (let [result (fsp/get-reservations)]
          (should= {:data :mocked} result)
          (should-contain "?startTime=gte:2025-12-02" @captured-url)
          (should-contain "&endTime=lt:2025-12-04" @captured-url)
          (should-contain "/operators/OP123/reservations?" @captured-url)
          (should-contain "&limit=200" @captured-url)
          (should= fsp/polled-reservations @captured-save-atom)
          (should= fsp/reservation-com-errors @captured-error-handler)
          (should= "reservations" @captured-source)
          (should= {:headers {"x-subscription-key" "fake-key"}
                    :socket-timeout 5000
                    :connection-timeout 5000}
                   @captured-args))))))

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
        (let [result (fsp/get-flights)]
          (should= {:data :mocked} result)
          (should-contain "/operators/OP123/flights?" @captured-url)
          (should-contain "&limit=200" @captured-url)
          (should-contain "flightDate=gte:2025-12-03" @captured-url)
          (should-contain "flightDateRangeEndDate=lt:2025-12-04" @captured-url)
          (should-contain "limit=200" @captured-url)
          (should= fsp/polled-flights @captured-save-atom)
          (should= fsp/reservation-com-errors @captured-error-handler)
          (should= "flights" @captured-source)
          (should= {:headers {"x-subscription-key" "fake-key"}
                    :socket-timeout 5000
                    :connection-timeout 5000}
                   @captured-args))))))

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
        (let [result (fsp/get-aircraft)]
          (should= ["tail1"] result)
          (should= ["tail1"] @fsp/polled-aircraft)
          (should-contain "/operators/OP123/aircraft" @captured-url)
          (should= fsp/reservation-com-errors @captured-error-handler)
          (should= "aircraft" @captured-source)
          (should= {:headers {"x-subscription-key" "fake-key"}
                    :socket-timeout 5000
                    :connection-timeout 5000}
                   @captured-args))))))
