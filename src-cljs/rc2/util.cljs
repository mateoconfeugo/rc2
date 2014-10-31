(ns rc2.util
  (:require [schema.core :as s]))

(def WorldCoordinate {:x s/Num :y s/Num :z s/Num :type (s/eq :world)})
(def CanvasCoordinate {:x s/Num :y s/Num :z s/Num :type (s/eq :canvas)})
(def Coordinate (s/either WorldCoordinate CanvasCoordinate))

(def origin {:x 0 :y 0 :z 0 :type :world})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Canvas/Drawing environment utilities

(defn get-context [canvas]
  "Get a drawing context for the canvas."
  (.getContext canvas "2d"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Coordinate manipulation & transformation

(defn ->world
  "Create a world coordinate."
  ([x y z] (s/validate WorldCoordinate {:x x :y y :z z :type :world}))
  ([x y] (->world x y 0))
  ([[x y z]] (->world x y z)))

(defn ->canvas
  "Create a canvas coordinate."
  ([x y z] (s/validate CanvasCoordinate {:x x :y y :z z :type :canvas}))
  ([x y] (->canvas x y 0))
  ([[x y z]] (->canvas x y z)))

(defn screen->canvas [canvas {:keys [x y]}]
  "Convert screen coordinates to canvas coordinates."
  (let [bounding-box (.getBoundingClientRect canvas)]
    (->canvas (* (- x (.-left bounding-box)) (/ (.-width canvas) (.-width bounding-box)))
              (* (- y (.-top bounding-box)) (/ (.-height canvas) (.-height bounding-box))))))

(defn world->canvas [canvas {:keys [x y z type] :as coord}]
  "Convert world coordinates to canvas coordinates."
  (s/validate Coordinate coord)
  (if (keyword-identical? :world type)
    (->canvas (+ x (/ (.-width canvas) 2))
              (+ (- y) (/ (.-height canvas) 2))
              z)
    coord))

(defn canvas->world [canvas {:keys [x y z type] :as coord}]
  "Convert canvas coordinates to world coordinates."
  (s/validate Coordinate coord)
  (if (keyword-identical? :canvas type)
    (->world (- x (/ (.-width canvas) 2))
             (- (- y (/ (.-height canvas) 2)))
             z)
    coord))

(defn pp-coord [{:keys [x y]}]
  (str "[" x ", " y "]"))

(defn coerce-coord [canvas c1 c2]
  "Convert C2 to be the same type of coordinate as C1."
  (if (= :world (:type c1))
    (canvas->world canvas c2)
    (world->canvas canvas c2)))

(defn coord+ [canvas c1 c2]
  "Add two coordinates."
  (try
   (let [{x1 :x y1 :y z1 :z} c1
         {x2 :x y2 :y z2 :z} (coerce-coord canvas c1 c2)]
     {:x (+ x1 x2) :y (+ y1 y2) :z (+ z1 z2) :type (:type c1)})
   (catch js/Object e
     (.log js/console "Caught error" e "with coords" (str c1) "," (str c2))
     (throw e))))

(defn coord- [canvas c1 c2]
  "Subtract two coordinates."
  (let [{x1 :x y1 :y z1 :z} c1
        {x2 :x y2 :y z2 :z} (coerce-coord canvas c1 c2)]
    {:x (- x1 x2) :y (- y1 y2) :z (- z1 z2) :type (:type c1)}))

(defn distance
  ([canvas c1] (distance canvas origin c1))
  ([canvas c1 c2]
     (let [{x1 :x y1 :y z1 :z} c1
           {x2 :x y2 :y z2 :z} (coerce-coord canvas c1 c2)
           dx (- x1 x2)
           dy (- y1 y2)
           dz (- z1 z2)]
       (.sqrt js/Math (+ (* dx dx) (* dy dy) (* dz dz))))))

(defn scale-to [canvas c length]
  (let [{x :x y :y z :z} c
        current-len (distance canvas origin c)
        scale-factor (/ length current-len)]
    (assoc c :x (* x scale-factor) :y (* y scale-factor) :z (* z scale-factor))))

(defn world-edge [canvas type]
  "Get a world coordinate lying at the extreme of an axis.

Accepts the following keywords for their corresponding axes:
:top (+Y)
:bottom (-Y)
:left (-X)
:right (+X)"
  (let [x-range (/ (.-width canvas) 2)
        y-range (/ (.-height canvas) 2)]
    (cond
     (= :right type) (assoc origin :x x-range)
     (= :left type) (assoc origin :x (- x-range))
     (= :top type) (assoc origin :y y-range)
     (= :bottom type) (assoc origin :y (- y-range)))))

(defn current-time [] (.getTime (js/Date.)))
