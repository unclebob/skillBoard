(ns skillBoard.core
  (:require
    [quil.core :as q]
    [quil.middleware :as m]
    [skillBoard.atoms :as atoms]
    [skillBoard.comm-utils :as comm]
    [skillBoard.config :as config]
    [skillBoard.core-utils :as core-utils]
    [skillBoard.presenters.airports]
    [skillBoard.presenters.flights]
    [skillBoard.presenters.main :as presenter]
    [skillBoard.presenters.traffic]
    [skillBoard.presenters.weather]
    [skillBoard.split-flap :as split-flap]
    [skillBoard.text-util :as text]))

(defn load-display-info []
  (let [screen-width (q/width)
        useful-width (- screen-width 10)
        screen-height (q/height)
        char-width (/ useful-width config/cols)
        sf-char-gap (* char-width config/sf-char-gap)
        font-width (- char-width sf-char-gap)
        sf-font-size (text/find-font-size-for-width (:sf-font @config/display-info) font-width)]
    (swap! config/display-info
           assoc
           :size [screen-width screen-height]
           :top-margin (* screen-height config/header-height-fraction)
           :label-height (* screen-height config/label-height-fraction)
           :sf-font-size sf-font-size
           :sf-char-gap sf-char-gap
           )))

(defn- load-fonts []
  (let [sf-font (q/create-font "Skyfont" 32)
        header-font (q/create-font "Bahnschrift" 50)
        annotation-font (q/create-font "Times New Roman" 9)
        clock-font (q/create-font "DSEG7 Modern" 32)]
    (swap! config/display-info assoc
           :sf-font sf-font
           :header-font header-font
           :annotation-font annotation-font
           :clock-font clock-font)))

(defn poll []
  (try
    (comm/get-aircraft)
    (comm/get-adsb-by-tail-numbers @comm/polled-aircraft)
    (comm/get-flights)
    (comm/get-reservations)
    (comm/get-metars config/flight-category-airports)
    (comm/get-metar-history config/airport)
    (comm/get-tafs config/taf-airport)
    (comm/get-nearby-adsb)
    (reset! atoms/log-traffic? true)
    (catch Exception e
      (core-utils/log :error e))))

(defn start-polling []
  (poll)
  (future
    (loop []
      (let [now (System/currentTimeMillis)
            seconds-since-last-poll (quot (- now @atoms/poll-time) 1000)]
        (when (or @atoms/poll-key
                  (>= seconds-since-last-poll config/seconds-between-internet-polls))
          (future (poll))
          (reset! atoms/poll-key false)
          (reset! atoms/poll-time now))
        (reset! atoms/clock-pulse (< 500 (mod now 1000)))
        (Thread/sleep 100)
        (recur))
      )
    ))

(defn setup []
  (core-utils/log :status "Setup...")
  (load-fonts)
  (config/load-config)
  (load-display-info)
  (start-polling)
  (let [{:keys [sf-font-size sf-font header-font annotation-font clock-font
                size top-margin label-height]} @config/display-info
        _ (q/text-font sf-font)
        _ (q/text-size sf-font-size)
        font-width (q/text-width "X")
        font-height (+ (q/text-ascent) (q/text-descent))
        header-font-size (text/find-font-size-for-height header-font (* 0.7 top-margin))
        label-font-size (text/find-font-size-for-height header-font (* 0.8 label-height))
        clock-font-size (text/find-font-size-for-height clock-font (* 0.5 top-margin))
        text-area-height (- (second size) top-margin label-height)
        line-height (* font-height (+ 1 config/sf-line-gap))
        lines-count (quot text-area-height line-height)
        _ (swap! config/display-info assoc
                 :font-width font-width
                 :font-height font-height
                 :line-count lines-count)
        summary (presenter/make-screen)
        flappers (split-flap/make-flappers summary [])
        now (System/currentTimeMillis)]
    (q/frame-rate config/frame-rate)
    (q/background 255)
    {:time now
     :lines summary
     :flappers flappers
     :sf-font sf-font
     :sf-font-size sf-font-size
     :clock-font clock-font
     :font-width font-width
     :font-height font-height
     :line-count lines-count
     :header-font header-font
     :header-font-size header-font-size
     :label-font-size label-font-size
     :clock-font-size clock-font-size
     :annotation-font annotation-font
     :departure-icon (q/load-image "resources/flightlogo.png")}))

(defn update-state [state]
  (try
    (let [now (System/currentTimeMillis)
          updated-state (split-flap/do-update state)]
      (when @atoms/test?
        (swap! atoms/update-count inc)
        (swap! atoms/update-time-accumulator + (- (System/currentTimeMillis) now))
        (swap! atoms/max-flapper-count max (count (:flappers updated-state))))
      updated-state)
    (catch Exception e
      (core-utils/log :error e)
      state)))

(def frame-count (atom 0))

(defn draw-state [state]
  (try
    (let [now (System/currentTimeMillis)]
      (split-flap/draw state)
      (when @atoms/test?
        (swap! frame-count inc)
        (swap! atoms/draw-time-accumulator + (- (System/currentTimeMillis) now))
        (when (>= (- now @atoms/draw-time-start) 1000)
          (let [draw-total @atoms/draw-time-accumulator
                update-total @atoms/update-time-accumulator
                flap-make-total @atoms/flapper-make-time-accumulator
                flap-update-total @atoms/flapper-update-time-accumulator
                render-background-total @atoms/render-background-time-accumulator
                render-header-total @atoms/render-header-time-accumulator
                render-lines-total @atoms/render-lines-time-accumulator
                render-lines-rerender-total @atoms/render-lines-rerender-time-accumulator
                render-lines-rerender-count @atoms/render-lines-rerender-count
                render-flappers-total @atoms/render-flappers-time-accumulator
                render-line-chars-total @atoms/render-lines-char-count-accumulator
                render-line-rects-total @atoms/render-lines-rect-count-accumulator
                render-line-texts-total @atoms/render-lines-text-count-accumulator
                render-flapper-texts-total @atoms/render-flapper-text-count-accumulator
                frames @frame-count
                updates @atoms/update-count
                flap-makes @atoms/flapper-make-count
                flap-updates @atoms/flapper-update-count
                draw-avg (if (zero? frames) 0.0 (double (/ draw-total frames)))
                update-avg (if (zero? updates) 0.0 (double (/ update-total updates)))
                flap-make-avg (if (zero? flap-makes) 0.0 (double (/ flap-make-total flap-makes)))
                flap-update-avg (if (zero? flap-updates) 0.0 (double (/ flap-update-total flap-updates)))
                render-background-avg (if (zero? frames) 0.0 (double (/ render-background-total frames)))
                render-header-avg (if (zero? frames) 0.0 (double (/ render-header-total frames)))
                render-lines-avg (if (zero? frames) 0.0 (double (/ render-lines-total frames)))
                render-lines-rerender-avg (if (zero? render-lines-rerender-count) 0.0 (double (/ render-lines-rerender-total render-lines-rerender-count)))
                render-flappers-avg (if (zero? frames) 0.0 (double (/ render-flappers-total frames)))
                render-line-chars-avg (if (zero? frames) 0.0 (double (/ render-line-chars-total frames)))
                render-line-rects-avg (if (zero? frames) 0.0 (double (/ render-line-rects-total frames)))
                render-line-texts-avg (if (zero? frames) 0.0 (double (/ render-line-texts-total frames)))
                render-flapper-texts-avg (if (zero? frames) 0.0 (double (/ render-flapper-texts-total frames)))]
            (core-utils/log :profile
                            (format "fps=%d draw_total_ms=%d draw_avg_ms=%.3f updates=%d update_total_ms=%d update_avg_ms=%.3f flap_make_count=%d flap_make_total_ms=%.3f flap_make_avg_ms=%.3f flap_update_count=%d flap_update_total_ms=%.3f flap_update_avg_ms=%.3f render_background_total_ms=%.3f render_background_avg_ms=%.3f render_header_total_ms=%.3f render_header_avg_ms=%.3f render_lines_total_ms=%.3f render_lines_avg_ms=%.3f render_lines_rerender_count=%d render_lines_rerender_total_ms=%.3f render_lines_rerender_avg_ms=%.3f render_flappers_total_ms=%.3f render_flappers_avg_ms=%.3f render_line_chars_total=%d render_line_chars_avg=%.1f render_line_rects_total=%d render_line_rects_avg=%.1f render_line_texts_total=%d render_line_texts_avg=%.1f render_flapper_texts_total=%d render_flapper_texts_avg=%.1f max_flappers=%d"
                                    frames
                                    draw-total
                                    draw-avg
                                    updates
                                    update-total
                                    update-avg
                                    flap-makes
                                    flap-make-total
                                    flap-make-avg
                                    flap-updates
                                    flap-update-total
                                    flap-update-avg
                                    render-background-total
                                    render-background-avg
                                    render-header-total
                                    render-header-avg
                                    render-lines-total
                                    render-lines-avg
                                    render-lines-rerender-count
                                    render-lines-rerender-total
                                    render-lines-rerender-avg
                                    render-flappers-total
                                    render-flappers-avg
                                    render-line-chars-total
                                    render-line-chars-avg
                                    render-line-rects-total
                                    render-line-rects-avg
                                    render-line-texts-total
                                    render-line-texts-avg
                                    render-flapper-texts-total
                                    render-flapper-texts-avg
                                    @atoms/max-flapper-count)))
          (reset! atoms/draw-time-start now)
          (reset! atoms/draw-time-accumulator 0)
          (reset! atoms/update-time-accumulator 0)
          (reset! atoms/update-count 0)
          (reset! atoms/max-flapper-count 0)
          (reset! atoms/flapper-make-time-accumulator 0)
          (reset! atoms/flapper-make-count 0)
          (reset! atoms/flapper-update-time-accumulator 0)
          (reset! atoms/flapper-update-count 0)
          (reset! atoms/render-background-time-accumulator 0)
          (reset! atoms/render-header-time-accumulator 0)
          (reset! atoms/render-lines-time-accumulator 0)
          (reset! atoms/render-lines-rerender-time-accumulator 0)
          (reset! atoms/render-lines-rerender-count 0)
          (reset! atoms/render-flappers-time-accumulator 0)
          (reset! atoms/render-lines-char-count-accumulator 0)
          (reset! atoms/render-lines-rect-count-accumulator 0)
          (reset! atoms/render-lines-text-count-accumulator 0)
          (reset! atoms/render-flapper-text-count-accumulator 0)
          (reset! frame-count 0))))
    (catch Exception e
      (core-utils/log :error e)
      state)))

(defn on-close [_]
  (q/no-loop)
  (q/exit)                                                  ; Exit the sketch
  (core-utils/log :status "Skill Board closed.")
  (System/exit 0))

(defn key-released [state event]
  (condp = (:key event)
    :p (reset! atoms/poll-key true)
    :space (reset! atoms/change-screen? true)
    nil)
  state)

(declare skillBoard)

(defn -main [& args]
  (core-utils/log :status (str "skillBoard v" config/version " has begun."))
  (let [args (set args)
        window? (some? (args "-w"))
        _ (reset! atoms/test? (some? (args "-t")))
        _ (reset! atoms/log-stdout? (nil? (args "--no-stdout")))
        _ (when @atoms/test? (spit "profile.log" ""))
        _ (core-utils/log :status (str "args: " args ", window? " window? ", test? " @atoms/test?))
        ]
    (q/defsketch skillBoard
                 :title "Skill Board"
                 :size :fullscreen
                 :setup setup
                 :update update-state
                 :draw draw-state
                 :features (if window? [] [:present])
                 :middleware [m/fun-mode]
                 :on-close on-close
                 :key-released key-released
                 :host "skillBoard")))
