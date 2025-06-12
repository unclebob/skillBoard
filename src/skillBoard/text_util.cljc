(ns skillBoard.text-util
  (:require
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


