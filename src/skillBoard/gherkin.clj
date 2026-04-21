(ns skillBoard.gherkin
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]))

(def step-keywords #{"Given" "When" "Then" "And" "But"})

(defn- strip-comment [line]
  (if (str/starts-with? (str/trim line) "#")
    ""
    line))

(defn- keyword-line [prefix line]
  (when (str/starts-with? line prefix)
    (str/trim (subs line (count prefix)))))

(defn- table-row? [line]
  (and (str/starts-with? line "|")
       (str/ends-with? line "|")))

(defn- parse-table-row [line]
  (->> (subs line 1 (dec (count line)))
       (#(str/split % #"\|"))
       (mapv str/trim)))

(defn- rows->maps [rows]
  (let [headers (map keyword (first rows))]
    (mapv #(zipmap headers %) (rest rows))))

(defn- step-line [line]
  (let [[head & tail] (str/split line #"\s+" 2)]
    (when (step-keywords head)
      {:keyword head
       :text (or (first tail) "")})))

(defn- add-step [state step]
  (let [step (assoc step :table [] :doc-string nil)]
    (if (:background? state)
      (-> state
          (update-in [:feature :background :steps] conj step)
          (assoc :last-step-path [:feature :background :steps]))
      (-> state
          (update-in [:feature :scenarios (dec (count (get-in state [:feature :scenarios]))) :steps] conj step)
          (assoc :last-step-path [:feature :scenarios (dec (count (get-in state [:feature :scenarios]))) :steps])))))

(defn- update-last-step [state f & args]
  (let [steps-path (:last-step-path state)
        index (dec (count (get-in state steps-path)))]
    (apply update-in state (conj steps-path index) f args)))

(defn- add-table-row [state row]
  (if (:examples? state)
    (update-in state
               [:feature :scenarios (dec (count (get-in state [:feature :scenarios]))) :examples]
               conj row)
    (update-last-step state update :table conj row)))

(defn- append-doc-line [state line]
  (update-last-step state update :doc-string #(str (when % (str % "\n")) line)))

(defn- complete-tables [feature]
  (let [complete-step (fn [step]
                        (if (seq (:table step))
                          (update step :table rows->maps)
                          (dissoc step :table)))
        complete-scenario (fn [scenario]
                            (let [scenario (update scenario :steps #(mapv complete-step %))]
                              (if (seq (:examples scenario))
                                (update scenario :examples rows->maps)
                                (dissoc scenario :examples))))]
    (-> feature
        (update-in [:background :steps] #(mapv complete-step %))
        (update :scenarios #(mapv complete-scenario %)))))

(defn- handle-feature [state line]
  (when-let [name (keyword-line "Feature:" line)]
    (assoc-in state [:feature :name] name)))

(defn- handle-background [state line]
  (when (= "Background:" line)
    (assoc state :background? true :examples? false)))

(defn- handle-scenario-outline [state line]
  (when-let [name (keyword-line "Scenario Outline:" line)]
    (-> state
        (update-in [:feature :scenarios]
                   conj {:type "Scenario Outline"
                         :name name
                         :steps []
                         :examples []})
        (assoc :background? false :examples? false))))

(defn- handle-scenario [state line]
  (when-let [name (keyword-line "Scenario:" line)]
    (-> state
        (update-in [:feature :scenarios]
                   conj {:type "Scenario"
                         :name name
                         :steps []})
        (assoc :background? false :examples? false))))

(defn- handle-examples [state line]
  (when (= "Examples:" line)
    (assoc state :examples? true)))

(defn- handle-table-row [state line]
  (when (table-row? line)
    (add-table-row state (parse-table-row line))))

(defn- handle-step [state line]
  (when-let [step (step-line line)]
    (-> state
        (add-step step)
        (assoc :examples? false))))

(defn- handle-description [state line]
  (when (:name (:feature state))
    (update-in state [:feature :description] conj line)))

(def ^:private normal-line-handlers
  [handle-feature
   handle-background
   handle-scenario-outline
   handle-scenario
   handle-examples
   handle-table-row
   handle-step
   handle-description])

(defn- handle-normal-line [state line]
  (or (some #(% state line) normal-line-handlers)
      state))

(defn- handle-doc-string-line [state raw-line line]
  (when (:doc-string? state)
    (if (= "\"\"\"" line)
      (assoc state :doc-string? false)
      (append-doc-line state raw-line))))

(defn- handle-blank-line [state line]
  (when (str/blank? line)
    state))

(defn- handle-doc-string-start [state line]
  (when (= "\"\"\"" line)
    (assoc state :doc-string? true)))

(defn- handle-line [state raw-line]
  (let [line (str/trim (strip-comment raw-line))]
    (or (handle-doc-string-line state raw-line line)
        (handle-blank-line state line)
        (handle-doc-string-start state line)
        (handle-normal-line state line))))

(defn parse-lines [source-name lines]
  (let [initial {:feature {:source source-name
                           :name nil
                           :description []
                           :background {:steps []}
                           :scenarios []}
                 :background? false
                 :examples? false
                 :doc-string? false
                 :last-step-path nil}
        parsed (reduce handle-line initial lines)]
    (complete-tables (:feature parsed))))

(defn parse-file [file]
  (with-open [reader (io/reader file)]
    (parse-lines (.getName (io/file file)) (line-seq reader))))

(defn feature-files [dir]
  (->> (file-seq (io/file dir))
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".feature"))
       (sort-by #(.getPath %))))

(defn parse-directory [dir]
  {:features (mapv parse-file (feature-files dir))})

(defn write-json! [features-dir output-file]
  (let [parsed (parse-directory features-dir)
        output (io/file output-file)]
    (io/make-parents output)
    (spit output (json/write-str parsed :escape-slash false))
    parsed))

(defn -main [& [features-dir output-file]]
  (let [features-dir (or features-dir "features")
        output-file (or output-file "target/generated-acceptance/features.json")]
    (write-json! features-dir output-file)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-04-21T10:34:08.150209-05:00", :module-hash "-1371001759", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-452458816"} {:id "def/step-keywords", :kind "def", :line 7, :end-line 7, :hash "1189497717"} {:id "defn-/strip-comment", :kind "defn-", :line 9, :end-line 12, :hash "1034256068"} {:id "defn-/keyword-line", :kind "defn-", :line 14, :end-line 16, :hash "-1824266139"} {:id "defn-/table-row?", :kind "defn-", :line 18, :end-line 20, :hash "-548048374"} {:id "defn-/parse-table-row", :kind "defn-", :line 22, :end-line 25, :hash "-2073316718"} {:id "defn-/rows->maps", :kind "defn-", :line 27, :end-line 29, :hash "-670050580"} {:id "defn-/step-line", :kind "defn-", :line 31, :end-line 35, :hash "2066150557"} {:id "defn-/add-step", :kind "defn-", :line 37, :end-line 45, :hash "1677743489"} {:id "defn-/update-last-step", :kind "defn-", :line 47, :end-line 50, :hash "1432647397"} {:id "defn-/add-table-row", :kind "defn-", :line 52, :end-line 57, :hash "465758338"} {:id "defn-/append-doc-line", :kind "defn-", :line 59, :end-line 60, :hash "1118049622"} {:id "defn-/complete-tables", :kind "defn-", :line 62, :end-line 74, :hash "-158262990"} {:id "defn-/handle-feature", :kind "defn-", :line 76, :end-line 78, :hash "-1808025208"} {:id "defn-/handle-background", :kind "defn-", :line 80, :end-line 82, :hash "44800068"} {:id "defn-/handle-scenario-outline", :kind "defn-", :line 84, :end-line 92, :hash "-535562159"} {:id "defn-/handle-scenario", :kind "defn-", :line 94, :end-line 101, :hash "1374438572"} {:id "defn-/handle-examples", :kind "defn-", :line 103, :end-line 105, :hash "532668671"} {:id "defn-/handle-table-row", :kind "defn-", :line 107, :end-line 109, :hash "77085014"} {:id "defn-/handle-step", :kind "defn-", :line 111, :end-line 115, :hash "197336204"} {:id "defn-/handle-description", :kind "defn-", :line 117, :end-line 119, :hash "-1418026109"} {:id "def/normal-line-handlers", :kind "def", :line 121, :end-line 129, :hash "1782602293"} {:id "defn-/handle-normal-line", :kind "defn-", :line 131, :end-line 133, :hash "-530751171"} {:id "defn-/handle-doc-string-line", :kind "defn-", :line 135, :end-line 139, :hash "1274873153"} {:id "defn-/handle-blank-line", :kind "defn-", :line 141, :end-line 143, :hash "-1311835728"} {:id "defn-/handle-doc-string-start", :kind "defn-", :line 145, :end-line 147, :hash "1073163228"} {:id "defn-/handle-line", :kind "defn-", :line 149, :end-line 154, :hash "-460183351"} {:id "defn/parse-lines", :kind "defn", :line 156, :end-line 167, :hash "-466468127"} {:id "defn/parse-file", :kind "defn", :line 169, :end-line 171, :hash "1891061804"} {:id "defn/feature-files", :kind "defn", :line 173, :end-line 177, :hash "-1884510233"} {:id "defn/parse-directory", :kind "defn", :line 179, :end-line 180, :hash "-1330939833"} {:id "defn/write-json!", :kind "defn", :line 182, :end-line 187, :hash "599367913"} {:id "defn/-main", :kind "defn", :line 189, :end-line 192, :hash "2122743122"}]}
;; clj-mutate-manifest-end
