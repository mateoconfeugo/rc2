(ns rc2.lib.planner
  (:require [schema.core :as s]))

(def Point [s/Number])
(def SourceMap {(s/named s/Keyword "part name") Point})
(def PartList [[(s/named s/Keyword "part name") Point]])

(defn ->source [sources part]
  "Map a part to its source using locations provided in 'sources."
  (when-let [source-location (get sources (first part))]
    (vector source-location (second part))))

(s/defn map-to-sources [sources :- SourceMap
                        destinations :- [Point]]
  "Map destinations to their component sources."
  (map (partial ->source sources) destinations))

(defn plan-pick-and-place [sources destinations]
  "Plan a pick and place sequence to populate the positions in 'destinations using components from
  'sources."
  (let [source-paths (map-to-sources sources destinations)]
    (when (not-any? nil? source-paths)
      ;; TODO Optimize the path.
      (apply concat source-paths))))
