(ns rc2.lib.position_test
  (:use clojure.test
        rc2.lib.position))

(def tolerance 1e-14)

(deftest test-displacement-default
  (testing "displacement should return the pythagorean distance between the origin and a point"
    (is (= 1 (displacement (point 0 1 0))))
    (is (= (Math/sqrt 12) (displacement (point 2 2 2))))
    (is (= (Math/sqrt 12) (displacement (point -2 -2 -2))))))

(deftest test-displacement-explicit
  (testing "displacement should return the pythagorean distance between two points"
    (is (= 1 (displacement (point 0 1 0) (point 0 2 0))))
    (is (= (Math/sqrt 12) (displacement (point 2 2 2) (point 4 4 4))))
    (is (= (Math/sqrt 12) (displacement (point -2 -2 -2) (point -4 -4 -4))))))

(deftest test-within
  (testing "within should return true if two points are closer than the given distance from each other"
    (is (within 1 (point 0 0 0.5) origin))
    (is (not (within 1 (point 0 0 10) origin)))))

(deftest test-rotate
  (testing "Rotate should rotate the point around the Z axis by the given number of radians"
    (is (within tolerance (point -1.0 0.0 0.0) (rotate (point 1.0 0.0 0.0) Math/PI)))
    (is (within tolerance (point 0.0 -1.0 0.0) (rotate (point 0.0 1.0 0.0) Math/PI)))
    (is (within tolerance (point 0.0 0.0 1.0) (rotate (point 0.0 0.0 1.0) Math/PI)))))
