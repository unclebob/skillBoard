(ns skillBoard.presenters.airports
  (:require
    [skillBoard.comm-utils :as comm]
    [skillBoard.presenters.utils :as utils]))

(defn make-flight-category-line [metar]
  (let [{:keys [fltCat icaoId visib cover clouds wspd wgst]} metar
        base (if (= cover "CLR")
               "    "
               (:base (first clouds)))
        fltCat (if (nil? fltCat) "    " fltCat)
        cover (if (nil? cover) "   " cover)
        base (if (nil? base) "     " base)
        wgst (if (nil? wgst) "   " (str "G" wgst))
        ctgy-line (format "%4s %4s %3s %5s %3s %2s%3s" icaoId fltCat cover base visib wspd wgst)]
    {:line ctgy-line :color :white}))

(defn make-flight-category-screen []
  (let [metars (vals @comm/polled-metars)
        metars (sort utils/by-distance metars)
        fc-lines (map make-flight-category-line metars)]
    fc-lines))