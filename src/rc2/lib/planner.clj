(ns rc2.lib.planner
  (:use [clojure.tools.trace])
  (:require [clojure.math.combinatorics :as combo]
            [rc2.lib.position :as pos]
            [schema.core :as s]))

(def Point [s/Number])
(def SourceMap {(s/named s/Keyword "part name") Point})
(def PartDef [(s/named s/Keyword "part name") Point])
(def PartList [PartDef])

(s/defn ->source :- [Point] [sources :- SourceMap part :- PartDef]
  "Returns a vector from the source providing the part to the final location of the part using
   locations provided in 'sources."
  (when-let [source-location (get sources (first part))]
    (vector source-location (second part))))

(s/defn map-to-sources [sources :- SourceMap dests :- PartList]
  "Map dests to their component sources."
  (map (partial ->source sources) dests))

(s/defn path-length :- Number [path :- [Point]]
  "Find the length of path, accounting for distance between each source and destination."
  (reduce + (map (fn [a b] (pos/displacement a b)) path (rest path))))

(s/defn optimize :- PartList [sources :- SourceMap dests :- PartList]
  "Pick the permutation of the destination list which minimizes travel distance."
  (let [possible-paths (map (comp (partial apply concat) (partial map-to-sources sources))
                            (combo/permutations dests))]
    (->> possible-paths
        (map (fn [path] {:path path :length (path-length path)}))
        (reduce (fn [a b] (if (< (:length a) (:length b)) a b)))
        :path)))

(s/defn plan-pick-and-place :- [Point] [sources :- SourceMap dests :- PartList]
  "Plan a pick and place sequence to populate the positions in 'dests using components from
  'sources."
  (optimize sources dests))
