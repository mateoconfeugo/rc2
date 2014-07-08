(ns rc2.lib.planner
  (:use [clojure.tools.trace])
  (:require [clojure.math.combinatorics :as combo]
            [rc2.lib.position :as pos]
            [schema.core :as s]))

(def Point [s/Num])
(def SourceMap {(s/named s/Keyword "part name") Point})
(def PartDef [(s/one s/Keyword "part name") Point])
(def PartList [PartDef])


(s/defn ->source :- [Point] [sources :- SourceMap part :- PartDef]
  "Return a vector from the source to the sink."
  (when-let [source-location (get sources (first part))]
    (vector source-location (second part))))

(s/defn map-sinks-to-sources [sources :- SourceMap sinks :- PartList]
  "Map sinks to their component sources."
  (map (partial ->source sources) sinks))

(s/defn path-length :- Number [path :- [Point]]
  "Find the length of path, accounting for distance between each source and destination."
  (reduce + (map (fn [a b] (pos/displacement a b)) path (rest path))))

(s/defn optimize-brute-force :- PartList [sources :- SourceMap sinks :- PartList]
  "Pick the permutation of the destination list which minimizes travel distance."
  (println "Using brute force algorithm to plan.")
  (let [possible-paths (map (comp (partial apply concat) (partial map-sinks-to-sources sources))
                            (combo/permutations sinks))]
    (->> possible-paths
        (map (fn [path] {:path path :length (path-length path)}))
        (reduce (fn [a b] (if (< (:length a) (:length b)) a b)))
        :path)))

;; In the branch and bound formulation here, each node in the tree represents a source->sink
;; traversal. The cost of a given path is given by the length of all of the source->sink segments
;; and the sink->source segments.
(defn optimize-bounded-internal [current nodes min]
  (if (empty? nodes)
    [current (path-length current)]
    (reduce (fn [cur-min next]
              ;; (println "Next node =" next)
              (let [new-path (into current next)
                    new-len (path-length new-path)
                    new-nodes (remove #(= next %) nodes)
                    [cur-min-path cur-min-len] cur-min]
                (if (< new-len cur-min-len)
                  (do ;;(println "Path" path "is shortest so far (" len "vs" cur-min-len ")")
                     (optimize-bounded-internal new-path new-nodes cur-min))
                  cur-min)))
            min nodes)))

(s/defn optimize-bounded :- PartList [sources :- SourceMap sinks :- PartList]
  "Find an optimal path that fills all sinks from the correct sources, using branch and bound."
  (println "Using branch and bound algorithm to plan.")
  (let [nodes (map-sinks-to-sources sources sinks) ;; Nodes is the available source->sink vectors.
        [path len] (optimize-bounded-internal [] nodes [[] (Integer/MAX_VALUE)])]
    (println "Found path" path "of length" len)
    path))

(s/defn plan-pick-and-place :- [Point] [sources :- SourceMap sinks :- PartList]
  "Plan a pick and place sequence to populate the positions in sinks from sources."
  ;; (optimize-brute-force sources sinks)
  (optimize-bounded sources sinks))
