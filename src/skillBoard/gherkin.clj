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
        parsed (reduce
                 (fn [state raw-line]
                   (let [line (str/trim (strip-comment raw-line))]
                     (cond
                       (:doc-string? state)
                       (if (= "\"\"\"" line)
                         (assoc state :doc-string? false)
                         (append-doc-line state raw-line))

                       (str/blank? line) state

                       (= "\"\"\"" line)
                       (assoc state :doc-string? true)

                       (keyword-line "Feature:" line)
                       (assoc-in state [:feature :name] (keyword-line "Feature:" line))

                       (= "Background:" line)
                       (assoc state :background? true :examples? false)

                       (keyword-line "Scenario Outline:" line)
                       (-> state
                           (update-in [:feature :scenarios]
                                      conj {:type "Scenario Outline"
                                            :name (keyword-line "Scenario Outline:" line)
                                            :steps []
                                            :examples []})
                           (assoc :background? false :examples? false))

                       (keyword-line "Scenario:" line)
                       (-> state
                           (update-in [:feature :scenarios]
                                      conj {:type "Scenario"
                                            :name (keyword-line "Scenario:" line)
                                            :steps []})
                           (assoc :background? false :examples? false))

                       (= "Examples:" line)
                       (assoc state :examples? true)

                       (table-row? line)
                       (add-table-row state (parse-table-row line))

                       (step-line line)
                       (-> state
                           (add-step (step-line line))
                           (assoc :examples? false))

                       (:name (:feature state))
                       (update-in state [:feature :description] conj line)

                       :else state)))
                 initial
                 lines)]
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
