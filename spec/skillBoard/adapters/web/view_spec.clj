(ns skillBoard.adapters.web.view-spec
  (:require
    [skillBoard.adapters.web.view :as view]
    [skillBoard.foundation.atoms :as atoms]
    [skillBoard.foundation.config :as config]
    [skillBoard.presenters.main :as main]
    [speclj.core :refer :all]))

(def wind-grid
  {:source :open-meteo-gfs-hrrr
   :generated-at-ms 1
   :center [42.4221486 -87.8679161]
   :radius-nm 200
   :points [{:lat 42.42 :lon -87.87 :u 10.0 :v 0.0}]})

(describe "board view"
  (around [it]
    (let [screens @config/screens
          screen-type @main/screen-type
          logging @atoms/log-traffic?
          info @config/display-info]
      (try
        (it)
        (finally
          (reset! config/screens screens)
          (reset! main/screen-type screen-type)
          (reset! atoms/log-traffic? logging)
          (reset! config/display-info info)))))

  (it "builds every screen without moving the wall display"
    (let [screen-type @main/screen-type]
      (swap! config/display-info assoc :line-count 11)
      (reset! atoms/log-traffic? true)
      (let [board (view/board-view {} 8 320 240)
            chars (get-in board [:flap :chars])]
        (should= "FLIGHT OPERATIONS" (get-in board [:screens :flights :header]))
        (should= "TIME" (get-in board [:screens :flights :columns 0 :label]))
        (should= (apply str (repeat 64 \space)) (get-in board [:screens :flights :lines 0 :text]))
        (should= [255 255 255] (get-in board [:screens :flights :lines 0 :rgb]))
        (should= "WEATHER" (get-in board [:screens :taf :header]))
        (should= "NEARBY TRAFFIC" (get-in board [:screens :traffic :header]))
        (should= "FLIGHT CATEGORY" (get-in board [:screens :airports :header]))
        (should= "SURFACE WINDS" (get-in board [:screens :wind-map :header]))
        (should= "flights" (get-in board [:sequence 0 :screen]))
        (should= 40 (get-in board [:sequence 0 :duration]))
        (should= 42 (count chars))
        (should= "A" (first chars))
        (should= 33 (get-in board [:flap :frame-ms]))
        (should= true @atoms/log-traffic?)
        (should= screen-type @main/screen-type)
        (should= 11 (:line-count @config/display-info)))))

  (it "describes the wind map from the snapshot"
    (let [board (view/board-view {:wind-grid wind-grid
                                  :test? true
                                  :aircraft ["N345TS" "N378MA"]
                                  :metars {"KUGN" {:rawOb "KUGN 000000Z 00000KT 10SM BKN025"
                                                   :fltCat "VFR"
                                                   :lat 42.42
                                                   :lon -87.87
                                                   :icaoId "KUGN"
                                                   :clouds [{:cover "BKN" :base 2500}]}
                                           "KXXX" {:lat 43.0
                                                   :fltCat "IFR"
                                                   :icaoId "KXXX"}}}
                                 8 320 240)
          scene (get-in board [:screens :wind-map :scene])
          labels (set (map :label (:fleet scene)))
          cells (:ceilings scene)
          placed (first (filter #(and (pos? (:x %)) (pos? (:y %))) cells))]
      (should= 80 (get-in scene [:field :cols]))
      (should= 50 (get-in scene [:field :rows]))
      (should-contain "N345TS" (str labels))
      (should-not-contain "N378MA" (str labels))
      (should-contain "Radius: 200 NM" (get-in scene [:source :text]))
      (should= true (some? (:stale scene)))
      (should= 1 (count (:markers scene)))
      (should-be-nil (:label (first (:markers scene))))
      (should= 864 (count cells))
      (should= 80/9 (:x placed))
      (should= 10 (:y placed))
      (should= 33 (get-in scene [:motion :frame-ms]))
      (should (> (:x (first (:fleet scene)))
                 (ffirst (:triangle (first (:fleet scene))))))
      (should= 160.0 (get-in scene [:circle :label :x]))
      (should= 18.0 (get-in scene [:circle :label :y]))
      (should= 76.0 (get-in scene [:scales :wind :title :y]))
      (should= 13.333333333333343 (get-in scene [:scales :wind :bands 0 :h]))
      (should= 153.33333333333331 (get-in scene [:scales :wind :bands 0 :label-y]))))

  (it "uses a one pixel board when the requested size is not positive"
    (let [board (view/board-view {} 8 0 -3)]
      (should= 1 (:width board))
      (should= 1 (:height board)))))
