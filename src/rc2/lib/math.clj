(ns rc2.lib.math
  (:require [clojure.math.numeric-tower :as nt]))

;; Constants
(def pi Math/PI)

;; Functions
(defn cos [theta] (Math/cos theta))
(defn sin [theta] (Math/sin theta))
(defn tan [theta] (Math/tan theta))
(defn square [x] (nt/expt x 2))
(defn sqrt [x] (nt/sqrt x))
(defn abs [x] (nt/abs x))
(defn acos [x] (Math/acos x))
(defn asin [x] (Math/asin x))
(defn atan
  ([x] (Math/atan x))
  ([x y] (Math/atan2 x y)))
