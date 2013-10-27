(ns rc2.lib.planner)

(defn ->source [sources part]
  "Map a part to its source using locations provided in 'sources."
  (vector (get sources (first part)) (second part)))

(defn map-to-sources [sources destinations]
  "Map destinations to their component sources."
  (map (partial ->source sources) destinations))

(defn plan-pick-and-place [sources destinations]
  "Plan a pick and place sequence to populate the positions in 'destinations using components from
  'sources."
  ;; The basic algorithm goes like this:
  ;; 1. Join each destination point with the location of its component source.
  (let [source-paths (map-to-sources sources destinations)]
    ;; 2. Permute the point list to minimize the distance between a destination and the next source.
    ;; This is done using the following algorithm:
    ;; Compute distances between each destination and each source. Sort the points by these
    ;; distances, and select the lowest weighted path. Add the path to the path list. Select the
    ;; next lowest path - if it's from a destination that's already in the path list, skip it. If
    ;; not, add it to the path list. Repeat untill all destinations have been accounted for.
    (apply concat source-paths)))
