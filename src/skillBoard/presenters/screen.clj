(ns skillBoard.presenters.screen
  (:require [quil.core :as q]
            [skillBoard.foundation.config :as config]))

(defn setup-headers [header-font label-font-size]
  (let [label-height (* 0.8 (:label-height @config/display-info))
        baseline (- (+ (:top-margin @config/display-info) label-height) (/ label-height 2))
        ]
    (q/text-font header-font)
    (q/text-size label-font-size)
    (q/text-align :left :center)
    (q/fill 255 255 255)
    baseline
    ))

(defn draw-column-headers [flap-width header-font label-font-size headers]
  (let [baseline (setup-headers header-font label-font-size)]
    (doseq [[label column] headers]
      (q/text label (* flap-width column) baseline))))

(defmulti make (fn [screen-type & _] screen-type))
(defmulti header-text identity)
(defmulti display-column-headers (fn [screen-type & _args] screen-type))
(defmulti draw-body (fn [screen-type & _args] screen-type))

(defmethod header-text :default [_]
  "TILT")

(defmethod make :default [_ & _]
  :no-such-screen)

(defmethod display-column-headers :default [_ _flap-width _header-font _label-font-size]
  nil)

(defmethod draw-body :default [_ _state]
  false)
