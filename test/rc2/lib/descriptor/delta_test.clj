(ns rc2.lib.descriptor.delta_test
  (:require [speclj.core :refer :all]
            [rc2.lib
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

(defn- within? [tolerance a1 a2]
  "Tests whether a value is within tolerance of another."
  (> tolerance (math/abs (- a1 a2))))

(defn- pose-within? [tolerance p1 p2]
  "Tests whether a pose is within tolerance of another"
  (every? true?
          (map (fn [a b] (> tolerance (math/abs (- a b))))
               (vals (robot/joint-angles p1)) (vals (robot/joint-angles p2)))))

(describe
 "reachable?"
 (it "a point that is half the arm's distance away is reachable"
     (should= true (delta/reachable? test-descriptor (pos/->vec upper 0 0))))
 (it "a point 3 units more distant than the arm's length is unreachable"
     (should= false
              (delta/reachable? test-descriptor (pos/->vec (+ upper lower 3) 0 0)))))

(describe
 "inverse-3d"
 (it "when the arm is at (10,0,-10) it should point straight out"
     (should (within? angle-tolerance 0
                      (delta/inverse-3d test-descriptor (pos/->vec 10 0 -10)))))
 (it "when the arm is at (-10,0,-10) it should point straight down"
     (should (within? angle-tolerance (- (/ math/pi 2))
                      (delta/inverse-3d test-descriptor (pos/->vec -10 0 -10)))))
 (it "a point directly below the origin should form an angle"
     (should (within? angle-tolerance (- (/ math/pi 4))
                      (let [z-val (- (* (+ upper lower) (math/sin (/ math/pi 4))))]
                        (delta/inverse-3d test-descriptor (pos/->vec 0 0 z-val))))))
 (it "a point on the Z as far down as possible should point straight down"
     (should (within? angle-tolerance (- (/ math/pi 2))
                      (delta/inverse-3d test-descriptor (pos/->vec 0 0 (- 0 upper lower))))))
 (it "a point along the x axis should form an angle above the x axis"
     (should (within? angle-tolerance (/ math/pi 3)
                      (delta/inverse-3d test-descriptor (pos/->vec 10 0 0)))))
 (it "a point just below the x axis should form an angle above the x axis"
     (should (within? angle-tolerance (/ math/pi 6)
                      (delta/inverse-3d test-descriptor
                                        (pos/->vec (* 10 (math/sin (/ math/pi 3))) 0 (- 5))))))
 (it "when the base is longer than the end effector, the math still works"
     (should (within? angle-tolerance 0
                      (delta/inverse-3d (assoc test-descriptor :base (+ base 2))
                                        (pos/->vec 12 0 -10)))))
 (it "when the end effector is longer than the base, the math still works"
     (should (within? angle-tolerance 0
                      (delta/inverse-3d (assoc test-descriptor :effector (+ effector 2))
                                        (pos/->vec 8 0 -10))))))

(describe
 "joint-angles"
 (it "joint-angles should return the joint angles as a map"
     {:a 1 :b 2 :c 3} (robot/joint-angles (delta/->DeltaPose {:a 1 :b 2 :c 3} {}))
     (should= {:a 3 :b 2 :c 1} (robot/joint-angles (delta/->DeltaPose {:a 3 :b 2 :c 1} {})))))

(describe
 "find-pose"
 (it  "when positioned along the z axis, all arms angles are equal"
      (should (let [angle (/ math/pi 4)
                    z-val (- (* (+ upper lower) (math/sin angle)))]
                (pose-within? angle-tolerance
                              (delta/->DeltaPose {:a (- angle) :b (- angle) :c (- angle)} {})
                              (robot/find-pose test-descriptor (pos/->vec 0 0 z-val))))))
 (it "when positioned directly under arm A, B & C should be vertical"
     (should (let [angle (- (/ math/pi 2))]
               (pose-within? angle-tolerance (delta/->DeltaPose {:a 0 :b angle :c angle} {})
                             (robot/find-pose test-descriptor (pos/->vec 10 0 -10)))))))

(run-specs)
