(ns skillBoard.presenters.screen)

(defmulti make identity)

(defmulti header-text identity)

(defmethod header-text :default [_]
  "TILT")

(defmethod make :default [_]
  :no-such-screen)
