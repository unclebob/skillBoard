(ns skillBoard.atoms)

(def poll-key (atom false))
(def poll-time (atom (System/currentTimeMillis)))
(def clock-pulse (atom false))
(def test? (atom false))
(def change-screen? (atom false))
(def screen-changed? (atom true))
(def log-traffic? (atom false))
(def log-stdout? (atom true))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T10:35:55.502304-05:00", :module-hash "-297529202", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-804622198"} {:id "def/poll-key", :kind "def", :line 3, :end-line 3, :hash "-583902707"} {:id "def/poll-time", :kind "def", :line 4, :end-line 4, :hash "314661084"} {:id "def/clock-pulse", :kind "def", :line 5, :end-line 5, :hash "-2026337349"} {:id "def/test?", :kind "def", :line 6, :end-line 6, :hash "-440055074"} {:id "def/change-screen?", :kind "def", :line 7, :end-line 7, :hash "608904526"} {:id "def/screen-changed?", :kind "def", :line 8, :end-line 8, :hash "-1875865199"} {:id "def/log-traffic?", :kind "def", :line 9, :end-line 9, :hash "-1539842420"} {:id "def/log-stdout?", :kind "def", :line 10, :end-line 10, :hash "-1835645389"}]}
;; clj-mutate-manifest-end
