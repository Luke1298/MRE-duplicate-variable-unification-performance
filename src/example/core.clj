(ns example.core
  (:require
   [datomic.client.api :as d]
   [clojure.edn :as edn]))


(def fixed-cardinality [{:depth 1 :cardinality 100}
                        {:depth 2 :cardinality 100}
                        {:depth 3 :cardinality 100}
                        {:depth 4 :cardinality 100}
                        {:depth 5 :cardinality 100}
                        {:depth 6 :cardinality 100}
                        {:depth 7 :cardinality 100}
                        {:depth 8 :cardinality 100}
                        {:depth 9 :cardinality 100}
                        {:depth 10 :cardinality 100}])

(def fixed-depth [{:depth 2 :cardinality 50}
                  {:depth 2 :cardinality 100}
                  {:depth 2 :cardinality 150}
                  {:depth 2 :cardinality 200}
                  {:depth 2 :cardinality 250}
                  {:depth 2 :cardinality 300}
                  {:depth 2 :cardinality 350}
                  {:depth 2 :cardinality 400}
                  {:depth 2 :cardinality 450}
                  {:depth 2 :cardinality 500}
                  {:depth 2 :cardinality 550}
                  {:depth 2 :cardinality 600}
                  {:depth 2 :cardinality 650}])

(def default-test-suite [{:depth 1 :cardinality 10}
                         {:depth 2 :cardinality 10}
                         {:depth 3 :cardinality 10}
                         {:depth 4 :cardinality 10}
                         {:depth 5 :cardinality 10}
                         {:depth 1 :cardinality 100}
                         {:depth 2 :cardinality 100}
                         {:depth 3 :cardinality 100}
                         {:depth 4 :cardinality 100}
                         {:depth 5 :cardinality 100}
                         {:depth 1 :cardinality 500}
                         {:depth 2 :cardinality 500}
                         {:depth 3 :cardinality 500}
                         {:depth 4 :cardinality 500}
                         {:depth 5 :cardinality 500}])

(def RUN-TIMES 10)

(defn run-test [conn {:keys [depth cardinality]}]
  (let [all-artists (->> (d/q '[:find ?artist
                                :in $
                                :where [?artist :artist/name]]
                              (d/db conn))
                        (map first))
        _ (assert (> (count all-artists) cardinality) "The test is not suited for a cardinality of this size.")
        reduced-cardinality-artists (take cardinality all-artists)
        test-query (concat '[:find (count ?release)]
                            (concat [:in '$]
                                    (->> depth
                                         range
                                         (mapv (fn [d]
                                                 (vec (list (symbol (str "?d" d)) '...))))))
                            (concat [:where]
                                    (->> depth
                                         range
                                         (mapv (fn [d]
                                                 (vec (list '?release :release/artists (symbol (str "?d" d)))))))))
        start-time (System/currentTimeMillis)]
    (dotimes [n RUN-TIMES]
      (apply (partial d/q test-query (d/db conn)) (repeat depth reduced-cardinality-artists)))
    (let [total-time (- (System/currentTimeMillis) start-time)]
      (println (format "Average time (over %d runs) for depth = %2d and cardinality = %3d: %.3f ms" RUN-TIMES depth cardinality (float (/ total-time RUN-TIMES)))))))





(defn -main
  "Must specify a manifest file so that we know how to connect to the db etc,
   it should look like config/manifest.edn.example.
   Additional arguments can be provided to specify a particular performance test to run"
  ([manifest-file]
   (-main manifest-file nil))
  ([manifest-file test-suite-to-use]
   (let [test-suite (case test-suite-to-use
                      "fixed-cardinality" fixed-cardinality
                      "fixed-depth" fixed-depth
                      default-test-suite)
         manifest (-> manifest-file slurp edn/read-string)
         {:keys [db-name client-cfg]} manifest
         client (d/client client-cfg)]
     (d/create-database client {:db-name db-name})
     (let [conn (d/connect client {:db-name db-name})]
       (doseq [test! test-suite]
         (do
           (run-test conn test!)))))))
