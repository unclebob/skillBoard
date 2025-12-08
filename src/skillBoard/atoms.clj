(ns skillBoard.atoms)

(def poll-key (atom false))
(def poll-time (atom (System/currentTimeMillis)))
(def clock-pulse (atom false))
(def test? (atom false))