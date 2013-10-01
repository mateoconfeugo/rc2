(ns rc2.lib.descriptor.delta_test
  (:use clojure.test
        midje.sweet)
  (:require [rc2.lib
             [math :as math]
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
                        (vals (robot/joint-angles p1)) (vals (robot/joint-angles p2))))))

(facts "About reachable?"
  (fact "A point that is half the arm's distance away is reachable"
   (delta/reachable? test-descriptor (pos/point upper 0 0)) => true)
  (fact "A point 3 units more distant than the arm's length is unreachable"
   (delta/reachable? test-descriptor (pos/point (+ upper lower 3) 0 0))
   => false))

(facts "About inverse-3d"
  (fact "When the arm is at (10,0,-10) it should point straight out"
    (delta/inverse-3d test-descriptor (pos/point 10 0 -10))
    => (within? angle-tolerance 0))
  (fact "When the arm is at (-10,0,-10) it should point straight down"
    (delta/inverse-3d test-descriptor (pos/point -10 0 -10))
    => (within? angle-tolerance (- (/ math/pi 2))))
  (fact "A point directly below the origin should form an angle"
    (let [z-val (- (* (+ upper lower) (math/sin (/ math/pi 4))))]
      (delta/inverse-3d test-descriptor (pos/point 0 0 z-val)))
    => (within? angle-tolerance (- (/ math/pi 4))))
  (fact "A point on the Z as far down as possible should point straight down."
    (delta/inverse-3d test-descriptor (pos/point 0 0 (- 0 upper lower)))
    => (within? angle-tolerance (- (/ math/pi 2))))
  (fact "A point along the x axis should form an angle above the x axis."
    (delta/inverse-3d test-descriptor (pos/point 10 0 0))
    => (within? angle-tolerance (/ math/pi 3)))
  (fact "A point just below the x axis should form an angle above the x axis."
    (delta/inverse-3d test-descriptor
                      (pos/point (* 10 (math/sin (/ math/pi 3))) 0 (- 5)))
    => (within? angle-tolerance (/ math/pi 6))))

(facts "About joint-angles"
  (fact "joint-angles should return the joint angles as a map"
    (robot/joint-angles (delta/->DeltaPose {:a 1 :b 2 :c 3})) => {:a 1 :b 2 :c 3}
    (robot/joint-angles (delta/->DeltaPose {:a 3 :b 2 :c 1})) => {:a 3 :b 2 :c 1}))

(facts "About find-pose"
  (fact "When positioned along the z axis, all arms angles are equal"
    (let [angle (/ math/pi 4)
          z-val (- (* (+ upper lower) (math/sin angle)))]
      (robot/find-pose test-descriptor (pos/point 0 0 z-val))
      => (pose-within? angle-tolerance
                       (delta/->DeltaPose {:a (- angle) :b (- angle) :c (- angle)}))))
  (fact "When positioned directly under arm A, B & C should be vertical"
    (robot/find-pose test-descriptor (pos/point 10 0 -10))
    => (let [angle (- (/ math/pi 2))]
         (pose-within? angle-tolerance (delta/->DeltaPose {:a 0 :b angle :c angle})))))
