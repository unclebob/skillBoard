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
                          (.beginDraw glyph)
                          (.noSmooth glyph)
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

(defn- display-com-errors! [pos]
  (draw-status-light! pos 20 @comm/reservation-com-errors)
  (draw-status-light! pos 35 @comm/adsb-com-errors)
  (draw-status-light! pos 50 @comm/weather-com-errors))

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
    (draw-header! state header-font header-font-size label-font-size clock-font clock-font-size now geometry)
    (draw-lines! lines sf-font sf-font-size geometry)
    (draw-flappers! flappers line-colors sf-font sf-font-size geometry)))

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T11:34:56.015723-05:00", :module-hash "-128456467", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 12, :hash "1920259433"} {:id "def/next-char", :kind "def", :line 14, :end-line 57, :hash "-1113363380"} {:id "defn/get-next-char", :kind "defn", :line 59, :end-line 60, :hash "-2009754732"} {:id "defn-/remaining-flapper", :kind "defn-", :line 62, :end-line 65, :hash "956020619"} {:id "defn/add-remaining-flappers", :kind "defn", :line 67, :end-line 71, :hash "-1424815926"} {:id "defn/make-flappers-for-line", :kind "defn", :line 73, :end-line 88, :hash "-1150675378"} {:id "defn/make-flappers", :kind "defn", :line 90, :end-line 97, :hash "-1702311483"} {:id "defn/update-flappers", :kind "defn", :line 99, :end-line 120, :hash "-737275604"} {:id "defn/header-text", :kind "defn", :line 122, :end-line 123, :hash "114439821"} {:id "defn/pad-and-trim-line", :kind "defn", :line 125, :end-line 127, :hash "776748785"} {:id "def/color-rgb", :kind "def", :line 129, :end-line 136, :hash "-923251396"} {:id "def/default-color-rgb", :kind "def", :line 138, :end-line 138, :hash "400843010"} {:id "defn-/color-components", :kind "defn-", :line 140, :end-line 141, :hash "487777990"} {:id "defn/set-color", :kind "defn", :line 143, :end-line 145, :hash "-1661480124"} {:id "def/lines-layer-cache", :kind "def", :line 147, :end-line 147, :hash "-1174342755"} {:id "def/flapper-glyph-cache", :kind "def", :line 148, :end-line 148, :hash "768445455"} {:id "defn-/set-layer-color", :kind "defn-", :line 150, :end-line 152, :hash "470209911"} {:id "defn-/render-visible-line-char!", :kind "defn-", :line 154, :end-line 164, :hash "-929574463"} {:id "defn-/render-line-char!", :kind "defn-", :line 166, :end-line 172, :hash "316695977"} {:id "defn-/render-line-chars!", :kind "defn-", :line 174, :end-line 182, :hash "-479351971"} {:id "defn-/render-report-line!", :kind "defn-", :line 184, :end-line 191, :hash "1392765341"} {:id "defn-/add-rendered-count", :kind "defn-", :line 193, :end-line 194, :hash "666732482"} {:id "defn-/render-lines-layer!", :kind "defn-", :line 196, :end-line 215, :hash "-112768291"} {:id "defn-/flapper-char-set", :kind "defn-", :line 217, :end-line 221, :hash "-1958648659"} {:id "defn-/render-flapper-glyphs!", :kind "defn-", :line 223, :end-line 250, :hash "-23427186"} {:id "defn-/draw-now-text", :kind "defn-", :line 252, :end-line 254, :hash "-1928031187"} {:id "defn-/draw-geometry", :kind "defn-", :line 256, :end-line 266, :hash "762353633"} {:id "defn-/lines-layer-key", :kind "defn-", :line 268, :end-line 277, :hash "-1079152757"} {:id "defn-/current-lines-layer", :kind "defn-", :line 279, :end-line 287, :hash "-943945809"} {:id "defn-/draw-lines!", :kind "defn-", :line 289, :end-line 303, :hash "1184045821"} {:id "defn-/glyph-key", :kind "defn-", :line 305, :end-line 313, :hash "310858416"} {:id "defn-/cached-glyphs", :kind "defn-", :line 315, :end-line 329, :hash "416450688"} {:id "defn-/flapper-position", :kind "defn-", :line 331, :end-line 334, :hash "-968831626"} {:id "defn-/draw-uncached-flapper!", :kind "defn-", :line 336, :end-line 346, :hash "-540675139"} {:id "defn-/draw-flapper!", :kind "defn-", :line 348, :end-line 356, :hash "972610288"} {:id "defn-/draw-flappers!", :kind "defn-", :line 358, :end-line 362, :hash "1756293776"} {:id "def/status-light-thresholds", :kind "def", :line 364, :end-line 366, :hash "2121939427"} {:id "def/default-status-light-rgb", :kind "def", :line 368, :end-line 368, :hash "-1410728437"} {:id "defn-/status-light-rgb", :kind "defn-", :line 370, :end-line 374, :hash "-1313487399"} {:id "defn-/draw-status-light!", :kind "defn-", :line 376, :end-line 379, :hash "-1943852759"} {:id "defn-/display-com-errors!", :kind "defn-", :line 381, :end-line 384, :hash "-855560315"} {:id "defn-/display-time!", :kind "defn-", :line 386, :end-line 393, :hash "-1770118503"} {:id "defn-/draw-header!", :kind "defn-", :line 395, :end-line 406, :hash "935010868"} {:id "defn/draw", :kind "defn", :line 408, :end-line 416, :hash "-1133446758"} {:id "defn/blank-line", :kind "defn", :line 418, :end-line 419, :hash "-1220779777"} {:id "defn/blank-screen", :kind "defn", :line 421, :end-line 422, :hash "234994382"} {:id "defn/current-time-ms", :kind "defn", :line 424, :end-line 425, :hash "1840988419"} {:id "defn-/screen-summary", :kind "defn-", :line 427, :end-line 430, :hash "1926277654"} {:id "defn-/next-screen-time", :kind "defn-", :line 432, :end-line 433, :hash "824504023"} {:id "defn-/flap-duration-expired?", :kind "defn-", :line 435, :end-line 436, :hash "-598238773"} {:id "defn-/next-flappers", :kind "defn-", :line 438, :end-line 450, :hash "2066365699"} {:id "defn/do-update", :kind "defn", :line 452, :end-line 462, :hash "-510576541"}]}
;; clj-mutate-manifest-end
