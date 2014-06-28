(ns rc2.util-test
  (:require [specljs.core :as specljs]
            [rc2.util :as util])
  (:require-macros [specljs.core :refer [describe it should should= should-not]]))

(describe
 "distance"
 (it "calculates the cartesian distance between two points"
     (should= 2 (util/distance (util/->world 0 0) (util/->world 2 0)))
     (should= 2 (util/distance (util/->world 0 0) (util/->world 0 2)))
     (should= 5 (util/distance (util/->world 0 0) (util/->world 4 3)))))
