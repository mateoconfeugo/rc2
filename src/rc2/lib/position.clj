(ns rc2.lib.position)

(defrecord PointCoordinate [x y z])

(defn point [x y z]
  "Get a new point coordinate record for [x,y,z]."
  (PointCoordinate. x y z))

(def origin (point 0 0 0))

;; TODO These have to be defined in a math library somewhere.
(defn- square [x] (* x x))

(defn- sqrt [x] (Math/sqrt x))

(defn displacement
  "Find the distance between two points"
  ([p] (displacement origin p))
  ([p1 p2] (let [dx (- (:x p1) (:x p2))
                 dy (- (:y p1) (:y p2))
                 dz (- (:z p1) (:z p2))]
               (sqrt (+ (square dx) (square dy) (square dz))))))

(defn rotate [position angle]
  "Rotate 'position around the Z axis by 'angle radians"
  (let [ct (Math/cos angle)
        st (Math/sin angle)
        {:keys [x y z]} position]
   (point
    (+ (* ct x) (* st y))
    (+ (* (- st) x) (* ct y))
    z)))

(defn within [dist p1 p2]
  (if (> dist (Math/abs (displacement p1 p2)))
    true
    false))
