(ns skillBoard.presenters.screen)

(defmulti make identity)

(defmethod make :default [_]
  :no-such-screen)
