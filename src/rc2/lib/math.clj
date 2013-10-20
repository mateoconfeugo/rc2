(ns rc2.lib.math
  (:require [clojure.math.numeric-tower :as nt]
            [clojure.core.typed :as type]))

(type/ann ^:no-check clojure.math.numeric-tower/expt [Number Number -> Number])
(type/ann ^:no-check clojure.math.numeric-tower/sqrt [Number -> Number])

;; Constants
(type/ann pi Double)
(def pi Math/PI)

;; Functions
(type/ann cos [Double -> Number])
(defn cos [theta] (Math/cos theta))

(type/ann sin [Double -> Number])
(defn sin [theta] (Math/sin theta))

(type/ann tan [Double -> Number])
(defn tan [theta] (Math/tan theta))

(type/ann square [Number -> Number])
(defn square [x] (nt/expt x 2))

(type/ann sqrt [Number -> Number])
(defn sqrt [x] (nt/sqrt x))

(type/ann abs [Number -> Number])
(defn abs [x] (nt/abs x))

(type/ann acos [Double -> Number])
(defn acos [x] (Math/acos x))

(type/ann asin [Double -> Number])
(defn asin [x] (Math/asin x))

(type/ann atan
          (Fn [Double -> Number]
              [Double Double -> Number]))
(defn atan
  ([x] (Math/atan x))
  ([x y] (Math/atan2 x y)))
