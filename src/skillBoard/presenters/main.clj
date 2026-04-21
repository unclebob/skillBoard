(ns skillBoard.presenters.main
  (:require
    [skillBoard.atoms :as atoms]
    [skillBoard.config :as config]
    [skillBoard.presenters.screen :as screen]
    [skillBoard.presenters.utils :as utils]))

(def screen-type (atom (:screen (first @config/screens))))
(def screen-duration (atom 0))
(def screen-start-time (atom 0))

(defn make-screen []
  (let [time (utils/get-now)
        current-screen-seconds (quot (- time @screen-start-time) 1000)]
    (when (or @atoms/change-screen? (> current-screen-seconds @screen-duration))
      (reset! screen-type (:screen (first @config/screens)))
      (reset! screen-duration (:duration (first @config/screens)))
      (reset! screen-start-time time)
      (reset! atoms/change-screen? false)
      (reset! atoms/screen-changed? true)
      (swap! config/screens rest))
    (screen/make @screen-type)
    ))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T10:30:34.950567-05:00", :module-hash "-1519150504", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "-1133709307"} {:id "def/screen-type", :kind "def", :line 8, :end-line 8, :hash "713557113"} {:id "def/screen-duration", :kind "def", :line 9, :end-line 9, :hash "-525159426"} {:id "def/screen-start-time", :kind "def", :line 10, :end-line 10, :hash "1173594620"} {:id "defn/make-screen", :kind "defn", :line 12, :end-line 23, :hash "1288633347"}]}
;; clj-mutate-manifest-end
