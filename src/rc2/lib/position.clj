(ns rc2.lib.position
  (:use clojure.tools.trace)
  (:require [rc2.lib.math :as math]))

(defn point [x y z]
  "Get a new point coordinate record for [x,y,z]."
  [x y z])

(def origin (point 0 0 0))

(defn ->point-vector [p1 p2]
  "Compute the vector connecting p1 and p2."
  (map - p2 p1))

(defn vector-length [v]
  "Find the length of a vector."
  (math/sqrt (reduce + (map math/square v))))

(defn displacement
  "Find the distance between two points"
  ([p] (displacement origin p))
  ([p1 p2] (vector-length (->point-vector p1 p2))))

(defn rotate [position angle]
  "In three dimensions, rotate 'position around the Z axis by 'angle radians."
  (let [ct (math/cos angle)
        st (math/sin angle)
        [x y z] position]
   (point
    (+ (* ct x) (* st y))
    (+ (* (- st) x) (* ct y))
    z)))

(defn translate [p v]
  "Translate 'p along 'v."
  (map + p v))

(defn scale [n v]
  "Scale the vector 'v by 'n"
  (map (partial * n) v))

(defn within [dist p1 p2]
  "Test if 'p1 and 'p2 are within 'dist of each other."
  (if (> dist (math/abs (displacement p1 p2)))
    true
    false))

(defn ->unit-vector [v]
  "Convert the vector defined by two points (p1,p2) into a unit vector (i,j,k)"
  (map #(/ % (vector-length v)) v))

(defn interpolate [p1 p2 interval]
  "Create a set of points on the line between p1 and p2"
  (let [count (math/abs (/ (displacement p1 p2) interval))
        v (->point-vector p1 p2)
        uv (->unit-vector v)]
    (concat
          (for [n (range count)]
            (translate p1
                       (scale (* n interval) uv)))
          (list p2))))
