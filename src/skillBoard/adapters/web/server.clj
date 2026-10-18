(ns skillBoard.adapters.web.server
  (:require
    [clojure.data.json :as json]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [skillBoard.adapters.web.board :as board]
    [skillBoard.adapters.web.view :as view])
  (:import
    [com.sun.net.httpserver HttpExchange HttpHandler HttpServer]
    [java.net InetSocketAddress]))

(def ^:private view-cache (atom {:key nil :json nil}))
(def ^:private view-lock (Object.))

(def ^:private files
  {"/" "resources/web/board.html"
   "/board.js" "resources/web/board.js"
   "/board.css" "resources/web/board.css"
   "/logo.png" "resources/flightlogo.png"
   "/fonts/skyfont.otf" "fonts/Skyfont_Font_Non-Commercial/Skyfont-NonCommercial.otf"
   "/fonts/bahnschrift.ttf" "fonts/Bahnschrift-master/bahnschrift.ttf"
   "/fonts/dseg7.ttf" "fonts/fonts-DSEG_v046/DSEG7-Modern/DSEG7Modern-Regular.ttf"})

(def ^:private content-types
  {".js" "text/javascript; charset=utf-8"
   ".css" "text/css; charset=utf-8"
   ".png" "image/png"
   ".otf" "font/otf"
   ".ttf" "font/ttf"})

(defn- content-type [path]
  (get content-types
       (some #(when (str/ends-with? path %) %) (keys content-types))
       "text/html; charset=utf-8"))

(defn- query-map [^HttpExchange exchange]
  (into {}
        (for [pair (some-> (.getRequestURI exchange) .getQuery (str/split #"&"))
              :when (str/includes? pair "=")]
          (let [[k v] (str/split pair #"=" 2)]
            [(keyword k) v]))))

(defn- long-param [params key default]
  (try
    (let [n (Long/parseLong (str (get params key default)))]
      (if (pos? n) n default))
    (catch Exception _
      default)))

(defn- request-text [^HttpExchange exchange]
  (slurp (.getRequestBody exchange) :encoding "UTF-8"))

(defn- respond [^HttpExchange exchange status type body]
  (let [bytes (.getBytes (str body) "UTF-8")
        headers (.getResponseHeaders exchange)]
    (.set headers "Content-Type" type)
    (.sendResponseHeaders exchange (int status) (alength bytes))
    (with-open [out (.getResponseBody exchange)]
      (.write out bytes))))

(defn- respond-empty [^HttpExchange exchange status]
  (.sendResponseHeaders exchange (int status) -1)
  (.close (.getResponseBody exchange)))

(defn- send-path [^HttpExchange exchange path]
  (let [file (io/file (files path))]
    (if (.isFile file)
      (let [bytes (java.nio.file.Files/readAllBytes (.toPath file))
            headers (.getResponseHeaders exchange)]
        (.set headers "Content-Type" (content-type path))
        (.sendResponseHeaders exchange 200 (alength bytes))
        (with-open [out (.getResponseBody exchange)]
          (.write out bytes)))
      (respond exchange 404 "text/plain; charset=utf-8" "not found"))))

(defn- read-edn [text]
  (edn/read-string text))

(defn- poll-command? [command]
  (and (map? command) (#{"p" :p} (:key command))))

(defn- board-json [snapshot rows width height]
  (locking view-lock
    (let [key [snapshot rows width height]
          cached @view-cache]
      (if (= key (:key cached))
        (:json cached)
        (let [rendered (json/write-str (view/board-view snapshot rows width height))]
          (reset! view-cache {:key key :json rendered})
          rendered)))))

(defn- handle-snapshot [state exchange]
  (try
    (swap! state board/store-snapshot (read-edn (request-text exchange)))
    (respond-empty exchange 204)
    (catch Exception _
      (respond exchange 400 "text/plain; charset=utf-8" "bad snapshot"))))

(defn- handle-command [state exchange]
  (let [command (try (read-edn (request-text exchange)) (catch Exception _ {}))]
    (when (poll-command? command)
      (swap! state board/request-poll))
    (respond-empty exchange 204)))

(defn- handle-take-command [state exchange]
  (let [taken (atom nil)]
    (swap! state (fn [current]
                   (let [result (board/take-poll current)]
                     (reset! taken result)
                     (:board result))))
    (respond exchange 200 "application/json; charset=utf-8"
             (json/write-str {:poll (true? (:poll? @taken))}))))

(defn- handle-board [state exchange]
  (let [params (query-map exchange)]
    (respond exchange 200 "application/json; charset=utf-8"
             (board-json (:snapshot @state)
                         (long-param params :rows 24)
                         (long-param params :width 1280)
                         (long-param params :height 720)))))

(def ^:private routes
  {["POST" "/snapshot"] handle-snapshot
   ["POST" "/command"] handle-command
   ["GET" "/command"] handle-take-command
   ["GET" "/board"] handle-board})

(defn- file-request? [method path]
  (and (= method "GET") (contains? files path)))

(defn- dispatch-file [^HttpExchange exchange path method]
  (if (file-request? method path)
    (send-path exchange path)
    (respond exchange 404 "text/plain; charset=utf-8" "not found")))

(defn- dispatch [state ^HttpExchange exchange]
  (let [path (.getPath (.getRequestURI exchange))
        method (.getRequestMethod exchange)
        handler (get routes [method path])]
    (if handler
      (handler state exchange)
      (dispatch-file exchange path method))))

(defn- handler [state]
  (reify HttpHandler
    (handle [_ exchange]
      (try
        (dispatch state exchange)
        (catch Exception e
          (respond exchange 500 "text/plain; charset=utf-8" (.getMessage e)))))))

(defn board [info]
  @(:state info))

(defn start!
  ([port] (start! port (atom (board/empty-board))))
  ([port state]
   (let [address (InetSocketAddress. "0.0.0.0" (int port))
         server (HttpServer/create address 0)]
     (.createContext server "/" (handler state))
     (.start server)
     {:server server
      :port (.getPort (.getAddress server))
      :state state})))

(defn stop! [{:keys [server]}]
  (when server
    (.stop server 0)))

(defn -main [& _]
  (let [info (start! board/port)]
    (println (str "Skill Board web http://127.0.0.1:" (:port info)))
    @(promise)))
