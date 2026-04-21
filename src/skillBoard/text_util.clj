(ns skillBoard.text-util
  (:require
    [clojure.string :as string]
    [quil.core :as q]

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


(defn- get-width [font font-size]
  (q/text-font font font-size)
  (q/text-width "X"))

(defn- get-height [font font-size]
  (q/text-font font font-size)
  (+ (q/text-ascent) (q/text-descent)))

(defn find-font-size-for
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

(defn find-font-size-for-width [font desired-width]
  (find-font-size-for font desired-width get-width))

(defn find-font-size-for-height [font desired-height]
  (find-font-size-for font desired-height get-height))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T10:28:20.295957-05:00", :module-hash "773971037", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "817663"} {:id "defn/wrap", :kind "defn", :line 8, :end-line 20, :hash "1695522556"} {:id "defn-/get-width", :kind "defn-", :line 23, :end-line 25, :hash "-1366678868"} {:id "defn-/get-height", :kind "defn-", :line 27, :end-line 29, :hash "943069941"} {:id "defn/find-font-size-for", :kind "defn", :line 31, :end-line 55, :hash "1325442964"} {:id "defn/find-font-size-for-width", :kind "defn", :line 57, :end-line 58, :hash "-2128981233"} {:id "defn/find-font-size-for-height", :kind "defn", :line 60, :end-line 61, :hash "469964023"}]}
;; clj-mutate-manifest-end
