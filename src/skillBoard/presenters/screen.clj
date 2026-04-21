(ns skillBoard.presenters.screen
  (:require [quil.core :as q]
            [skillBoard.config :as config]))

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

(defmulti make identity)
(defmulti header-text identity)
(defmulti display-column-headers (fn [screen-type & _args] screen-type))
(defmulti draw-body (fn [screen-type & _args] screen-type))

(defmethod header-text :default [_]
  "TILT")

(defmethod make :default [_]
  :no-such-screen)

(defmethod display-column-headers :default [_ _flap-width _header-font _label-font-size]
  nil)

(defmethod draw-body :default [_ _state]
  false)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T10:33:12.541152-05:00", :module-hash "-508482524", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "1953837694"} {:id "defn/setup-headers", :kind "defn", :line 5, :end-line 14, :hash "-356704663"} {:id "defmulti/make", :kind "defmulti", :line 16, :end-line 16, :hash "645886603"} {:id "defmulti/header-text", :kind "defmulti", :line 17, :end-line 17, :hash "34793097"} {:id "defmulti/display-column-headers", :kind "defmulti", :line 18, :end-line 18, :hash "82062993"} {:id "defmethod/header-text/:default", :kind "defmethod", :line 20, :end-line 21, :hash "-579764816"} {:id "defmethod/make/:default", :kind "defmethod", :line 23, :end-line 24, :hash "1059796038"} {:id "defmethod/display-column-headers/:default", :kind "defmethod", :line 26, :end-line 27, :hash "-394556077"}]}
;; clj-mutate-manifest-end
