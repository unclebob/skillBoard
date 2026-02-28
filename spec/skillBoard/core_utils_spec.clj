(ns skillBoard.core-utils-spec
  (:require
    [skillBoard.atoms :as atoms]
    [skillBoard.core-utils :as core-utils]
    [speclj.core :refer :all]))

(declare spit-calls)
(describe "log"
  (with spit-calls (atom []))
  (it "writes status messages to status.log"
    (with-redefs [spit (fn [& args] (swap! @spit-calls conj args))
                  print (fn [& _])]
      (core-utils/log :status "Setup...")
      (let [[file content] (first @@spit-calls)]
        (should= "status.log" file)
        (should-contain "Setup..." content))))

  (it "writes error messages to error.log"
    (with-redefs [spit (fn [& args] (swap! @spit-calls conj args))
                  print (fn [& _])]
      (core-utils/log :error "Something broke")
      (let [[file content] (first @@spit-calls)]
        (should= "error.log" file)
        (should-contain "Something broke" content))))

  (it "writes exceptions to error.log with stack trace"
    (with-redefs [spit (fn [& args] (swap! @spit-calls conj args))
                  print (fn [& _])]
      (core-utils/log :error (Exception. "Boom"))
      (let [[file content] (first @@spit-calls)]
        (should= "error.log" file)
        (should-contain "Boom" content))))

  (it "defaults to status.log with single arg"
    (with-redefs [spit (fn [& args] (swap! @spit-calls conj args))
                  print (fn [& _])]
      (core-utils/log "hello")
      (let [[file content] (first @@spit-calls)]
        (should= "status.log" file)
        (should-contain "hello" content))))

  (it "does not print when log-stdout? is false"
    (let [print-calls (atom [])]
      (with-redefs [spit (fn [& _])
                    print (fn [& args] (swap! print-calls conj args))]
        (reset! atoms/log-stdout? false)
        (core-utils/log :status "silent")
        (should= [] @print-calls)
        (reset! atoms/log-stdout? true))))

  (it "prints when log-stdout? is true"
    (let [print-calls (atom [])]
      (with-redefs [spit (fn [& _])
                    print (fn [& args] (swap! print-calls conj args))]
        (reset! atoms/log-stdout? true)
        (core-utils/log :status "loud")
        (should= 1 (count @print-calls)))))
  )
