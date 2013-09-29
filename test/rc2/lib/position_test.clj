(ns rc2.lib.position_test
  (:use midje.sweet
        rc2.lib.position))

(def tolerance 1e-14)

(facts "About calling displacement with one argument"
  (fact (displacement (point 0 1 0)) => 1)
  (fact (displacement (point 2 2 2)) => (Math/sqrt 12))
  (fact (displacement (point -2 -2 -2)) => (Math/sqrt 12)))

(facts "About calling displacement with two arguments"
  (fact (displacement (point 0 1 0) (point 0 2 0)) => 1)
  (fact (displacement (point 2 2 2) (point 4 4 4)) => (Math/sqrt 12))
  (fact (displacement (point -2 -2 -2) (point -4 -4 -4)) => (Math/sqrt 12)))

(facts "About within"
  (fact "'within is true if two points are closer than 'dist from each other"
       (within 1 (point 0 0 0.5) origin) => true)
  (fact "'within is false if two points are farther than 'dist from each other"
        (within 1 (point 0 0 10) origin) => false))

(facts "About rotate"
  (fact "Rotating a point along the x axis by pi mirrors it over the y axis"
   (within tolerance (point -1.0 0.0 0.0) (rotate (point 1.0 0.0 0.0) Math/PI))
   => true)
  (fact "Rotating a point along the y axis by pi mirrors it over the x axis"
    (within tolerance (point 0.0 -1.0 0.0) (rotate (point 0.0 1.0 0.0) Math/PI))
    => true)
  (fact "Rotating a point along the z axis has no effect"
    (within tolerance (point 0.0 0.0 1.0) (rotate (point 0.0 0.0 1.0) Math/PI))
    => true))
