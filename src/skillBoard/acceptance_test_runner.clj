(ns skillBoard.acceptance-test-runner
  (:require [skillBoard.test-runner :as test-runner]))

(defn -main [& args]
  (test-runner/run-specs! test-runner/acceptance-spec-args args))
