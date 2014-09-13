(ns rc2.lib.position
  (:use clojure.tools.trace)
  (:require [rc2.lib.math :as math]
            [schema.core :as s])
  (:import [clojure.lang Seqable]))

(def Vec [(s/one s/Num "x") (s/one s/Num "y") (s/one s/Num "z")])

(s/defn ->vec :- Vec
  "Get a new vec coordinate for [x,y,z]."
  ([p] (when (and (= 3 (count p)) (every? number? p)) (apply vector p)))
  ([x y z] (when (every? number? [x y z]) [x y z])))

(def origin (->vec 0 0 0))

(s/defn vec- :- Vec [p1 :- Vec p2 :- Vec]
  "Compute the vector connecting p1 and p2."
  (->vec (map - p2 p1)))

(s/defn vector-length :- s/Num [v :- Vec]
  "Find the length of a vector."
  (math/sqrt (reduce + (map math/square v))))

(s/defn displacement :- s/Num
  "Find the distance between two vecs"
  ([p :- Vec] (displacement origin p))
  ([p1 :- Vec p2 :- Vec] (vector-length (vec- p1 p2))))

(s/defn rotate :- Vec [position :- Vec angle :- s/Num]
  "In three dimensions, rotate 'position around the Z axis by 'angle radians."
  (let [ct (math/cos angle)
        st (math/sin angle)
        [x y z] position]
    (->vec
     (+ (* ct x) (* st y))
     (+ (* (- st) x) (* ct y))
     z)))

(s/defn translate :- Vec [p :- Vec v :- Vec]
  "Translate 'p along 'v."
  (->vec (map + p v)))

(s/defn scale :- Vec [n :- s/Num v :- Vec]
  "Scale the vector 'v by 'n"
  (->vec (map (partial * n) v)))

(s/defn within :- s/Bool [dist :- s/Num p1 :- Vec p2 :- Vec]
  "Test if 'p1 and 'p2 are within 'dist of each other."
  (if (> dist (math/abs (displacement p1 p2)))
    true
    false))

(s/defn ->unit-vector :- Vec [v :- Vec]
  "Convert the vector defined by two vecs (p1,p2) into a unit vector (i,j,k)"
  (let [length (vector-length v)]
    (->vec (map (fn [x] (/ x length)) v))))

(s/defn interpolate :- [Vec] [p1 :- Vec p2 :- Vec interval :- s/Num]
  "Create a set of vecs on the line between p1 and p2"
  (let [count (math/abs (/ (displacement p1 p2) interval))
        v (vec- p1 p2)
        uv (->unit-vector v)]
    (concat
     (for [n (range count)]
       (translate p1
                  (scale (* n interval) uv)))
     (list p2))))
