(ns skillBoard.display16
  (:require [quil.core :as q]))
(def aspect-ratio 1.3)
(def margin-ratio 0.05)
(def segment-width-ratio 0.07)
(def segment-gap-ratio (* 0.3 segment-width-ratio))

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
        vbar-length (- segment-height (* 2 tip-height))]
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
     :vbar-length vbar-length})
  )

(defn translate-point [[x y] [dx dy]]
  [(+ x dx) (+ y dy)])

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

(defn build-backslash-segment [{:keys [margin segment-width segment-length segment-height segment-gap height width]}]
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


(defn draw-segment [seg]
  (q/begin-shape)
  (doseq [point-index [0 1 2 3 4 5 0]]
    (let [[x y] (nth seg point-index)]
      (q/vertex x y)))
  (q/end-shape :close))

(defn draw [{:keys [segment-gap segment-length segment-height segment-width height width margin] :as display}]
  (let [hseg (build-horizontal-segment display)
        vseg (build-vertical-segment display)
        backslash-seg (build-backslash-segment display)
        right-displacement (+ segment-length segment-gap)
        half-segment-width (* 0.5 segment-width)
        half-height (* 0.5 height)
        vertical-displacement (+ segment-height segment-width)]

    ;segement 0
    (draw-segment hseg)

    ;segment 1
    (q/with-translation
      [right-displacement 0]
      (draw-segment hseg))

    ;segment 2
    (draw-segment vseg)

    ;segment 3
    (draw-segment backslash-seg)

    ;segment 4
    (q/with-translation
      [(+ segment-length (* 0.5 segment-gap)) 0]
      (draw-segment vseg))

    ;segment 5

    ;segment 6
    (q/with-translation
      [(- width margin margin segment-width) 0]
      (draw-segment vseg))

    ;segment 7
    (q/with-translation
      [0 (- half-height margin half-segment-width)]
      (draw-segment hseg))

    ;segment 8
    (q/with-translation
      [right-displacement (- half-height margin half-segment-width)]
      (draw-segment hseg))

    ;segment 9
    (q/with-translation
      [0 vertical-displacement]
      (draw-segment vseg))

    ;segment 10

    ;segment 11
    (q/with-translation
       [(+ segment-length (* 0.5 segment-gap)) vertical-displacement]
       (draw-segment vseg))

    ;segment 12
    (q/with-translation
      [(- (* 0.5 width) half-segment-width margin) (- (* 0.5 height) half-segment-width margin)]
      (draw-segment backslash-seg))

    ;segment 13
    (q/with-translation
      [(- width margin margin segment-width) vertical-displacement]
      (draw-segment vseg))

    ;segment 14
    (q/with-translation
      [0 (- height segment-width margin margin)]
      (draw-segment hseg))

    ;segment 15
    (q/with-translation
      [right-displacement (- height segment-width margin margin)]
      (draw-segment hseg))
    )
  )




