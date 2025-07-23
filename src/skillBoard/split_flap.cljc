(ns skillBoard.split-flap
  (:require
    [java-time.api :as time]
    [quil.core :as q]
    [skillBoard.config :as config]
    [skillBoard.time-util :as time-util]
    ))

(def next-char
  {\A \B
   \B \C
   \C \D
   \D \E
   \E \F
   \F \G
   \G \H
   \H \I
   \I \J
   \J \K
   \K \L
   \L \M
   \M \N
   \N \O
   \O \P
   \P \Q
   \Q \R
   \R \S
   \S \T
   \T \U
   \U \V
   \V \W
   \W \X
   \X \Y
   \Y \Z
   \Z \0
   \0 \1
   \1 \2
   \2 \3
   \3 \4
   \4 \5
   \5 \6
   \6 \7
   \7 \8
   \8 \9
   \9 \.
   \. \space
   \space \/
   \/ \+
   \+ \-
   \- \:
   \: \A
   })

(def gaps #{6 13 19 25 32 36 42 46 50})

(defn get-next-char [c]
  (get next-char c \space))

(defn draw-split-flap [{:keys [sf-font lines flappers font-width font-height flights] :as state}]
  (let [now (time-util/get-HHmm (time-util/local-to-utc (time/local-date-time)))
        now (str now "Z")
        flap-width (+ font-width (:sf-char-gap @config/display-info))
        flap-height (* font-height (inc config/sf-line-gap))
        top-margin (:top-margin @config/display-info)
        label-margin (+ top-margin (:label-height @config/display-info))
        draw-char (fn [c x y]
                    (when (or (> y (dec flights))
                              (nil? (gaps x)))
                      (let [cx (* x flap-width)
                            cy (+ (* y flap-height) label-margin)]
                        (q/fill 255 255 255)
                        (q/no-stroke)
                        (q/rect (+ cx (* font-width 0.2))
                                (+ cy (* font-height 0.2))
                                (* font-width 0.6)
                                (* font-height 0.6))

                        (q/fill 0 0 0)
                        (q/text-font sf-font)
                        (q/text-align :left :top)
                        (q/text (str c) cx cy))))
        draw-line (fn [line y]
                    (loop [x 0
                           cs line]
                      (if (empty? cs)
                        nil
                        (let [c (first cs)]
                          (draw-char c x y)
                          (recur (inc x) (rest cs))))))
        draw-lines (fn []
                     (loop [lines lines
                            y 0]
                       (if (empty? lines)
                         nil
                         (let [line (first lines)]
                           (draw-line line y)
                           (recur (rest lines) (inc y))))))
        draw-flappers (fn [] (doseq [{:keys [at from]} flappers]
                               (let [[col row] at]
                                 (draw-char from col row))))
        display-column-headers (fn []
                                 (let [label-height (:label-height @config/display-info)
                                       label-font-size (/ label-height config/font-height-per-point)
                                       baseline (- (+ top-margin label-height) (/ label-height 2))
                                       ]
                                   (q/text-font (:header-font state))
                                   (q/text-size label-font-size)
                                   (q/text-align :left :center)
                                   (q/fill 255 255 255)
                                   (q/text "TIME" 0 baseline)
                                   (q/text "AIRCRAFT" (* flap-width 7) baseline)
                                   (q/text "PILOT" (* flap-width 14) baseline)
                                   (q/text "CFI" (* flap-width 20) baseline)
                                   (q/text "CHECKOUT" (* flap-width 26) baseline)
                                   (q/text "ALT" (* flap-width 33) baseline)
                                   (q/text "DISTANCE" (* flap-width 37) baseline)
                                   (q/text "DIR" (* flap-width 43) baseline)
                                   (q/text "SPEED" (* flap-width 47) baseline)
                                   (q/text "REMARKS" (* flap-width 51) baseline)
                                   )
                                 )
        draw-header (fn []
                      (q/image (:departure-icon state) 0 0 top-margin top-margin)
                      (q/fill 255)
                      (q/text-font (:header-font state))
                      (q/text-size (* (:top-margin @config/display-info) 0.7))
                      (q/text-align :left :center)
                      (q/text "Skill Aviation Flights" (+ top-margin 10) (/ top-margin 2))
                      (q/text-align :center :top)
                      (q/text-size 12)
                      (q/text config/version (/ (q/width) 2) 5)
                      (display-column-headers)
                      (q/text-font (:sf-font state))
                      (q/text-size (* (:top-margin @config/display-info) 0.3))
                      (q/text-align :left :top)
                      (q/text now (- (q/width) (q/text-width now) 50) 10)
                      )]
    (q/background 50)
    (draw-header)
    (draw-lines)
    (draw-flappers)
    ))


(defn add-remaining-flappers [flappers remainder col row type]
  (if (empty? remainder)
    flappers
    (recur (conj flappers {:at [col row]
                           (if (= type :old) :to :from) \space
                           (if (= type :old) :from :to) (first remainder)})
           (rest remainder)
           (inc col) row type))
  )

(defn make-flappers-for-line [new-line old-line row flappers]
  (loop [new-line new-line
         old-line old-line
         col 0
         flappers flappers]
    (cond
      (empty? new-line) (add-remaining-flappers flappers old-line col row :old)
      (empty? old-line) (add-remaining-flappers flappers new-line col row :new)

      :else
      (let [char-new (first new-line)
            char-old (first old-line)]
        (if (= char-new char-old)
          (recur (rest new-line) (rest old-line) (inc col) flappers)
          (recur (rest new-line) (rest old-line) (inc col)
                 (conj flappers {:at [col row] :from char-old :to char-new})))))))

(defn make-flappers [new-report old-report]
  (loop [new-report new-report
         old-report old-report
         row 0
         flappers []]
    (cond
      (and (empty? new-report) (empty? old-report)) flappers
      :else
      (recur (rest new-report)
             (rest old-report)
             (inc row)
             (make-flappers-for-line (first new-report)
                                     (first old-report)
                                     row
                                     flappers)))))

(defn update-flappers [flappers]
  (loop [flappers flappers
         updated-flappers []]
    (if (empty? flappers)
      updated-flappers
      (let [{:keys [at from to] :as flapper} (first flappers)]
        (if (= from to)
          (recur (rest flappers) updated-flappers)
          (if (< (rand) 0.8)
            (recur (rest flappers)
                   (conj updated-flappers
                         {:at at
                          :from (get-next-char from)
                          :to to}))
            (recur (rest flappers) (conj updated-flappers flapper))))))))

