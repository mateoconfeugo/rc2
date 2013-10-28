(ns rc2.lib.descriptor.delta
  (:require [clojure.core.typed :as type]
            [rc2.lib
             [math :as math]
             [robot :as robot]
             [position :as pos]])
  (:import [clojure.lang IPersistentMap IPersistentVector Keyword]))

(declare reachable? find-angles)

;; Descriptors for delta-style robots.
(type/ann-record DeltaPose [angles :- (IPersistentMap Keyword Number)])
(defrecord DeltaPose [angles]
  robot/RobotPose
  (joint-angles [pose] (:angles pose)))

(type/ann-record DeltaDescriptor [upper :- Number
                                  lower :- Number
                                  effector :- Number
                                  base :- Number])
(defrecord DeltaDescriptor [upper lower effector base]
  robot/RobotDescriptor
  (find-pose [descriptor position] (->DeltaPose (find-angles descriptor position)))
  (reachable? [descriptor position] (reachable? descriptor position)))

(type/ann zero Number)
(def zero 0)
(type/ann two-thirds-pi Number)
(def two-thirds-pi (/ (* 2 Math/PI) 3))
(type/ann four-thirds-pi Number)
(def four-thirds-pi (/ (* 4 Math/PI) 3))

(type/ann reachable? [DeltaDescriptor pos/Vec -> Boolean])
(defn reachable? [descriptor position]
  "Test if 'position is reachable with an arm that matches 'descriptor."
  (let [{:keys [upper lower]} descriptor]
    (if (<= (pos/displacement position) (+ upper lower))
      true
      false)))

(type/ann inverse-3d [DeltaDescriptor pos/Vec -> Number])
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

(type/ann find-angles [DeltaDescriptor pos/Vec
                      -> (IPersistentMap Keyword Number)])
(defn- find-angles [descriptor position]
  (let [positions (map (partial pos/rotate position) [zero two-thirds-pi four-thirds-pi])
        servos [:a :b :c]]
    (into {}
          (map
           (type/fn> :- (Vector* Keyword Number) [servo :- Keyword position :- pos/Vec]
                     (vector servo (inverse-3d descriptor position))) servos positions))))
