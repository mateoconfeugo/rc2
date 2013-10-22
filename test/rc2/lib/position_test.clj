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

(facts "About interpolate"
  (fact "Interpolating between two points should create a sequence of points between them"
    (interpolate [0 0 0] [3 3 3] 1)
    => '((0.0 0.0 0.0)
        (0.5773502691896257 0.5773502691896257 0.5773502691896257)
        (1.1547005383792515 1.1547005383792515 1.1547005383792515)
        (1.7320508075688772 1.7320508075688772 1.7320508075688772)
        (2.309401076758503 2.309401076758503 2.309401076758503)
        (2.8867513459481287 2.8867513459481287 2.8867513459481287)
        (3 3 3)))
  (fact "Interpolating from negative to positive points should work"
    (interpolate [-1 0 0] [1 0 0] 1) => '((-1 0 0) (0 0 0) (1 0 0)))
  (fact "Interpolating from negative to positive points should work"
    (interpolate [0 0 0] [1 0 0] 0.25) => '((0.0 0.0 0.0) (0.25 0.0 0.0) (0.5 0.0 0.0) (0.75 0.0 0.0)
                                            [1 0 0])))
