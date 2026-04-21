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
            rendered-count (render-report-line! layer line color y flap-width flap-height label-margin
                                                backing-rect-top-left-x backing-rect-top-left-y
                                                backing-rect-width backing-rect-height)]
        (recur (rest remaining-lines)
               (inc y)
               (+ line-char-count rendered-count)
               (+ line-rect-count rendered-count)
               (+ line-text-count rendered-count))))))

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

(defn- status-light-rgb [errors]
  (or (some (fn [[threshold rgb]]
              (when (> errors threshold) rgb))
            status-light-thresholds)
      [0 255 0]))

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
;; {:version 1, :tested-at "2026-04-21T11:14:38.140134-05:00", :module-hash "1288394638", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 12, :hash "1920259433"} {:id "def/next-char", :kind "def", :line 14, :end-line 57, :hash "-1113363380"} {:id "defn/get-next-char", :kind "defn", :line 59, :end-line 60, :hash "-2009754732"} {:id "defn/add-remaining-flappers", :kind "defn", :line 62, :end-line 70, :hash "-259913949"} {:id "defn/make-flappers-for-line", :kind "defn", :line 72, :end-line 87, :hash "-1150675378"} {:id "defn/make-flappers", :kind "defn", :line 89, :end-line 103, :hash "925688221"} {:id "defn/update-flappers", :kind "defn", :line 105, :end-line 126, :hash "-737275604"} {:id "defn/header-text", :kind "defn", :line 128, :end-line 129, :hash "114439821"} {:id "defn/pad-and-trim-line", :kind "defn", :line 131, :end-line 133, :hash "776748785"} {:id "def/color-rgb", :kind "def", :line 135, :end-line 142, :hash "-923251396"} {:id "def/default-color-rgb", :kind "def", :line 144, :end-line 144, :hash "400843010"} {:id "defn-/color-components", :kind "defn-", :line 146, :end-line 147, :hash "487777990"} {:id "defn/set-color", :kind "defn", :line 149, :end-line 151, :hash "-1661480124"} {:id "def/lines-layer-cache", :kind "def", :line 153, :end-line 153, :hash "-1174342755"} {:id "def/flapper-glyph-cache", :kind "def", :line 154, :end-line 154, :hash "768445455"} {:id "defn-/set-layer-color", :kind "defn-", :line 156, :end-line 158, :hash "470209911"} {:id "defn-/render-visible-line-char!", :kind "defn-", :line 160, :end-line 170, :hash "-929574463"} {:id "defn-/render-line-char!", :kind "defn-", :line 172, :end-line 178, :hash "316695977"} {:id "defn-/render-line-chars!", :kind "defn-", :line 180, :end-line 188, :hash "-479351971"} {:id "defn-/render-report-line!", :kind "defn-", :line 190, :end-line 197, :hash "1392765341"} {:id "defn-/render-lines-layer!", :kind "defn-", :line 199, :end-line 227, :hash "1464229563"} {:id "defn-/flapper-char-set", :kind "defn-", :line 229, :end-line 233, :hash "-1556162328"} {:id "defn-/render-flapper-glyphs!", :kind "defn-", :line 235, :end-line 262, :hash "-23427186"} {:id "defn-/draw-now-text", :kind "defn-", :line 264, :end-line 266, :hash "-1928031187"} {:id "defn-/draw-geometry", :kind "defn-", :line 268, :end-line 278, :hash "762353633"} {:id "defn-/lines-layer-key", :kind "defn-", :line 280, :end-line 289, :hash "-1079152757"} {:id "defn-/current-lines-layer", :kind "defn-", :line 291, :end-line 299, :hash "-943945809"} {:id "defn-/draw-lines!", :kind "defn-", :line 301, :end-line 315, :hash "1184045821"} {:id "defn-/glyph-key", :kind "defn-", :line 317, :end-line 325, :hash "310858416"} {:id "defn-/cached-glyphs", :kind "defn-", :line 327, :end-line 341, :hash "416450688"} {:id "defn-/flapper-position", :kind "defn-", :line 343, :end-line 346, :hash "-968831626"} {:id "defn-/draw-uncached-flapper!", :kind "defn-", :line 348, :end-line 358, :hash "-540675139"} {:id "defn-/draw-flapper!", :kind "defn-", :line 360, :end-line 368, :hash "972610288"} {:id "defn-/draw-flappers!", :kind "defn-", :line 370, :end-line 374, :hash "1756293776"} {:id "def/status-light-thresholds", :kind "def", :line 376, :end-line 378, :hash "2121939427"} {:id "defn-/status-light-rgb", :kind "defn-", :line 380, :end-line 384, :hash "1281867651"} {:id "defn-/draw-status-light!", :kind "defn-", :line 386, :end-line 389, :hash "-1943852759"} {:id "defn-/display-com-errors!", :kind "defn-", :line 391, :end-line 394, :hash "-855560315"} {:id "defn-/display-time!", :kind "defn-", :line 396, :end-line 403, :hash "-1770118503"} {:id "defn-/draw-header!", :kind "defn-", :line 405, :end-line 416, :hash "935010868"} {:id "defn/draw", :kind "defn", :line 418, :end-line 426, :hash "-1133446758"} {:id "defn/blank-line", :kind "defn", :line 428, :end-line 429, :hash "-1220779777"} {:id "defn/blank-screen", :kind "defn", :line 431, :end-line 432, :hash "234994382"} {:id "defn/current-time-ms", :kind "defn", :line 434, :end-line 435, :hash "1840988419"} {:id "defn-/screen-summary", :kind "defn-", :line 437, :end-line 440, :hash "1926277654"} {:id "defn-/next-screen-time", :kind "defn-", :line 442, :end-line 443, :hash "824504023"} {:id "defn-/flap-duration-expired?", :kind "defn-", :line 445, :end-line 446, :hash "-598238773"} {:id "defn-/next-flappers", :kind "defn-", :line 448, :end-line 460, :hash "2066365699"} {:id "defn/do-update", :kind "defn", :line 462, :end-line 472, :hash "-510576541"}]}
;; clj-mutate-manifest-end
