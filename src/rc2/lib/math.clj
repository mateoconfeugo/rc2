(ns rc2.lib.math
  (:require [clojure.math.numeric-tower :as nt]
            [clojure.core.typed :as type]))

(type/ann ^:no-check clojure.math.numeric-tower/expt [Number Number -> Number])
(type/ann ^:no-check clojure.math.numeric-tower/sqrt [Number -> Number])

;; Constants
(type/ann pi Number)
(def pi Math/PI)

;; Functions
(type/ann cos [Number -> Number])
(defn cos [theta] (Math/cos (double theta)))

(type/ann sin [Number -> Number])
(defn sin [theta] (Math/sin (double theta)))

(type/ann tan [Number -> Number])
(defn tan [theta] (Math/tan (double theta)))

(type/ann square [Number -> Number])
(defn square [x] (nt/expt x 2))

(type/ann sqrt [Number -> Number])
(defn sqrt [x] (nt/sqrt x))

(type/ann abs [Number -> Number])
(defn abs [x] (nt/abs x))

(type/ann acos [Number -> Number])
(defn acos [x] (Math/acos (double x)))

(type/ann asin [Number -> Number])
(defn asin [x] (Math/asin (double x)))

(type/ann atan
          (Fn [Number -> Number]
              [Number Number -> Number]))
(defn atan
  ([x] (Math/atan (double x)))
  ([x y] (Math/atan2 (double x) (double y))))
