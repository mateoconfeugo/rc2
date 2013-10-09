(ns rc2.lib.descriptor.delta
  (:require [rc2.lib
             [math :as math]
             [robot :as robot]
             [position :as pos]]))

;; Descriptors for delta-style robots.

(defrecord DeltaDescriptor [upper lower effector base])
(defrecord DeltaPose [angles])

(def two-thirds-pi (/ (* 2 Math/PI) 3))
(def four-thirds-pi (/ (* 4 Math/PI) 3))

(defn reachable? [descriptor position]
  "Test if 'position is reachable with an arm that matches 'descriptor."
  (let [{:keys [upper lower]} descriptor]
    (if (<= (pos/displacement position) (+ upper lower))
      true
      false)))

(defn inverse-3d [descriptor position]
  "Determine the angle needed for a single arm in the xz plane with parameters in 'descriptor to
  reach 'position."
  (let [{:keys [upper lower effector base]} descriptor
        [x y z] position
        c (math/sqrt (+ (math/square z) (math/square (- (+ x effector) base))))
        a2 (- (math/square lower) (math/square y))
        alpha (math/acos
               (/ (- (+ (math/square upper) (math/square c)) a2)
                  (* 2 c upper)))
        beta (math/atan z (- (+ x effector) base))]
    (+ alpha beta)))

(defn- find-angles [descriptor position]
  (let [positions (map (partial pos/rotate position) [0 two-thirds-pi four-thirds-pi])
        servos [:a :b :c]]
    (into {} (map #(vector %1 (inverse-3d descriptor %2)) servos positions))))

(extend-protocol robot/RobotBehavior
  DeltaDescriptor
  (find-pose [descriptor position]
    (when (reachable? descriptor position)
      (->DeltaPose (find-angles descriptor position)))))

(extend-protocol robot/RobotPose
  DeltaPose
  (joint-angles [pose] (:angles pose)))
