(ns rc2.util-test
  (:require [specljs.core :as specljs]
            [rc2.util :as util])
  (:require-macros [specljs.core :refer [describe it should should= should-not]]))

(describe
 "distance"
 (it "calculates the cartesian distance between two points"
     (should= 2 (util/distance nil (util/->world 0 0) (util/->world 2 0)))
     (should= 2 (util/distance nil (util/->world 0 0) (util/->world 0 2)))
     (should= 5 (util/distance nil (util/->world 0 0) (util/->world 4 3)))
     (should= 5 (util/distance nil (util/->world 0 0) (util/->world -4 -3)))))

(describe
 "scale-to"
 (it "scales a vector to the desired magnitude"
     (should= 2 (.ceil js/Math (util/distance nil (util/scale-to nil (util/->world 1 1) 2)))))
 (it "preserves the direction of the provided vector"
     (should= (util/->world -1 0) (util/scale-to nil (util/->world -1 0) 1)))
 (it "doesn't die on 0 length"
     (should= (util/->world 0 0) (util/scale-to nil (util/->world 10 10) 0))))
