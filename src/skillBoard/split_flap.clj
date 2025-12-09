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
    [skillBoard.text-util :as text]
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

(defn header-text []
  (screen/header-text @presenter/screen-type))

(defn pad-and-trim-line [line length]
  (let [padded-line (str line (apply str (repeat length " ")))]
    (subs padded-line 0 length)))

(defn draw [{:keys [sf-font sf-font-size clock-font lines flappers font-width font-height header-font] :as state}]
  (let [now (time-util/get-HHmm (time-util/local-to-utc (time/local-date-time)))
        now (if @atoms/clock-pulse now (string/replace now ":" " "))
        flap-width (+ font-width (:sf-char-gap @config/display-info))
        flap-height (* font-height (inc config/sf-line-gap))
        top-margin (:top-margin @config/display-info)
        label-margin (+ top-margin (:label-height @config/display-info))

        draw-char
        (fn [c x y color]
          (let [cx (* x flap-width)
                cy (+ (* y flap-height) label-margin)]
            (condp = color
              :white (q/fill 255 255 255)
              :red (q/fill 255 200 200)
              :green (q/fill 200 255 200)
              :blue (q/fill 200 200 255)
              (q/fill 200 200 200)
              )
            (q/no-stroke)
            (q/rect (+ cx (* font-width 0.1))
                    (+ cy (* font-height 0.1))
                    (* font-width 0.8)
                    (* font-height 0.8))

            (q/fill 0 0 0)
            (q/text-font sf-font)
            (q/text-size sf-font-size)
            (q/text-align :left :top)
            (q/text (str c) cx cy))
          )

        draw-line
        (fn [line color y]
          (loop [x 0
                 cs (pad-and-trim-line line config/cols)]
            (if (empty? cs)
              nil
              (let [c (first cs)]
                (draw-char c x y color)
                (recur (inc x) (rest cs))))))
        draw-lines
        (fn []
          (loop [lines lines
                 y 0]
            (if (empty? lines)
              nil
              (let [{:keys [line color]} (first lines)]
                (when (not (string/blank? line))
                  (draw-line line color y))
                (recur (rest lines) (inc y))))))
        draw-flappers
        (fn []
          (doseq [{:keys [at from]} flappers]
            (let [[col row] at]
              (draw-char from col row nil))))

        display-com-errors
        (fn [pos]
          (cond
            (> @comm/reservation-com-errors 6) (q/fill 255 0 0)
            (> @comm/reservation-com-errors 3) (q/fill 255 165 0)
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
                time-height (* 0.5 (:top-margin @config/display-info))
                time-font-size (text/find-font-size-for-height clock-font time-height)
                _ (q/text-size time-font-size)
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
          (q/text-size (text/find-font-size-for-height header-font (* 0.7 top-margin)))
          (q/text-align :left :center)
          (q/text (header-text) (+ top-margin 10) (/ top-margin 2))
          (q/text-align :center :top)
          (q/text-size 12)
          (q/text config/version (/ (q/width) 2) 5)
          (screen/display-column-headers @presenter/screen-type flap-width header-font)
          (display-time))]

    (q/background 30)
    (draw-header)
    (draw-lines)
    (draw-flappers)))

(defn do-update [{:keys [time flappers lines] :as state}]
  (let [now (System/currentTimeMillis)
        old-summary lines
        summary (presenter/make-screen)
        new-screen? (not= summary old-summary)
        new-screen-time (if new-screen? now time)
        flappers (cond
                   new-screen? (make-flappers summary nil)
                   (> (- now new-screen-time) config/flap-duration) []
                   :else (update-flappers flappers)
                   )]
    (assoc state :time new-screen-time
                 :lines summary
                 :flappers flappers)))