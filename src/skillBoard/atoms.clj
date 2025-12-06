(ns skillBoard.atoms)

(def poll-key (atom false))
(def poll-time (atom (System/currentTimeMillis)))
(def screen-key (atom false))
(def clock-pulse (atom false))