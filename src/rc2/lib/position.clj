(ns rc2.lib.position
  (:use clojure.tools.trace)
  (:require [rc2.lib.math :as math]
            [clojure.core.typed :as type])
  (:import [clojure.lang Seqable]))

(type/def-alias Vec '[Number Number Number])

;; TODO Migrate all of these functions from core/typed to Schema.
(type/ann ^:no-check vec? (predicate Vec))
(defn vec? [coord]
  "Returns true if 'coord represents a vec in 3-space."
  (if (and (sequential? coord)
           (= 3 (count coord))
           (every? number? coord))
    true
    false))

(type/ann ->vec (Fn [(U (type/NonEmptyLazySeq Number)
                        (I (Seqable Number) (ExactCount 3))) -> Vec]
                    [Number Number Number -> Vec]))
(defn ->vec
  "Get a new vec coordinate for [x,y,z]."
  ([p]  {:post [(vec? %)]}
     (when (and (= 3 (count p)) (every? number? p)) (apply vector p)))
  ([x y z]  {:post [(vec? %)]}
     (when (every? number? [x y z]) [x y z])))

(type/ann origin Vec)
(def origin (->vec 0 0 0))

(type/ann ^:no-check ->vec-vector [Vec Vec -> Vec])
(defn ->vec-vector [p1 p2]
  {:pre [(and (vec? p1) (vec? p2))]}
  "Compute the vector connecting p1 and p2."
  (->vec (map - p2 p1)))

(type/ann vector-length [Vec -> Number])
(defn vector-length [v]
  "Find the length of a vector."
  (math/sqrt (reduce + (map math/square v))))

(type/ann displacement (Fn [Vec -> Number]
                         [Vec Vec -> Number]))
(defn displacement
  "Find the distance between two vecs"
  ([p] (displacement origin p))
  ([p1 p2] (vector-length (->vec-vector p1 p2))))

(type/ann rotate (Fn [Vec Number -> Vec]))
(defn rotate [position angle]
  {:pre [(vec? position)]}
  "In three dimensions, rotate 'position around the Z axis by 'angle radians."
  (let [ct (math/cos angle)
        st (math/sin angle)
        [x y z] position]
    (->vec
     (+ (* ct x) (* st y))
     (+ (* (- st) x) (* ct y))
     z)))

(type/ann translate (Fn [Vec Vec -> Vec]))
(defn translate [p v]
  {:pre [(vec? p) (vec? v)]}
  "Translate 'p along 'v."
  (->vec (map + p v)))

(type/ann scale (Fn [Number Vec -> Vec]))
(defn scale [n v]
  "Scale the vector 'v by 'n"
  (->vec (map (partial * n) v)))

(type/ann within (Fn [Number Vec Vec
                      -> Boolean]))
(defn within [dist p1 p2]
  "Test if 'p1 and 'p2 are within 'dist of each other."
  (if (> dist (math/abs (displacement p1 p2)))
    true
    false))

(type/ann ->unit-vector [Vec -> Vec])
(defn ->unit-vector [v]
  "Convert the vector defined by two vecs (p1,p2) into a unit vector (i,j,k)"
  (let [length (vector-length v)]
    (->vec (map (type/fn> [x :- Number] (/ x length)) v))))

(type/ann interpolate (Fn [Vec Vec Number
                           -> (Seqable Vec)]))
(defn interpolate [p1 p2 interval]
  "Create a set of vecs on the line between p1 and p2"
  (let [count (math/abs (/ (displacement p1 p2) interval))
        v (->vec-vector p1 p2)
        uv (->unit-vector v)]
    (concat
     (type/for> :- Vec [n :- Number (range count)]
       (translate p1
                  (scale (* n interval) uv)))
     (list p2))))
