(ns skillBoard.split-flap
  (:require
    [clojure.string :as string]
    [java-time.api :as time]
    [quil.core :as q]
    [skillBoard.config :as config]
    [skillBoard.flight-schedule-pro :as fsp]
    [skillBoard.presenter :as presenter]
    [skillBoard.radar-cape :as radar-cape]
    [skillBoard.text-util :as text]
    [skillBoard.time-util :as time-util]
    [skillBoard.weather :as weather]
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

(defn do-update [{:keys [time flappers lines pulse] :as state}]
  (let [now (System/currentTimeMillis)
        since (quot (- now time) 1000)
        poll? (or
                (> since config/seconds-between-internet-polls)
                (q/mouse-pressed?))
        old-summary lines
        summary (if poll?
                  (presenter/make-screen)
                  old-summary)
        flappers (cond
                   (not= summary old-summary) (make-flappers summary old-summary)
                   (> (- now time) config/flap-duration) []
                   :else (update-flappers flappers)
                   )
        frame-rate (if (empty? flappers) 2 10)]
    (q/frame-rate frame-rate)
    (assoc state :time (if poll? now time)
                 :lines summary
                 :flappers flappers
                 :pulse (not pulse))))

(defn header-text []
  (condp = @presenter/screen-type
    :flights "FLIGHT OPERATIONS"
    :taf "WEATHER"
    :flight-category "FLIGHT CATEGORY"
    "TILT"))

(defn pad-and-trim-line [line length]
  (let [padded-line (str line (apply str (repeat length " ")))]
    (subs padded-line 0 length)))

(defn draw [{:keys [sf-font sf-font-size clock-font lines flappers font-width font-height pulse header-font] :as state}]
  (let [now (time-util/get-HHmm (time-util/local-to-utc (time/local-date-time)))
        now (if pulse now (string/replace now ":" " "))
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

        setup-headers
        (fn []
          (let [label-height (* 0.8 (:label-height @config/display-info))
                label-font-size (text/find-font-size-for-height header-font label-height)
                baseline (- (+ top-margin label-height) (/ label-height 2))
                ]
            (q/text-font header-font)
            (q/text-size label-font-size)
            (q/text-align :left :center)
            (q/fill 255 255 255)
            baseline
            ))

        display-flight-operation-headers
        (fn []
          (let [baseline (setup-headers)]
            (q/text "TIME" 0 baseline)
            (q/text "AIRCRAFT" (* flap-width 7) baseline)
            (q/text "CREW" (* flap-width 14) baseline)
            (q/text "OUT" (* flap-width 26) baseline)
            (q/text "BRG/ALT/GS" (* flap-width 33) baseline)
            (q/text "REMARKS" (* flap-width 51) baseline)
            )
          )

        display-flight-category-headers
        (fn []
          (let [baseline (setup-headers)]
            (q/text "AIRPORT" 0 baseline)
            (q/text "CATGRY" (* flap-width 5) baseline)
            (q/text "SKY" (* flap-width 10) baseline)
            (q/text "BASE" (* flap-width 14) baseline)
            (q/text "VIS" (* flap-width 20) baseline)
            (q/text "WIND" (* flap-width 24) baseline)
            )
          )

        display-column-headers
        (fn []
          (condp = @presenter/screen-type
            :flights (display-flight-operation-headers)
            :taf nil
            :flight-category (display-flight-category-headers)))

        display-com-errors
        (fn [pos]
          (cond
            (> @fsp/com-errors 6) (q/fill 255 0 0)
            (> @fsp/com-errors 3) (q/fill 255 165 0)
            :else (q/fill 0 255 0))
          (q/ellipse pos 20 10 10)

          (cond
            (> @radar-cape/com-errors 3) (q/fill 255 0 0)
            (> @radar-cape/com-errors 1) (q/fill 255 165 0)
            :else (q/fill 0 255 0))
          (q/ellipse pos 35 10 10)

          (cond
            (> @weather/com-errors 3) (q/fill 255 0 0)
            (> @weather/com-errors 1) (q/fill 255 165 0)
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
          (display-column-headers)
          (display-time))]

    (q/background 30)
    (draw-header)
    (draw-lines)
    (draw-flappers)
    ))

