(ns rc2.lib.descriptor.delta_test
  (:use clojure.test
        rc2.lib.descriptor.delta
        rc2.lib.position))

(def upper 10)
(def lower 10)
(def effector 3)
(def base 3)
(def descriptor (delta-descriptor upper lower effector base))

(deftest test-reachable?
  (testing "reachable? should return false if the point is out of range and true otherwise"
    (is (reachable? descriptor (point upper 0 0)))
    (is (not (reachable? descriptor (point (+ upper lower 3) 0 0))))))
