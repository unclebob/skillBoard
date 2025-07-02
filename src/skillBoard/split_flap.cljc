(ns skillBoard.split-flap
  (:require
    [quil.core :as q]
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

(defn draw-char [{:keys [sf-font font-width font-height]}
                 c cx cy]
  (q/fill 255 255 255)
  (q/no-stroke)
  (q/rect (+ cx (:left backing-rect))
          (+ cy (:top backing-rect))
          (- font-width (:right backing-rect))
          (- font-height (:bottom backing-rect)))
  (q/fill 0 0 0)
  (q/text-font sf-font)
  (q/text-align :left :top)
  (q/text (str c) cx cy))

(defn draw-split-flap [{:keys [lines font-width font-height] :as state}]
  (let [draw-line (fn [line y]
                    (loop [x 0
                           cs line]
                      (if (empty? cs)
                        nil
                        (let [c (first cs)
                              next-x (+ x font-width 6)]
                          (draw-char state c x y)
                          (recur next-x (rest cs))))))
        draw-lines (fn []
                     (q/background 50)
                     (loop [lines lines
                            y 0]
                       (if (empty? lines)
                         nil
                         (let [line (first lines)]
                           (draw-line line y)
                           (recur (rest lines) (+ y font-height 10))))))]
    (draw-lines)
    ))
