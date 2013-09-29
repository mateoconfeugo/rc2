(ns rc2.lib.descriptor.delta_test
  (:use clojure.test
        midje.sweet)
  (:require [rc2.lib [math :as math]
             [position :as pos]
             [robot :as robot]]
            [rc2.lib.descriptor.delta :as delta]))

(def upper 10)
(def lower 10)
(def effector 2)
(def base 2)
(def test-descriptor (delta/->DeltaDescriptor upper lower effector base))

(def angle-tolerance 1e-14)

(defn- within? [tolerance a1]
  "Midje checker to test whether a value is within tolerance of another"
  (fn [a2] (> tolerance (math/abs (- a1 a2)))))

(defn- pose-within? [tolerance p1]
  "Midje checker to test whether a pose is within tolerance of another"
  (fn [p2] (every? true?
                   (map (fn [a b] (> tolerance (math/abs (- a b))))
                        (vals p1) (vals p2)))))

(facts "About reachable?"
  (fact "A point that is half the arm's distance away is reachable"
   (delta/reachable? test-descriptor (pos/point upper 0 0)) => true)
  (fact "A point 3 units more distant than the arm's length is unreachable"
   (delta/reachable? test-descriptor (pos/point (+ upper lower 3) 0 0)) => false))

(facts "About inverse-3d"
  (fact "When the arm is given (10,0,-10) it should point straight out"
    (delta/inverse-3d test-descriptor (pos/point 10 0 -10)) => (within? angle-tolerance 0))
  (fact "When the arm is given (-10,0,-10) it should point straight down"
    (delta/inverse-3d test-descriptor (pos/point -10 0 -10)) => (within? angle-tolerance (- (/ math/pi 2))))
  (fact "When the arm is given a point directly below the origin, it should form an angle"
    (delta/inverse-3d test-descriptor (pos/point 0 0 (- (* (+ upper lower) (math/sin (/ math/pi 4))))))
    => (within? angle-tolerance (- (/ math/pi 4))))
  (fact "When the arm is given a point below as far down as it can reach, it should point straight down."
    (delta/inverse-3d test-descriptor (pos/point 0 0 (- 0 upper lower)))
    => (within? angle-tolerance (- (/ math/pi 2))))
  (fact "When the arm is given a point along the x axis, it should form an angle above the x axis."
    (delta/inverse-3d test-descriptor (pos/point 10 0 0)) => (within? angle-tolerance (/ math/pi 3)))
  (fact "When the arm is given a point just below the x axis, it should form an angle above the x axis."
    (delta/inverse-3d test-descriptor (pos/point (* 10 (math/sin (/ math/pi 3))) 0 (- 5)))
    => (within? angle-tolerance (/ math/pi 6))))

(facts "About find-pose"
  (fact "When the effector is positioned along the z axis, all of the arms should have the same angle"
    (let [angle (/ math/pi 4)]
      (robot/find-pose test-descriptor (pos/point 0 0 (- (* (+ upper lower) (math/sin angle)))))
      => (pose-within? angle-tolerance (delta/->DeltaPose (- angle) (- angle) (- angle)))))
  (fact "When the effector is positioned directly under arm A, the other two should be approximately vertical"
    (robot/find-pose test-descriptor (pos/point 10 0 -10))
    => (pose-within? angle-tolerance (delta/->DeltaPose 0 (- (/ math/pi 2)) (- (/ math/pi 2))))))
