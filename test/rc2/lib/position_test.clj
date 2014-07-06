(ns rc2.lib.position_test
  (:require [speclj.core :refer :all]
            [rc2.lib.position :refer :all]))

(def tolerance 1e-14)

(describe
 "calling displacement with one argument"
 (it "returns 1 for a unit vector" (should= 1 (displacement (->vec 0 1 0))))
 (it "returns the length of a non-unit vector"
     (should= (Math/sqrt 12) (displacement (->vec 2 2 2))))
 (it "returns the length of a non-unit vector with negative elements"
     (should= (Math/sqrt 12) (displacement (->vec -2 -2 -2)))))

(describe
 "calling displacement with two arguments"
 (it "returns 1 for a unit vector between two points"
  (should= 1 (displacement (->vec 0 1 0) (->vec 0 2 0))))
 (it "returns the length of a vector between two points"
  (should= (Math/sqrt 12) (displacement (->vec 2 2 2) (->vec 4 4 4))))
 (it "returns the length of a vector between two points with negative elements"
  (should= (Math/sqrt 12) (displacement (->vec -2 -2 -2) (->vec -4 -4 -4)))))

(describe
 "within"
  (it "returns true if two points are closer than 'dist from each other"
      (should= true (within 1 (->vec 0 0 0.5) origin)))
  (it "returns false if two points are more than 'dist away from each other"
      (should= false (within 1 (->vec 0 0 10) origin))))

(describe
 "rotate"
 (it "rotating a vec along the x axis by pi mirrors it over the y axis"
     (should= true
              (within tolerance (->vec -1.0 0.0 0.0) (rotate (->vec 1.0 0.0 0.0) Math/PI))))
 (it "rotating a vec along the y axis by pi mirrors it over the x axis"
     (should= true
              (within tolerance (->vec 0.0 -1.0 0.0) (rotate (->vec 0.0 1.0 0.0) Math/PI))))
 (it "rotating a vec along the z axis has no effect"
     (should= true
              (within tolerance (->vec 0.0 0.0 1.0) (rotate (->vec 0.0 0.0 1.0) Math/PI)))))

(describe
 "interpolate"
 (it "interpolating between two points should create a sequence of points between them"
     (should== '((0.0 0.0 0.0)
                 (0.5773502691896257 0.5773502691896257 0.5773502691896257)
                 (1.1547005383792515 1.1547005383792515 1.1547005383792515)
                 (1.7320508075688772 1.7320508075688772 1.7320508075688772)
                 (2.309401076758503 2.309401076758503 2.309401076758503)
                 (2.8867513459481287 2.8867513459481287 2.8867513459481287)
                 (3 3 3))
               (interpolate [0 0 0] [3 3 3] 1)))
 (it "interpolating from negative to positive points should work"
     (should== '((-1 0 0) (0 0 0) (1 0 0)) (interpolate [-1 0 0] [1 0 0] 1)))
 (it "interpolating from negative to positive points should work"
     (should== '((0.0 0.0 0.0) (0.25 0.0 0.0) (0.5 0.0 0.0) (0.75 0.0 0.0) [1 0 0])
               (interpolate [0 0 0] [1 0 0] 0.25))))

(run-specs)
