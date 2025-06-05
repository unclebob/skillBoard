(ns skillBoard.display16
  (:require [quil.core :as q]))
(def aspect-ratio 1.4)
(def margin-ratio 0.05)
(def segment-width-ratio 0.07)
(def segment-gap-ratio (* 0.3 segment-width-ratio))
(def between-chars-ratio 0.1)

(def segment-map
  {\0 1
   \1 2
   \2 4
   \3 8
   \4 16
   \5 32
   \6 64
   \7 128
   \8 256
   \9 512
   \A 1024
   \B 2048
   \C 4096
   \D 8192
   \E 16384
   \F 32768})

(defn char-desc-to-bits [desc]
  (let [bits (map segment-map desc)]
    (reduce + 0 bits)))

;  -0-- -1--
; |\   |   /|
; 2 3  4  5 6
; |  \ | /  |
; |   \|/   |
;  -7-- -8--
; |   /|\   |
; 9  A B C  D
; | /  |  \ |
; |/   |   \|
;  -E-- -F--


(def character-desc-map
  {\A "0126789D"
   \B "01468BDEF"
   \C "0129EF"
   \D "0146BDEF"
   \E "01279EF"
   \F "01279"
   \G "0129EFD8"
   \H "296D78"
   \I "014BEF"
   \J "6DFE9"
   \K "2975C"
   \L "29EF"
   \M "29356D"
   \N "293C6D"
   \O "016DEF29"
   \P "0126789"
   \Q "01269DCEF"
   \R "0126789C"
   \S "10278DEF"
   \T "014B"
   \U "29EF6D"
   \V "29A5"
   \W "29AC6D"
   \X "3CA5"
   \Y "35B"
   \Z "015AEF"
   \0 "016DEF295A"
   \1 "56D"
   \2 "016789EF"
   \3 "01687DEF"
   \4 "2678D"
   \5 "01278DFE"
   \6 "012978DEF"
   \7 "015B"
   \8 "0126789DEF"
   \9 "012678DEF"
   \. "E"
   \space ""
   \/ "A5"
   \+ "478B"
   \- "78"
   \: "7E"
   })

(defn build-context [width]
  (let [width (double width)
        height (* aspect-ratio width)
        box [width height]
        margin (* margin-ratio width)
        segment-gap (* segment-gap-ratio width)
        segment-width (* segment-width-ratio width)
        segment-length (- (* 0.5 width) margin (* 0.5 segment-gap) (* 0.5 segment-width))
        segment-height (- (* 0.5 height) margin (* 1.5 segment-width))
        tip-height (* 0.5 segment-width)
        hbar-length (- segment-length tip-height tip-height)
        vbar-length (- segment-height (* 2 tip-height))
        between-chars (* between-chars-ratio width)]
    {:box box
     :width width
     :height height
     :margin margin
     :segment-gap segment-gap
     :segment-width segment-width
     :segment-length segment-length
     :segment-height segment-height
     :tip-height tip-height
     :hbar-length hbar-length
     :vbar-length vbar-length
     :between-chars between-chars}))

(defn translate-point [[x y] [dx dy]]
  [(+ x dx) (+ y dy)])

(defn translate-segment [segment [dx dy]]
  (map #(translate-point % [dx dy]) segment))

(defn build-horizontal-segment [{:keys [margin segment-width tip-height hbar-length]}]
  (let [half-segment-width (* 0.5 segment-width)
        left-tip [(+ margin half-segment-width) (+ margin half-segment-width)]
        left-tip-top [(+ (first left-tip) tip-height) margin]
        right-tip-top [(+ (first left-tip-top) hbar-length) margin]
        right-tip [(+ (first right-tip-top) tip-height) (+ margin half-segment-width)]
        right-tip-bottom [(first right-tip-top) (+ margin segment-width)]
        left-tip-bottom [(first left-tip-top) (+ margin segment-width)]]
    [left-tip left-tip-top right-tip-top right-tip right-tip-bottom left-tip-bottom]))

(defn build-vertical-segment [{:keys [margin segment-width tip-height vbar-length]}]
  (let [half-segment-width (* 0.5 segment-width)
        top-tip [(+ margin half-segment-width) (+ margin segment-width)]
        top-tip-right (translate-point top-tip [half-segment-width tip-height])
        bottom-tip-right (translate-point top-tip-right [0 vbar-length])
        bottom-tip (translate-point bottom-tip-right [(- half-segment-width) tip-height])
        bottom-tip-left (translate-point bottom-tip [(- half-segment-width) (- tip-height)])
        top-tip-left (translate-point bottom-tip-left [0 (- vbar-length)])]
    [top-tip top-tip-right bottom-tip-right bottom-tip bottom-tip-left top-tip-left]))

(defn build-backslash-segment [{:keys [margin segment-width segment-gap height width]}]
  (let [half-segment-width (* 0.5 segment-width)
        tip-side (* 0.7 segment-width)
        top-tip [(+ margin segment-width segment-gap) (+ margin segment-width segment-gap)]
        top-right-base (translate-point top-tip [tip-side 0])
        bottom-right-base [(- (* 0.5 width) half-segment-width segment-gap)
                           (- (* 0.5 height) half-segment-width segment-gap tip-side)]
        bottom-tip (translate-point bottom-right-base [0 tip-side])
        bottom-left-base (translate-point bottom-tip [(- tip-side) 0])
        top-left-base (translate-point top-tip [0 tip-side])]
    [top-tip top-right-base bottom-right-base bottom-tip bottom-left-base top-left-base]))

(defn build-slash-segment [{:keys [margin segment-width segment-gap height width]}]
  (let [half-segment-width (* 0.5 segment-width)
        tip-side (* 0.7 segment-width)
        top-tip [(- width margin segment-width segment-gap) (+ margin segment-width segment-gap)]
        top-left-base (translate-point top-tip [(- tip-side) 0])
        bottom-left-base [(+ (* 0.5 width) half-segment-width segment-gap)
                          (- (* 0.5 height) half-segment-width segment-gap tip-side)]
        bottom-tip (translate-point bottom-left-base [0 tip-side])
        bottom-right-base (translate-point bottom-tip [tip-side 0])
        top-right-base (translate-point top-tip [0 tip-side])]
    [top-tip top-left-base bottom-left-base bottom-tip bottom-right-base top-right-base]))


(defn draw-segment [seg]
  (q/begin-shape)
  (doseq [point-index [0 1 2 3 4 5 0]]
    (let [[x y] (nth seg point-index)]
      (q/vertex x y)))
  (q/end-shape :close))

(defn draw-character [{:keys [segments context]} c]
  (let [bits (char-desc-to-bits (character-desc-map c))
        [width height] (:box context)]
    (q/no-stroke)
    (q/fill 30 30 30)
    (q/rect 0 0 width height)
    (q/fill 255 255 255)
    (q/no-stroke)
    (loop [segments segments
           bits bits]
      (if (empty? segments)
        nil
        (do (when (odd? bits)
              (draw-segment (first segments)))
            (recur (rest segments) (bit-shift-right bits 1)))))))

(defn build-character-display [width]
  (let [{:keys [segment-gap segment-length segment-height segment-width height width margin] :as context} (build-context width)
        hseg (build-horizontal-segment context)
        vseg (build-vertical-segment context)
        backslash-seg (build-backslash-segment context)
        slash-seg (build-slash-segment context)
        right-displacement (+ segment-length segment-gap)
        half-segment-width (* 0.5 segment-width)
        half-height (* 0.5 height)
        vertical-displacement (+ segment-height segment-width)
        s0 hseg
        s1 (translate-segment hseg [right-displacement 0])
        s2 vseg
        s3 backslash-seg
        s4 (translate-segment vseg [right-displacement 0])
        s5 slash-seg
        s6 (translate-segment vseg [(- width margin margin segment-width) 0])
        s7 (translate-segment hseg [0 (- half-height margin half-segment-width)])
        s8 (translate-segment hseg [right-displacement (- half-height margin half-segment-width)])
        s9 (translate-segment vseg [0 vertical-displacement])
        s10 (translate-segment slash-seg [(+ (* -0.5 width) half-segment-width margin) (- (* 0.5 height) half-segment-width margin)])
        s11 (translate-segment vseg [(+ segment-length (* 0.5 segment-gap)) vertical-displacement])
        s12 (translate-segment backslash-seg [(- (* 0.5 width) half-segment-width margin) (- (* 0.5 height) half-segment-width margin)])
        s13 (translate-segment vseg [(- width margin margin segment-width) vertical-displacement])
        s14 (translate-segment hseg [0 (- height segment-width margin margin)])
        s15 (translate-segment hseg [right-displacement (- height segment-width margin margin)])]
    {:context context
     :segments [s0 s1 s2 s3 s4 s5 s6 s7 s8 s9 s10 s11 s12 s13 s14 s15]}))

(defn draw-line [{:keys [context] :as display} line]
  (let [width (:width context)
        between-chars (:between-chars context)
        char-spacing (+ width between-chars)]
    ;for some strange reason, in ClojureScript
    ;using q/with-translation does not work
    ;inside this loop.  So I fell back on push-matrix/pop-matrix.
    (doseq [[x c] (map vector (range 0 10000 char-spacing) line)]
      (q/push-matrix)
      (q/translate x 0)
      (draw-character display c)
      (q/pop-matrix))))

