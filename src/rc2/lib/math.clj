(ns rc2.lib.math
  (:require [clojure.math.numeric-tower :as nt]))

;; Constants
(def pi Math/PI)

;; Functions
(defn cos [theta] (Math/cos (double theta)))

(defn sin [theta] (Math/sin (double theta)))

(defn tan [theta] (Math/tan (double theta)))

(defn square [x] (nt/expt x 2))

(defn sqrt [x] (nt/sqrt x))

(defn abs [x] (nt/abs x))

(defn acos [x] (Math/acos (double x)))

(defn asin [x] (Math/asin (double x)))

(defn atan
  ([x] (Math/atan (double x)))
  ([x y] (Math/atan2 (double x) (double y))))
