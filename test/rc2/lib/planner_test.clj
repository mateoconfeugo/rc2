(ns rc2.lib.planner-test
  (:use midje.sweet
        rc2.lib.planner))

(def sources {:part-a [1 2 3], :part-b [4 5 6], :part-c [7 8 9]})
(def parts [[:part-b [6 5 4]] [:part-a [3 2 1]] [:part-a [3 2 1]]])

(facts "About ->source"
  (fact (->source sources [:part-a [3 2 1]]) => [[1 2 3] [3 2 1]])
  (fact (->source sources [:unknown [3 2 1]]) => nil))

(facts "About map-to-sources"
  (fact (map-to-sources sources [[:part-b [6 5 4]] [:part-a [3 2 1]] [:part-a [3 2 1]]])
    => [[[4 5 6] [6 5 4]] [[1 2 3] [3 2 1]] [[1 2 3] [3 2 1]]])
  (fact (map-to-sources sources [[:unknown [6 5 4]] [:part-a [3 2 1]]])
    => [nil [[1 2 3] [3 2 1]]]))

(facts "About path-length"
  (fact (path-length []) => 0)
  (fact (path-length [[1 1 1]]) => 0)
  (fact (path-length [[1 1 1] [1 1 1]]) => 0)
  (fact (path-length [[1 1 1] [1 1 2]]) => 1)
  (fact (path-length [[1 1 1] [1 1 2] [1 1 1]]) => 2))

(facts "About optimize"
  (fact (optimize sources parts) => [[1 2 3] [3 2 1] [1 2 3] [3 2 1] [4 5 6] [6 5 4]]))
