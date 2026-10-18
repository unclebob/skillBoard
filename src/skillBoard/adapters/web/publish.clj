(ns skillBoard.adapters.web.publish
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [skillBoard.adapters.web.board :as board]
    [skillBoard.foundation.atoms :as atoms]))

(def none ::none)
(def latest (atom nil))

(defn remember! [snapshot]
  (reset! latest snapshot)
  snapshot)

(defn changed? [previous current]
  (not= previous current))

(defn- accepted? [response status]
  (= status (:status response)))

(defn- post-snapshot! [port snapshot]
  (let [response (http/post (str "http://127.0.0.1:" port "/snapshot")
                            {:body (pr-str snapshot)
                             :content-type "application/edn"
                             :socket-timeout 2000
                             :connection-timeout 500
                             :throw-exceptions false})]
    (accepted? response 204)))

(defn- fetch-command [port]
  (let [response (http/get (str "http://127.0.0.1:" port "/command")
                           {:socket-timeout 2000
                            :connection-timeout 500
                            :throw-exceptions false})]
    (if (accepted? response 200)
      (json/read-str (:body response) :key-fn keyword)
      {})))

(defn exchange! [port previous current on-poll]
  (let [stored (if (and (changed? previous current)
                        (post-snapshot! port current))
                 current
                 previous)]
    (try
      (when (:poll (fetch-command port))
        (on-poll))
      (catch Exception _))
    stored))

(defn publish-once! [port previous]
  (let [current @latest]
    (if (nil? current)
      previous
      (try
        (exchange! port
                   previous
                   current
                   #(reset! atoms/poll-key true))
        (catch Exception _
          previous)))))

(defn start! []
  (future
    (loop [previous none]
      (let [stored (publish-once! board/port previous)]
        (Thread/sleep 1000)
        (recur stored)))))
