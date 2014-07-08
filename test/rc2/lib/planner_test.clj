(ns rc2.lib.planner-test
  (:require [speclj.core :refer :all]
            [rc2.lib.planner :refer :all]))

(def sources {:part-a [1 2 3], :part-b [4 5 6], :part-c [7 8 9]})
(def parts [[:part-b [6 5 4]] [:part-a [3 2 1]] [:part-b [0 0 0]] [:part-a [20 20 20]]])
(def optimal-path [[4 5 6] [6 5 4] [4 5 6] [0 0 0] [1 2 3] [3 2 1] [1 2 3] [20 20 20]])

(describe
 "->source"
 (it "returns a pair of the part location and source location"
     (should== [[1 2 3] [3 2 1]] (->source sources [:part-a [3 2 1]])))
 (it "returns nil for unknown parts" (should= nil (->source sources [:unknown [3 2 1]]))))

(describe
 "map-sinks-to-sources"
 (it "returns a list of pairs of part locations and source locations"
  (should== [[[4 5 6] [6 5 4]] [[1 2 3] [3 2 1]] [[1 2 3] [3 2 1]]]
            (map-sinks-to-sources sources
                                  [[:part-b [6 5 4]] [:part-a [3 2 1]] [:part-a [3 2 1]]])))
 (it "returns nil for unknown parts"
     (should= [nil [[1 2 3] [3 2 1]]]
              (map-sinks-to-sources sources [[:unknown [6 5 4]] [:part-a [3 2 1]]]))))

(describe
 "path-length"
 (it "returns 0 for the empty path" (should= 0 (path-length [])))
 (it "returns 0 for a 1-element path" (should= 0 (path-length [[1 1 1]])))
 (it "returns 0 for a path from a->a" (should= 0 (path-length [[1 1 1] [1 1 1]])))
 (it "returns 1 for a path of length 1" (should= 1 (path-length [[1 1 1] [1 1 2]])))
 (it "returns 2 for a round trip on a path of length 1"
     (should= 2 (path-length [[1 1 1] [1 1 2] [1 1 1]]))))

(describe
 "optimize-brute-force"
 (it "returns an optimal path for the parts & sources configuration provided"
     (should== optimal-path (optimize-brute-force sources parts))))

(describe
 "optimize-bounded"
 (it "returns an optimal path for the parts & sources configuration provided"
     (should== optimal-path (optimize-bounded sources parts))))

(describe
 "optimize-greedy"
 (it "returns a path for the parts & sources configuration provided"
     ;; TODO Figure out a better test for this algorithm
     (should= (count optimal-path) (count (optimize-greedy sources parts)))))

(describe
 "plan-pick-and-place"
 (it "returns an optimal path for the parts & sources configuration provided"
     (should== optimal-path (plan-pick-and-place sources parts))))

(run-specs)
