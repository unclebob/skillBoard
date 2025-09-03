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


(defn get-width [font-name font-size]
  (q/text-font (q/create-font font-name font-size) font-size)
  (q/text-width "X"))

(defn compute-font-size-for
  "Find the font size such that the target dimension of character 'X' meets the demand.
  works best for monospaced fonts."
  [font-name demand target-fn]
  (let [max-size 1000
        min-size 1
        tolerance 0.5
        max-iterations 10]
    (loop [low min-size
           high max-size
           iteration 0]
      (let [mid (int (/ (+ low high) 2))
            dimension (target-fn font-name mid)]
        (cond
          (or (>= iteration max-iterations) (= low high))
          mid

          (<= (Math/abs (- dimension demand)) tolerance)
          mid

          (> dimension demand)
          (recur low (dec mid) (inc iteration))

          (< dimension demand)
          (recur (inc mid) high (inc iteration)))))))

(defn compute-font-size-for-width [font-name desired-width]
  (compute-font-size-for font-name desired-width get-width))