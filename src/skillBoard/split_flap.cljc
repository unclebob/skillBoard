(ns skillBoard.split-flap
  (:require
    [java-time.api :as time]
    [quil.core :as q]
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

(defn get-next-char [c]
  (get next-char c \space))

(def backing-rect {:left 4
                   :right 4
                   :top 4
                   :bottom 8})

(def top-margin 100)

(defn draw-char [{:keys [sf-font font-width font-height]}
                 c cx cy]
  (q/fill 255 255 255)
  (q/no-stroke)
  (q/rect (+ cx (:left backing-rect))
          (+ cy (:top backing-rect) top-margin)
          (- font-width (:right backing-rect))
          (- font-height (:bottom backing-rect)))
  (q/fill 0 0 0)
  (q/text-font sf-font)
  (q/text-align :left :top)
  (q/text (str c) cx (+ cy top-margin)))

(defn draw-split-flap [{:keys [lines flappers font-width font-height] :as state}]
  (let [now (time-util/get-HHmm (time-util/local-to-utc (time/local-date-time)))
        now (str now "Z")
        flap-width (+ font-width 6)
        flap-height (+ font-height 10)
        draw-line (fn [line y]
                    (loop [x 0
                           cs line]
                      (if (empty? cs)
                        nil
                        (let [c (first cs)
                              next-x (+ x flap-width)]
                          (draw-char state c x y)
                          (recur next-x (rest cs))))))
        draw-lines (fn []
                     (loop [lines lines
                            y 0]
                       (if (empty? lines)
                         nil
                         (let [line (first lines)]
                           (draw-line line y)
                           (recur (rest lines) (+ y flap-height))))))
        draw-flappers (fn [] (doseq [{:keys [at from]} flappers]
                               (let [[col row] at]
                                 (draw-char state from (* flap-width col) (* flap-height row)))))]
    (q/background 50)
    (q/image (:departure-icon state) 0 0 top-margin top-margin)
    (q/fill 255)
    (q/text-align :left :top)
    (q/text-font (:header-font state))
    (q/text-size 50)
    (q/text "Skill Aviation Flights" (+ top-margin 10) 10)
    (q/text-size 25)
    (q/text (str "TIME"
                 "          "
                 "AIRCRAFT"
                 "               "
                 "---------- CREW ----------"
                 "            "
                 "CHECKED OUT"
                 "       "
                 "ALT"
                 "          "
                 "DISTANCE"
                 "        "
                 "DIR"
                 "           "
                 "SPEED")
            (+ top-margin 5) (- top-margin 25))
    (q/text-font (:sf-font state))
    (q/text-size 30)
    (q/text now (- (q/width) (q/text-width now) 50) 10)
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
      (let [{:keys [at from to]} (first flappers)]
        (if (= from to)
          (recur (rest flappers) updated-flappers)
          (recur (rest flappers)
                 (conj updated-flappers
                       {:at at
                        :from (get-next-char from)
                        :to to})))))))

