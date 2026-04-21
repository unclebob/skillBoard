(ns skillBoard.test-runner
  (:require
    [skillBoard.acceptance-generator :as acceptance-generator]
    [speclj.main :as speclj]))

(def default-spec-args ["-c" "spec" "target/generated-acceptance/spec"])

(def acceptance-spec-args ["-c" "target/generated-acceptance/spec"])

(defn run-specs! [spec-args extra-args]
  (acceptance-generator/generate-from-features!
    "features"
    "target/generated-acceptance/features.json"
    "target/generated-acceptance/spec")
  (apply speclj/-main (concat spec-args extra-args)))

(defn -main [& args]
  (run-specs! default-spec-args args))

(defn acceptance-main [& args]
  (run-specs! acceptance-spec-args args))
