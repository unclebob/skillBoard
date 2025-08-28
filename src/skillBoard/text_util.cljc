(ns skillBoard.text-util
  (:require
    [quil.core :as q]
    [clojure.string :as string]

    ))

(defn wrap [s w]
  (loop [s s
         wrapped []]
    (if (empty? s)
      wrapped
      (let [slen (count s)
            head-size (min w slen)
            break-pos (.lastIndexOf s " " w)
            head-size (if (or (<= slen w)
                              (neg? break-pos)) head-size break-pos)
            head (string/trim (subs s 0 head-size))
            tail (string/trim (subs s (count head)))]
        (recur tail (conj wrapped head))))))


(defn compute-font-size
  "Compute the font size such that the width of character 'X' is approximately the desired-character-width.
  works best for monospaced fonts."
  [font-name desired-character-width]
  (let [max-size 1000
        min-size 1
        tolerance 0.5
        max-iterations 20
        font (q/create-font font-name 64)]
    (loop [low min-size
           high max-size
           iteration 0]
      (let [mid (int (/ (+ low high) 2))
            _ (q/text-font font mid)
            text-width (q/text-width "X")]
        (cond
          (or (>= iteration max-iterations) (= low high))
          mid

          (<= (Math/abs (- text-width desired-character-width)) tolerance)
          mid

          (> text-width desired-character-width)
          (recur low (dec mid) (inc iteration))

          (< text-width desired-character-width)
          (recur (inc mid) high (inc iteration)))))))