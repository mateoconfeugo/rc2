(ns rc2.lib.descriptor.delta
  (:require [rc2.lib
             [math :as math]
             [robot :as robot]
             [position :as pos]]
            [schema.core :as s]))

(declare reachable? find-angles)

;; Descriptors for delta-style robots.
(s/defrecord DeltaPose [angles :- {s/Keyword s/Num} position :- {s/Keyword s/Num}]
  robot/RobotPose
  (joint-angles [pose] (:angles pose))
  (position [pose] (:position pose))
  Object
  (toString [pose] (str "{:angles " (:angles pose) " :position " (:position pose) "}")))

(s/defrecord DeltaDescriptor [upper :- s/Num lower :- s/Num effector :- s/Num base :- s/Num]
  robot/RobotDescriptor
  (find-pose [descriptor position] (->DeltaPose (find-angles descriptor position) position))
  (reachable? [descriptor position] (reachable? descriptor position)))

(def zero 0)
(def two-thirds-pi (/ (* 2 Math/PI) 3))
(def four-thirds-pi (/ (* 4 Math/PI) 3))

(s/defn reachable? :- s/Bool [descriptor :- DeltaDescriptor position :- pos/Vec]
  "Test if 'position is reachable with an arm that matches 'descriptor."
  (let [{:keys [upper lower]} descriptor]
    (if (<= (pos/displacement position) (+ upper lower))
      true
      false)))

(s/defn inverse-3d :- s/Num [descriptor :- DeltaDescriptor position :- pos/Vec]
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
  (let [positions (map (partial pos/rotate position) [zero two-thirds-pi four-thirds-pi])
        servos [:a :b :c]]
    (into {}
          (map
           (fn [servo position]
             (vector servo (inverse-3d descriptor position))) servos positions))))
