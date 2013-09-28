(ns rc2.lib.descriptor.delta
  (:use rc2.lib.robot
        rc2.lib.position))

;; Descriptors for delta-style robots.

(defrecord DeltaDescriptor [upper lower effector base])

(declare is-reachable
         inverse-3d)

;; TODO Implement protocol here.
;; This will involve applying the inverse-3d function to each of the delta arms.
;; Rotate the target point around the z by 2pi/3 and calculate the effector angles.
;; TODO Unit test.
(extend-protocol RobotBehavior
  DeltaDescriptor
  (find-pose [descriptor position]
    (when (is-reachable descriptor position)
      ;; (apply PointCoordinate.
      ;;        (map inverse-3d ))
      )))

(defn is-reachable [descriptor position]
  (let [{:keys [upper lower]} descriptor]
    (if (<= (displacement position) (+ upper lower))
      true
      false)))

;; TODO Implement the inverse kinematics function
;; TODO Unit Test
(defn- inverse-3d [descriptor position])
