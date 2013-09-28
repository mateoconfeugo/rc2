(ns rc2.lib.position
  (:require [rc2.lib.math :as math]))

(defrecord PointCoordinate [x y z])

(defn point [x y z]
  "Get a new point coordinate record for [x,y,z]."
  (PointCoordinate. x y z))

(def origin (point 0 0 0))

(defn displacement
  "Find the distance between two points"
  ([p] (displacement origin p))
  ([p1 p2] (let [dx (- (:x p1) (:x p2))
                 dy (- (:y p1) (:y p2))
                 dz (- (:z p1) (:z p2))]
               (math/sqrt (+ (math/square dx) (math/square dy) (math/square dz))))))

(defn rotate [position angle]
  "Rotate 'position around the Z axis by 'angle radians"
  (let [ct (math/cos angle)
        st (math/sin angle)
        {:keys [x y z]} position]
   (point
    (+ (* ct x) (* st y))
    (+ (* (- st) x) (* ct y))
    z)))

(defn within [dist p1 p2]
  (if (> dist (math/abs (displacement p1 p2)))
    true
    false))
