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

(defn- remaining-flapper [col row type c]
  (if (= type :old)
    {:at [col row] :to \space :from c}
    {:at [col row] :from \space :to c}))

(defn add-remaining-flappers [flappers remainder col row type]
  (into flappers
        (map-indexed (fn [idx c]
                       (remaining-flapper (+ col idx) row type c))
                     remainder)))

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
  (reduce (fn [flappers row]
            (make-flappers-for-line (nth new-report row nil)
                                    (nth old-report row nil)
                                    row
                                    flappers))
          []
          (range (max (count new-report) (count old-report)))))

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

(def color-rgb
  {:white [255 255 255]
   :red [255 125 125]
   :green [175 255 175]
   :blue [175 175 255]
   :magenta [255 200 255]
   :cyan [175 255 255]
   :yellow [255 255 175]})

(def default-color-rgb [175 175 175])

(defn- color-components [color]
  (get color-rgb color default-color-rgb))

(defn set-color [color]
  (let [[r g b] (color-components color)]
    (q/fill r g b)))

(def lines-layer-cache (atom {:key nil :layer nil}))
(def flapper-glyph-cache (atom {:key nil :glyphs nil}))

(defn- set-layer-color [layer color]
  (let [[r g b] (color-components color)]
    (.fill layer r g b)))

(defn- render-visible-line-char! [layer color c x cy
                                  backing-rect-top-left-x backing-rect-top-left-y
                                  backing-rect-width backing-rect-height]
  (set-layer-color layer color)
  (.rect layer
         (float (+ x backing-rect-top-left-x))
         (float (+ cy backing-rect-top-left-y))
         (float backing-rect-width)
         (float backing-rect-height))
  (.fill layer 0 0 0)
  (.text layer (str c) (float x) (float cy)))

(defn- render-line-char! [layer color c x cy
                          backing-rect-top-left-x backing-rect-top-left-y
                          backing-rect-width backing-rect-height]
  (when (not= c \space)
    (render-visible-line-char! layer color c x cy
                               backing-rect-top-left-x backing-rect-top-left-y
                               backing-rect-width backing-rect-height)))

(defn- render-line-chars! [layer color line cy flap-width
                           backing-rect-top-left-x backing-rect-top-left-y
                           backing-rect-width backing-rect-height]
  (let [cs (pad-and-trim-line line config/cols)]
    (doseq [[idx c] (map-indexed vector cs)]
      (render-line-char! layer color c (* idx flap-width) cy
                         backing-rect-top-left-x backing-rect-top-left-y
                         backing-rect-width backing-rect-height))
    (count (remove #{\space} cs))))

(defn- render-report-line! [layer line color y flap-width flap-height label-margin
                            backing-rect-top-left-x backing-rect-top-left-y
                            backing-rect-width backing-rect-height]
  (if (string/blank? line)
    0
    (render-line-chars! layer color line (+ (* y flap-height) label-margin) flap-width
                        backing-rect-top-left-x backing-rect-top-left-y
                        backing-rect-width backing-rect-height)))

(defn- add-rendered-count [counts rendered-count]
  (update-vals counts #(+ % rendered-count)))

(defn- render-lines-layer! [layer lines sf-font sf-font-size flap-width flap-height label-margin
                            backing-rect-top-left-x backing-rect-top-left-y
                            backing-rect-width backing-rect-height]
  (.beginDraw layer)
  (.clear layer)
  (.noStroke layer)
  (.textFont layer sf-font)
  (.textSize layer sf-font-size)
  (.textAlign layer processing.core.PConstants/LEFT processing.core.PConstants/TOP)
  (let [counts (reduce (fn [counts [y {:keys [line color]}]]
                         (let [rendered-count (render-report-line! layer line color y flap-width flap-height label-margin
                                                                   backing-rect-top-left-x backing-rect-top-left-y
                                                                   backing-rect-width backing-rect-height)]
                           (add-rendered-count counts rendered-count)))
                       {:line-char-count 0
                        :line-rect-count 0
                        :line-text-count 0}
                       (map-indexed vector lines))]
    (.endDraw layer)
    counts))

(defn- flapper-char-set []
  (->> (concat (keys next-char) (vals next-char))
       distinct
       (remove #(= % \space))
       vec))

(defn- render-flapper-glyphs! [sf-font sf-font-size flap-width flap-height
                               backing-rect-top-left-x backing-rect-top-left-y
                               backing-rect-width backing-rect-height colors]
  (let [gw (max 1 (int (Math/ceil flap-width)))
        gh (max 1 (int (Math/ceil flap-height)))]
    (reduce (fn [glyphs color]
                      (reduce (fn [glyphs c]
                        (let [glyph (q/create-graphics gw gh)]
                          (.noSmooth glyph)
                          (.beginDraw glyph)
                          (.clear glyph)
                          (.textFont glyph sf-font)
                          (.textSize glyph sf-font-size)
                          (.textAlign glyph processing.core.PConstants/LEFT processing.core.PConstants/TOP)
                          (set-layer-color glyph color)
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

(defn- draw-now-text []
  (let [now (time-util/get-HHmm (time-util/local-to-utc (time/local-date-time)))]
    (if @atoms/clock-pulse now (string/replace now ":" " "))))

(defn- draw-geometry [font-width font-height]
  (let [top-margin (:top-margin @config/display-info)
        label-margin (+ top-margin (:label-height @config/display-info))]
    {:flap-width (+ font-width (:sf-char-gap @config/display-info))
     :flap-height (* font-height (inc config/sf-line-gap))
     :top-margin top-margin
     :label-margin label-margin
     :backing-rect-top-left-x (int (* font-width 0.1))
     :backing-rect-top-left-y (int (* font-height 0.1))
     :backing-rect-width (int (* font-width 0.8))
     :backing-rect-height (int (* font-height 0.8))}))

(defn- lines-layer-key [lines sf-font-size geometry]
  [lines
   (:flap-width geometry)
   (:flap-height geometry)
   (:label-margin geometry)
   (:backing-rect-top-left-x geometry)
   (:backing-rect-top-left-y geometry)
   (:backing-rect-width geometry)
   (:backing-rect-height geometry)
   sf-font-size])

(defn- current-lines-layer []
  (let [{:keys [layer]} @lines-layer-cache
        width (q/width)
        height (q/height)
        size-match? (and (some? layer)
                         (= (.-width layer) width)
                         (= (.-height layer) height))]
    {:layer (if size-match? layer (q/create-graphics width height))
     :size-match? size-match?}))

(defn- draw-lines! [lines sf-font sf-font-size geometry]
  (let [{cached-key :key} @lines-layer-cache
        {:keys [layer size-match?]} (current-lines-layer)
        layer-key (lines-layer-key lines sf-font-size geometry)]
    (when (or (not size-match?) (not= cached-key layer-key))
      (render-lines-layer! layer lines sf-font sf-font-size
                           (:flap-width geometry)
                           (:flap-height geometry)
                           (:label-margin geometry)
                           (:backing-rect-top-left-x geometry)
                           (:backing-rect-top-left-y geometry)
                           (:backing-rect-width geometry)
                           (:backing-rect-height geometry)))
    (reset! lines-layer-cache {:key layer-key :layer layer})
    (q/image layer 0 0)))

(defn- glyph-key [sf-font sf-font-size colors geometry]
  [sf-font sf-font-size
   (:flap-width geometry)
   (:flap-height geometry)
   (:backing-rect-top-left-x geometry)
   (:backing-rect-top-left-y geometry)
   (:backing-rect-width geometry)
   (:backing-rect-height geometry)
   colors])

(defn- cached-glyphs [sf-font sf-font-size colors geometry]
  (let [{:keys [key glyphs]} @flapper-glyph-cache
        new-key (glyph-key sf-font sf-font-size colors geometry)]
    (if (= key new-key)
      glyphs
      (let [glyphs (render-flapper-glyphs! sf-font sf-font-size
                                            (:flap-width geometry)
                                            (:flap-height geometry)
                                            (:backing-rect-top-left-x geometry)
                                            (:backing-rect-top-left-y geometry)
                                            (:backing-rect-width geometry)
                                            (:backing-rect-height geometry)
                                            colors)]
        (reset! flapper-glyph-cache {:key new-key :glyphs glyphs})
        glyphs))))

(defn- flapper-position [[col row] geometry]
  [(int (Math/round (double (* col (:flap-width geometry)))))
   (int (Math/round (double (+ (* row (:flap-height geometry))
                               (:label-margin geometry)))))])

(defn- draw-uncached-flapper! [color from x y sf-font sf-font-size geometry]
  (set-color color)
  (q/rect (+ x (:backing-rect-top-left-x geometry))
          (+ y (:backing-rect-top-left-y geometry))
          (:backing-rect-width geometry)
          (:backing-rect-height geometry))
  (q/text-font sf-font)
  (q/text-size sf-font-size)
  (q/text-align :left :top)
  (q/fill 0 0 0)
  (q/text (str from) x y))

(defn- draw-flapper! [line-colors glyphs sf-font sf-font-size geometry {:keys [at from]}]
  (when (not= from \space)
    (let [[_ row] at
          [x y] (flapper-position at geometry)
          color (nth line-colors row nil)
          glyph (get glyphs [color from])]
      (if (some? glyph)
        (q/image glyph x y)
        (draw-uncached-flapper! color from x y sf-font sf-font-size geometry)))))

(defn- draw-flappers! [flappers line-colors sf-font sf-font-size geometry]
  (let [colors (->> line-colors distinct vec)
        glyphs (cached-glyphs sf-font sf-font-size colors geometry)]
    (doseq [flapper flappers]
      (draw-flapper! line-colors glyphs sf-font sf-font-size geometry flapper))))

(def status-light-thresholds
  [[3 [255 0 0]]
   [1 [255 165 0]]])

(def default-status-light-rgb [0 255 0])

(defn- status-light-rgb [errors]
  (or (some (fn [[threshold rgb]]
              (when (> errors threshold) rgb))
            status-light-thresholds)
      default-status-light-rgb))

(defn- draw-status-light! [pos y errors]
  (let [[r g b] (status-light-rgb errors)]
    (q/fill r g b)
    (q/ellipse pos y 10 10)))

(defn- weather-status-light-rgb []
  (if @comm/open-meteo-ok?
    (status-light-rgb @comm/weather-com-errors)
    [255 0 0]))

(defn- draw-weather-status-light! [pos]
  (let [[r g b] (weather-status-light-rgb)]
    (q/fill r g b)
    (q/ellipse pos 50 10 10)))

(defn- display-com-errors! [pos]
  (draw-status-light! pos 20 @comm/reservation-com-errors)
  (draw-status-light! pos 35 @comm/adsb-com-errors)
  (draw-weather-status-light! pos))

(defn- display-time! [clock-font clock-font-size now]
  (q/text-font clock-font)
  (q/text-size clock-font-size)
  (let [time-pos (- (q/width) (q/text-width now) 50)]
    (display-com-errors! (- time-pos 10))
    (q/text-align :left :top)
    (q/fill 255 255 255)
    (q/text now time-pos 10)))

(defn- draw-header! [state header-font header-font-size label-font-size clock-font clock-font-size now geometry]
  (q/image (:departure-icon state) 0 0 (:top-margin geometry) (:top-margin geometry))
  (q/fill 255)
  (q/text-font header-font)
  (q/text-size header-font-size)
  (q/text-align :left :center)
  (q/text (header-text) (+ (:top-margin geometry) 10) (/ (:top-margin geometry) 2))
  (q/text-align :center :top)
  (q/text-size 12)
  (q/text config/version (/ (q/width) 2) 5)
  (screen/display-column-headers @presenter/screen-type (:flap-width geometry) header-font label-font-size)
  (display-time! clock-font clock-font-size now))

(defn draw [{:keys [sf-font sf-font-size clock-font clock-font-size lines flappers
                    font-width font-height header-font header-font-size label-font-size] :as state}]
  (let [line-colors (mapv :color lines)
        now (draw-now-text)
        geometry (draw-geometry font-width font-height)]
    (q/background 30)
    (if (screen/draw-body @presenter/screen-type state)
      (draw-header! state header-font header-font-size label-font-size clock-font clock-font-size now geometry)
      (do
        (draw-header! state header-font header-font-size label-font-size clock-font clock-font-size now geometry)
        (draw-lines! lines sf-font sf-font-size geometry)
        (draw-flappers! flappers line-colors sf-font sf-font-size geometry)))))

(defn blank-line []
  {:line (apply str (repeat config/cols " ")) :color nil})

(defn blank-screen []
  (repeat (:line-count @config/display-info) (blank-line)))

(defn current-time-ms []
  (System/currentTimeMillis))

(defn- screen-summary []
  (if @atoms/screen-changed?
    (blank-screen)
    (presenter/make-screen)))

(defn- next-screen-time [new-screen? now previous-time]
  (if new-screen? now previous-time))

(defn- flap-duration-expired? [now screen-time]
  (> (- now screen-time) config/flap-duration))

(defn- next-flappers [summary old-summary new-screen? now screen-time flappers]
  (cond
    @atoms/screen-changed?
    (make-flappers summary (blank-screen))

    new-screen?
    (make-flappers summary old-summary)

    (flap-duration-expired? now screen-time)
    []

    :else
    (update-flappers flappers)))

(defn do-update [{:keys [time flappers lines] :as state}]
  (let [now (current-time-ms)
        old-summary lines
        summary (screen-summary)
        new-screen? (not= summary old-summary)
        new-screen-time (next-screen-time new-screen? now time)
        flappers (next-flappers summary old-summary new-screen? now new-screen-time flappers)]
    (reset! atoms/screen-changed? false)
    (assoc state :time new-screen-time
                 :lines summary
                 :flappers flappers)))
