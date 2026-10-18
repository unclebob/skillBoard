(ns skillBoard.adapters.web.process
  (:require
    [clojure.java.io :as io]
    [skillBoard.adapters.web.board :as board]
    [skillBoard.foundation.core-utils :as core-utils])
  (:import
    [java.lang ProcessBuilder$Redirect]
    [java.net Socket]))

(def port board/port)
(def ^:private child (atom nil))
(def ^:private hooked? (atom false))

(defn server-command [java-home classpath]
  [(str java-home "/bin/java")
   "-Djava.awt.headless=true"
   "-cp" classpath
   "clojure.main"
   "-m" "skillBoard.adapters.web.server"])

(defn start-needed? [listening?]
  (not listening?))

(defn listening? [port]
  (try
    (with-open [_ (Socket. "127.0.0.1" (int port))]
      true)
    (catch Exception _
      false)))

(defn stop! []
  (when-let [process @child]
    (.destroy process)
    (reset! child nil)))

(defn- hook-shutdown! []
  (when (compare-and-set! hooked? false true)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable (fn [] (stop!))))))

(defn- launch! []
  (io/make-parents (io/file "logs/web-server.log"))
  (let [command (server-command (System/getProperty "java.home")
                                (System/getProperty "java.class.path"))
        process (-> (ProcessBuilder. ^java.util.List command)
                    (.directory (io/file "."))
                    (.redirectErrorStream true)
                    (.redirectOutput (ProcessBuilder$Redirect/appendTo (io/file "logs/web-server.log")))
                    (.start))]
    (reset! child process)
    process))

(defn- start-child! []
  (when (start-needed? (listening? port))
    (launch!)))

(defn start! []
  (hook-shutdown!)
  (try
    (start-child!)
    (catch Exception e
      (core-utils/log :error e)
      nil)))
