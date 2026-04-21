(ns skillBoard.acceptance-generator-spec
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [skillBoard.acceptance-generator :as generator]
    [skillBoard.gherkin :as gherkin]
    [speclj.core :refer :all]))

(describe "Acceptance spec generator"
  (it "generates a Speclj spec from intermediate Gherkin json"
    (let [features-dir (io/file "target/acceptance-generator-spec/features")
          feature-file (io/file features-dir "sample.feature")
          json-file "target/acceptance-generator-spec/features.json"
          output-dir "target/acceptance-generator-spec/spec"
          _ (io/make-parents feature-file)
          _ (spit feature-file
                  (str "Feature: Generated coverage\n"
                       "  Background:\n"
                       "    Given a configured board\n"
                       "  Scenario Outline: Status lights\n"
                       "    Given a source has <failures> failures\n"
                       "    Then the color is <color>\n"
                       "    Examples:\n"
                       "      | failures | color |\n"
                       "      | 0        | green |\n"
                       "      | 4        | red   |\n"))
          _ (gherkin/write-json! (.getPath features-dir) json-file)
          generated-file (generator/generate! json-file output-dir)
          generated-text (slurp generated-file)]
      (should-contain "(ns skillBoard.generated-acceptance-spec" generated-text)
      (should-contain "Generated Gherkin acceptance coverage" generated-text)
      (should-contain "Status lights example 1" generated-text)
      (should-contain "Status lights example 2" generated-text)
      (should (str/includes? generated-text "should= 1"))
      (should (str/includes? generated-text "should= 2"))
      (with-open [reader (java.io.PushbackReader. (java.io.StringReader. generated-text))]
        (loop [forms-read 0]
          (let [form (read reader false ::eof)]
            (if (= ::eof form)
              (should (> forms-read 0))
              (recur (inc forms-read)))))))))
