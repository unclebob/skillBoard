(ns skillBoard.gherkin-spec
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [skillBoard.gherkin :as gherkin]
    [speclj.core :refer :all]))

(describe "Gherkin parser"
  (it "parses a feature with background steps and scenarios"
    (let [feature (gherkin/parse-lines
                    "sample.feature"
                    ["Feature: Sample behavior"
                     "  Useful display behavior."
                     ""
                     "  Background:"
                     "    Given a configured display"
                     ""
                     "  Scenario: First case"
                     "    When it is built"
                     "    Then it shows useful data"])]
      (should= "sample.feature" (:source feature))
      (should= "Sample behavior" (:name feature))
      (should= ["Useful display behavior."] (:description feature))
      (should= [{:keyword "Given"
                 :text "a configured display"
                 :doc-string nil}]
               (get-in feature [:background :steps]))
      (should= "First case" (get-in feature [:scenarios 0 :name]))
      (should= ["When" "Then"] (map :keyword (get-in feature [:scenarios 0 :steps])))))

  (it "attaches tables and doc strings to the preceding step"
    (let [feature (gherkin/parse-lines
                    "tables.feature"
                    ["Feature: Rich steps"
                     "  Scenario: Tables and text"
                     "    Given these rows:"
                     "      | tail | distance_nm |"
                     "      | N1   | 5           |"
                     "    And this text:"
                     "      \"\"\""
                     "      TAF KORD 121130Z"
                     "      \"\"\""])]
      (should= [{:tail "N1" :distance_nm "5"}]
               (get-in feature [:scenarios 0 :steps 0 :table]))
      (should= "      TAF KORD 121130Z"
               (get-in feature [:scenarios 0 :steps 1 :doc-string]))))

  (it "parses scenario outline examples as row maps"
    (let [feature (gherkin/parse-lines
                    "outline.feature"
                    ["Feature: Outlines"
                     "  Scenario Outline: Colors"
                     "    Given a source has <failures> failures"
                     "    Then the light is <color>"
                     ""
                     "    Examples:"
                     "      | failures | color  |"
                     "      | 0        | green  |"
                     "      | 4        | red    |"])]
      (should= "Scenario Outline" (get-in feature [:scenarios 0 :type]))
      (should= [{:failures "0" :color "green"}
                {:failures "4" :color "red"}]
               (get-in feature [:scenarios 0 :examples]))))

  (it "writes parsed features to intermediate json"
    (let [features-dir (io/file "target/gherkin-spec/features")
          feature-file (io/file features-dir "sample.feature")
          json-file "target/gherkin-spec/features.json"]
      (io/make-parents feature-file)
      (spit feature-file "Feature: JSON\n  Scenario: One\n    When parsed\n    Then json exists\n")
      (gherkin/write-json! (.getPath features-dir) json-file)
      (should= "JSON"
               (get-in (json/read-str (slurp json-file) :key-fn keyword)
                       [:features 0 :name]))))

  (it "uses default paths from the command line entry point"
    (let [called (atom nil)]
      (with-redefs [gherkin/write-json! (fn [features-dir output-file]
                                          (reset! called [features-dir output-file]))]
        (gherkin/-main)
        (should= ["features" "target/generated-acceptance/features.json"] @called))))

  (it "uses supplied paths from the command line entry point"
    (let [called (atom nil)]
      (with-redefs [gherkin/write-json! (fn [features-dir output-file]
                                          (reset! called [features-dir output-file]))]
        (gherkin/-main "custom/features" "target/custom.json")
        (should= ["custom/features" "target/custom.json"] @called)))))
