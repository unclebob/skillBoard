(ns skillBoard.split-flap
  (:require
    [clojure.string :as string]
    [java-time.api :as time]
    [quil.core :as q]
    [skillBoard.atoms :as atoms]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.presenters.main :as presenter]
    [skillBoard.presenters.screen :as screen]
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
  (loop [new-line (:line new-line)
         old-line (:line old-line)
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
  (let [advance-toward
        (fn [from to]
          (loop [c from
                 steps config/flap-steps-per-update]
            (if (or (= c to) (zero? steps))
              c
              (recur (get-next-char c) (dec steps)))))]
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
                          :from (advance-toward from to)
                          :to to}))
            (recur (rest flappers) (conj updated-flappers flapper)))))))))

(defn header-text []
  (screen/header-text @presenter/screen-type))

(defn pad-and-trim-line [line length]
  (let [padded-line (str line (apply str (repeat length " ")))]
    (subs padded-line 0 length)))

(defn set-color [color]
  (condp = color
    :white (q/fill 255 255 255)
    :red (q/fill 255 125 125)
    :green (q/fill 175 255 175)
    :blue (q/fill 175 175 255)
    :magenta (q/fill 255 200 255)
    :cyan (q/fill 175 255 255)
    :yellow (q/fill 255 255 175)
    (q/fill 175 175 175)))

(def lines-layer-cache (atom {:key nil :layer nil}))
(def flapper-glyph-cache (atom {:key nil :glyphs nil}))

(defn- set-layer-color [layer color]
  (condp = color
    :white (.fill layer 255 255 255)
    :red (.fill layer 255 125 125)
    :green (.fill layer 175 255 175)
    :blue (.fill layer 175 175 255)
    :magenta (.fill layer 255 200 255)
    :cyan (.fill layer 175 255 255)
    :yellow (.fill layer 255 255 175)
    (.fill layer 175 175 175)))

(defn- render-lines-layer! [layer lines sf-font sf-font-size flap-width flap-height label-margin
                            backing-rect-top-left-x backing-rect-top-left-y
                            backing-rect-width backing-rect-height]
  (.beginDraw layer)
  (.clear layer)
  (.noStroke layer)
  (.textFont layer sf-font)
  (.textSize layer sf-font-size)
  (.textAlign layer processing.core.PConstants/LEFT processing.core.PConstants/TOP)
  (loop [remaining-lines lines
         y 0
         line-char-count 0
         line-rect-count 0
         line-text-count 0]
    (if (empty? remaining-lines)
      (do
        (.endDraw layer)
        {:line-char-count line-char-count
         :line-rect-count line-rect-count
         :line-text-count line-text-count})
      (let [{:keys [line color]} (first remaining-lines)
            cy (+ (* y flap-height) label-margin)]
        (if (string/blank? line)
          (recur (rest remaining-lines) (inc y) line-char-count line-rect-count line-text-count)
          (let [[new-char-count new-rect-count new-text-count]
                (loop [x 0
                       cs (pad-and-trim-line line config/cols)
                       ccount line-char-count
                       rcount line-rect-count
                       tcount line-text-count]
                  (if (empty? cs)
                    [ccount rcount tcount]
                    (let [c (first cs)]
                      (if (= c \space)
                        (recur (+ x flap-width) (rest cs) ccount rcount tcount)
                        (do
                          (set-layer-color layer color)
                          (.rect layer
                                 (float (+ x backing-rect-top-left-x))
                                 (float (+ cy backing-rect-top-left-y))
                                 (float backing-rect-width)
                                 (float backing-rect-height))
                          (.fill layer 0 0 0)
                          (.text layer (str c) (float x) (float cy))
                          (recur (+ x flap-width) (rest cs) (inc ccount) (inc rcount) (inc tcount)))))))]
            (recur (rest remaining-lines) (inc y) new-char-count new-rect-count new-text-count)))))))

(defn- flapper-char-set []
  (->> (concat (keys next-char) (vals next-char))
       distinct
       (remove #(= % \space))
       vec))

(defn- set-layer-tile-color [layer color]
  (condp = color
    :white (.fill layer 255 255 255)
    :red (.fill layer 255 125 125)
    :green (.fill layer 175 255 175)
    :blue (.fill layer 175 175 255)
    :magenta (.fill layer 255 200 255)
    :cyan (.fill layer 175 255 255)
    :yellow (.fill layer 255 255 175)
    (.fill layer 175 175 175)))

(defn- render-flapper-glyphs! [sf-font sf-font-size flap-width flap-height
                               backing-rect-top-left-x backing-rect-top-left-y
                               backing-rect-width backing-rect-height colors]
  (let [gw (max 1 (int (Math/ceil flap-width)))
        gh (max 1 (int (Math/ceil flap-height)))]
    (reduce (fn [glyphs color]
              (reduce (fn [glyphs c]
                        (let [glyph (q/create-graphics gw gh)]
                          (.beginDraw glyph)
                          (.noSmooth glyph)
                          (.clear glyph)
                          (.textFont glyph sf-font)
                          (.textSize glyph sf-font-size)
                          (.textAlign glyph processing.core.PConstants/LEFT processing.core.PConstants/TOP)
                          (set-layer-tile-color glyph color)
                          (.rect glyph
                                 (float backing-rect-top-left-x)
                                 (float backing-rect-top-left-y)
                                 (float backing-rect-width)
                                 (float backing-rect-height))
                          (.fill glyph 0 0 0)
                          (.text glyph (str c) (float 0) (float 0))
                          (.endDraw glyph)
                          (assoc glyphs [color c] glyph)))
                      glyphs
                      (flapper-char-set)))
            {}
            colors)))

(defn draw [{:keys [sf-font sf-font-size clock-font clock-font-size lines flappers
                    font-width font-height header-font header-font-size label-font-size] :as state}]
  (let [profile? @atoms/test?
        line-colors (mapv :color lines)
        now (time-util/get-HHmm (time-util/local-to-utc (time/local-date-time)))
        now (if @atoms/clock-pulse now (string/replace now ":" " "))
        flap-width (+ font-width (:sf-char-gap @config/display-info))
        flap-height (* font-height (inc config/sf-line-gap))
        top-margin (:top-margin @config/display-info)
        label-margin (+ top-margin (:label-height @config/display-info))
        backing-rect-top-left-x (int (* font-width 0.1))
        backing-rect-top-left-y (int (* font-height 0.1))
        backing-rect-width ^int (int (* font-width 0.8))
        backing-rect-height ^int (int (* font-height 0.8))

        draw-lines
        (fn []
          (let [{:keys [key layer]} @lines-layer-cache
                width (q/width)
                height (q/height)
                size-match? (and (some? layer)
                                 (= (.-width layer) width)
                                 (= (.-height layer) height))
                layer (if size-match? layer (q/create-graphics width height))
                layer-key [lines
                           flap-width
                           flap-height
                           label-margin
                           backing-rect-top-left-x
                           backing-rect-top-left-y
                           backing-rect-width
                           backing-rect-height
                           sf-font-size]
                rerender? (or (not size-match?) (not= key layer-key))]
            (when rerender?
              (let [t0 (System/nanoTime)
                    {:keys [line-char-count line-rect-count line-text-count]}
                    (render-lines-layer! layer lines sf-font sf-font-size flap-width flap-height label-margin
                                         backing-rect-top-left-x backing-rect-top-left-y
                                         backing-rect-width backing-rect-height)
                    rerender-ms (/ (- (System/nanoTime) t0) 1e6)]
                (when profile?
                  (swap! atoms/render-lines-rerender-count inc)
                  (swap! atoms/render-lines-rerender-time-accumulator + rerender-ms)
                  (swap! atoms/render-lines-char-count-accumulator + line-char-count)
                  (swap! atoms/render-lines-rect-count-accumulator + line-rect-count)
                  (swap! atoms/render-lines-text-count-accumulator + line-text-count))))
            (reset! lines-layer-cache {:key layer-key :layer layer})
            (q/image layer 0 0)))

        draw-flappers
        (fn []
          (let [{:keys [key glyphs]} @flapper-glyph-cache
                colors (->> line-colors distinct vec)
                glyph-key [sf-font sf-font-size flap-width flap-height
                           backing-rect-top-left-x backing-rect-top-left-y
                           backing-rect-width backing-rect-height colors]
                glyphs (if (= key glyph-key)
                         glyphs
                         (render-flapper-glyphs! sf-font sf-font-size flap-width flap-height
                                                 backing-rect-top-left-x backing-rect-top-left-y
                                                 backing-rect-width backing-rect-height colors))]
            (when (not= key glyph-key)
              (reset! flapper-glyph-cache {:key glyph-key :glyphs glyphs}))
            (doseq [{:keys [at from]} flappers]
              (when (not= from \space)
                (let [[col row] at
                      x (int (Math/round (double (* col flap-width))))
                      y (int (Math/round (double (+ (* row flap-height) label-margin))))
                      color (nth line-colors row nil)
                      glyph (get glyphs [color from])]
                  (if (some? glyph)
                    (q/image glyph x y)
                    (do
                      (set-color color)
                      (q/rect (+ x backing-rect-top-left-x)
                              (+ y backing-rect-top-left-y)
                              backing-rect-width
                              backing-rect-height)
                      (q/text-font sf-font)
                      (q/text-size sf-font-size)
                      (q/text-align :left :top)
                      (q/fill 0 0 0)
                      (q/text (str from) x y))))))))

        display-com-errors
        (fn [pos]
          (cond
            (> @comm/reservation-com-errors 3) (q/fill 255 0 0)
            (> @comm/reservation-com-errors 1) (q/fill 255 165 0)
            :else (q/fill 0 255 0))
          (q/ellipse pos 20 10 10)

          (cond
            (> @comm/adsb-com-errors 3) (q/fill 255 0 0)
            (> @comm/adsb-com-errors 1) (q/fill 255 165 0)
            :else (q/fill 0 255 0))
          (q/ellipse pos 35 10 10)

          (cond
            (> @comm/weather-com-errors 3) (q/fill 255 0 0)
            (> @comm/weather-com-errors 1) (q/fill 255 165 0)
            :else (q/fill 0 255 0))
          (q/ellipse pos 50 10 10)
          )

        display-time
        (fn []
          (q/text-font clock-font)
          (let [
                _ (q/text-size clock-font-size)
                time-pos (- (q/width) (q/text-width now) 50)
                ]
            (display-com-errors (- time-pos 10))
            (q/text-align :left :top)
            (q/fill 255 255 255)
            (q/text now (- (q/width) (q/text-width now) 50) 10)
            ))

        draw-header
        (fn []
          (q/image (:departure-icon state) 0 0 top-margin top-margin)
          (q/fill 255)
          (q/text-font header-font)
          (q/text-size header-font-size)
          (q/text-align :left :center)
          (q/text (header-text) (+ top-margin 10) (/ top-margin 2))
          (q/text-align :center :top)
          (q/text-size 12)
          (q/text config/version (/ (q/width) 2) 5)
          (screen/display-column-headers @presenter/screen-type flap-width header-font label-font-size)
          (display-time))]

    (let [t0 (System/nanoTime)]
      (q/background 30)
      (when profile?
        (swap! atoms/render-background-time-accumulator + (/ (- (System/nanoTime) t0) 1e6))))

    (let [t0 (System/nanoTime)]
      (draw-header)
      (when profile?
        (swap! atoms/render-header-time-accumulator + (/ (- (System/nanoTime) t0) 1e6))))

    (let [t0 (System/nanoTime)]
      (draw-lines)
      (when profile?
        (swap! atoms/render-lines-time-accumulator + (/ (- (System/nanoTime) t0) 1e6))))

    (let [t0 (System/nanoTime)]
      (draw-flappers)
      (when profile?
        (swap! atoms/render-flappers-time-accumulator + (/ (- (System/nanoTime) t0) 1e6))
        (swap! atoms/render-flapper-text-count-accumulator + (count flappers))))
    ))

(defn blank-line []
  {:line (apply str (repeat config/cols " ")) :color nil})

(defn blank-screen []
  (repeat (:line-count @config/display-info) (blank-line)))

(defn do-update [{:keys [time flappers lines] :as state}]
  (let [now (System/currentTimeMillis)
        old-summary lines
        summary (if @atoms/screen-changed?
                  (blank-screen)
                  (presenter/make-screen))
        new-screen? (not= summary old-summary)
        new-screen-time (if new-screen? now time)
        [flappers mode elapsed-ms]
        (cond
          @atoms/screen-changed?
          (let [t0 (System/nanoTime)
                next-flappers (make-flappers summary (blank-screen))]
            [next-flappers :make (/ (- (System/nanoTime) t0) 1e6)])

          new-screen?
          (let [t0 (System/nanoTime)
                next-flappers (make-flappers summary old-summary)]
            [next-flappers :make (/ (- (System/nanoTime) t0) 1e6)])

          (> (- now new-screen-time) config/flap-duration)
          [[] :idle 0.0]

          :else
          (let [t0 (System/nanoTime)
                next-flappers (update-flappers flappers)]
            [next-flappers :update (/ (- (System/nanoTime) t0) 1e6)]))]
    (when @atoms/test?
      (condp = mode
        :make (do
                (swap! atoms/flapper-make-count inc)
                (swap! atoms/flapper-make-time-accumulator + elapsed-ms))
        :update (do
                  (swap! atoms/flapper-update-count inc)
                  (swap! atoms/flapper-update-time-accumulator + elapsed-ms))
        nil))
    (reset! atoms/screen-changed? false)
    (assoc state :time new-screen-time
                 :lines summary
                 :flappers flappers)))
