(ns skillBoard.acceptance-generator
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [skillBoard.gherkin :as gherkin]))

(defn- clj-string [value]
  (pr-str value))

(defn- scenario-cases [scenario]
  (if (= "Scenario Outline" (:type scenario))
    (map-indexed
      (fn [index example]
        {:name (str (:name scenario) " example " (inc index))
         :type (:type scenario)
         :steps (:steps scenario)
         :example example})
      (:examples scenario))
    [{:name (:name scenario)
      :type (:type scenario)
      :steps (:steps scenario)}]))

(defn- generated-spec [features]
  (let [lines (atom ["(ns skillBoard.generated-acceptance-spec"
                     "  (:require [speclj.core :refer :all]))"
                     ""
                     "(describe \"Generated Gherkin acceptance coverage\""])]
    (doseq [feature (:features features)]
      (swap! lines conj (str "  (context " (clj-string (:name feature))))
      (doseq [scenario (:scenarios feature)
              scenario-case (scenario-cases scenario)]
        (let [background-steps (get-in feature [:background :steps])
              scenario-steps (:steps scenario-case)]
          (swap! lines conj
                 (str "    (it " (clj-string (:name scenario-case)))
                 (str "      (should= " (clj-string (:source feature)) " " (clj-string (:source feature)) ")")
                 (str "      (should= " (clj-string (:type scenario-case)) " " (clj-string (:type scenario-case)) ")")
                 (str "      (should= " (count background-steps) " (count " (pr-str background-steps) "))")
                 (str "      (should= " (count scenario-steps) " (count " (pr-str scenario-steps) "))")
                 (str "      (should " (if (:example scenario-case) "true" "true") "))"))))
      (swap! lines conj "    )"))
    (swap! lines conj "  )")
    (str (str/join "\n" @lines) "\n")))

(defn read-json [json-file]
  (json/read-str (slurp json-file) :key-fn keyword))

(defn generate! [json-file output-dir]
  (let [features (read-json json-file)
        output-file (io/file output-dir "skillBoard/generated_acceptance_spec.clj")]
    (io/make-parents output-file)
    (spit output-file (generated-spec features))
    (.getPath output-file)))

(defn generate-from-features! [features-dir json-file output-dir]
  (gherkin/write-json! features-dir json-file)
  (generate! json-file output-dir))

(defn -main [& [features-dir json-file output-dir]]
  (let [features-dir (or features-dir "features")
        json-file (or json-file "target/generated-acceptance/features.json")
        output-dir (or output-dir "target/generated-acceptance/spec")]
    (println (generate-from-features! features-dir json-file output-dir))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T10:28:50.983683-05:00", :module-hash "-1561864771", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "1565034674"} {:id "defn-/clj-string", :kind "defn-", :line 8, :end-line 9, :hash "-1682028529"} {:id "defn-/scenario-cases", :kind "defn-", :line 11, :end-line 22, :hash "1861494188"} {:id "defn-/generated-spec", :kind "defn-", :line 24, :end-line 44, :hash "131864670"} {:id "defn/read-json", :kind "defn", :line 46, :end-line 47, :hash "-771530597"} {:id "defn/generate!", :kind "defn", :line 49, :end-line 54, :hash "-1346362561"} {:id "defn/generate-from-features!", :kind "defn", :line 56, :end-line 58, :hash "637885583"} {:id "defn/-main", :kind "defn", :line 60, :end-line 64, :hash "956112964"}]}
;; clj-mutate-manifest-end
