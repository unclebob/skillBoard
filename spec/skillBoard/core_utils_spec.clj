(ns skillBoard.core-utils-spec
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [skillBoard.atoms :as atoms]
    [skillBoard.core-utils :as core-utils]
    [speclj.core :refer :all]))

(declare spit-calls)

(def timestamp-pattern #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{2} .+")

(defn- dated-log-file [suffix]
  (let [date (.format (java.time.LocalDate/now)
                      (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd"))]
    (str "logs/" date suffix)))

(describe "log"
  (with spit-calls (atom []))
  (it "writes status messages to the dated status log"
    (with-redefs [spit (fn [& args] (swap! @spit-calls conj args))
                  core-utils/prune-old-logs! (fn [])
                  print (fn [& _])]
      (core-utils/log :status "Setup...")
      (let [[file content] (first @@spit-calls)]
        (should= (dated-log-file "status.log") file)
        (should-contain "Setup..." content))))

  (it "writes error messages to the dated error log"
    (with-redefs [spit (fn [& args] (swap! @spit-calls conj args))
                  core-utils/prune-old-logs! (fn [])
                  print (fn [& _])]
      (core-utils/log :error "Something broke")
      (let [[file content] (first @@spit-calls)]
        (should= (dated-log-file "error.log") file)
        (should-contain "Something broke" content))))

  (it "writes exceptions to the dated error log with a timestamped header and stack trace"
    (with-redefs [spit (fn [& args] (swap! @spit-calls conj args))
                  core-utils/prune-old-logs! (fn [])
                  print (fn [& _])]
      (core-utils/log :error (Exception. "Boom"))
      (let [[file content] (first @@spit-calls)
            lines (str/split-lines content)]
        (should= (dated-log-file "error.log") file)
        (should-contain "Boom" content)
        (should (re-matches #".* -----" (first lines)))
        (should (re-matches timestamp-pattern (first lines)))
        (should-contain "java.lang.Exception: Boom" (second lines)))))

  (it "defaults to the dated status log with single arg"
    (with-redefs [spit (fn [& args] (swap! @spit-calls conj args))
                  core-utils/prune-old-logs! (fn [])
                  print (fn [& _])]
      (core-utils/log "hello")
      (let [[file content] (first @@spit-calls)]
        (should= (dated-log-file "status.log") file)
        (should-contain "hello" content))))

  (it "does not print when log-stdout? is false"
    (let [print-calls (atom [])]
      (with-redefs [spit (fn [& _])
                    core-utils/prune-old-logs! (fn [])
                    print (fn [& args] (swap! print-calls conj args))]
        (reset! atoms/log-stdout? false)
        (core-utils/log :status "silent")
        (should= [] @print-calls)
        (reset! atoms/log-stdout? true))))

  (it "prints when log-stdout? is true"
    (let [print-calls (atom [])]
      (with-redefs [spit (fn [& _])
                    core-utils/prune-old-logs! (fn [])
                    print (fn [& args] (swap! print-calls conj args))]
        (reset! atoms/log-stdout? true)
        (core-utils/log :status "loud")
        (should= 1 (count @print-calls)))))
  )

(describe "prune-old-logs!"
  (it "deletes dated status and error logs more than the retention period old"
    (let [temp-dir (.toFile (java.nio.file.Files/createTempDirectory "skillBoard-logs" (make-array java.nio.file.attribute.FileAttribute 0)))
          today (java.time.LocalDate/now)
          old-date (.format (.minusDays today 61) (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd"))
          retained-date (.format (.minusDays today 60) (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd"))
          old-status (io/file temp-dir (str old-date "status.log"))
          old-error (io/file temp-dir (str old-date "error.log"))
          retained-status (io/file temp-dir (str retained-date "status.log"))
          unrelated (io/file temp-dir (str old-date "notes.log"))]
      (spit old-status "old")
      (spit old-error "old")
      (spit retained-status "retained")
      (spit unrelated "unrelated")
      (with-redefs [core-utils/log-directory (.getPath temp-dir)]
        (core-utils/prune-old-logs!))
      (should-not (.exists old-status))
      (should-not (.exists old-error))
      (should (.exists retained-status))
      (should (.exists unrelated)))))
