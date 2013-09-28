(ns rc2.lib.descriptor.delta_test
  (:use midje.sweet
        rc2.lib.descriptor.delta
        rc2.lib.position))

(def upper 10)
(def lower 10)
(def effector 3)
(def base 3)
(def descriptor (delta-descriptor upper lower effector base))

(facts "About reachable?"
  (fact "A point that is half the arm's distance away is reachable"
   (reachable? descriptor (point upper 0 0)) => true)
  (fact "A point 3 units more distant than the arm's length is unreachable"
   (reachable? descriptor (point (+ upper lower 3) 0 0)) => false))
