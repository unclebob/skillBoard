(ns skillBoard.foundation.atoms-spec
  (:require
    [skillBoard.foundation.atoms :as atoms]
    [speclj.core :refer :all]))

(def startup
  {:poll-key @atoms/poll-key
   :clock-pulse @atoms/clock-pulse
   :test? @atoms/test?
   :change-screen? @atoms/change-screen?
   :screen-changed? @atoms/screen-changed?
   :log-traffic? @atoms/log-traffic?
   :log-stdout? @atoms/log-stdout?})

(describe "startup flags"
  (it "starts idle, with stdout logging on and the first screen pending"
    (should= false (:poll-key startup))
    (should= false (:clock-pulse startup))
    (should= false (:test? startup))
    (should= false (:change-screen? startup))
    (should= true (:screen-changed? startup))
    (should= false (:log-traffic? startup))
    (should= true (:log-stdout? startup))))
