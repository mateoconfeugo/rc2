(ns rc2.util
  (:require [schema.core :as s])
  (:use-macros [dommy.macros :only [sel1]]))

(def WorldCoordinate {:x s/Num :y s/Num :type (s/eq :world)})
(def CanvasCoordinate {:x s/Num :y s/Num :type (s/eq :canvas)})
(def Coordinate (s/either WorldCoordinate CanvasCoordinate))

(def origin {:x 0 :y 0 :type :world})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Canvas/Drawing environment utilities
(defn get-canvas [] (sel1 :#target))

(defn get-context []
  "Get a drawing context for the canvas."
  (let [canvas (get-canvas)]
    (.getContext canvas "2d")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Coordinate manipulation & transformation

(defn ->world [x y]
  "Create a world coordinate."
  {:x x :y y :type :world})

(defn ->canvas [x y]
  "Create a canvas coordinate."
  {:x x :y y :type :canvas})

(defn screen->canvas [{:keys [x y]}]
  "Convert screen coordinates to canvas coordinates."
  (let [canvas (get-canvas)
        bounding-box (.getBoundingClientRect canvas)]
    {:x (* (- x (.-left bounding-box)) (/ (.-width canvas) (.-width bounding-box)))
     :y (* (- y (.-top bounding-box)) (/ (.-height canvas) (.-height bounding-box)))
     :type :canvas}))

(defn world->canvas [{:keys [x y type] :as coord}]
  "Convert world coordinates to canvas coordinates."
  (s/validate Coordinate coord)
  (if (keyword-identical? :world type)
    (let [canvas (get-canvas)]
      (->canvas (+ x (/ (.-width canvas) 2))
                (+ (- y) (/ (.-height canvas) 2))))
    coord))

(defn canvas->world [{:keys [x y type] :as coord}]
  "Convert canvas coordinates to world coordinates."
  (s/validate Coordinate coord)
  (if (keyword-identical? :canvas type)
    (let [canvas (get-canvas)]
      (->world (- x (/ (.-width canvas) 2))
               (- (- y (/ (.-height canvas) 2)))))
    coord))

(defn pp-coord [{:keys [x y]}]
  (str "[" x ", " y "]"))

(defn coerce-coord [c1 c2]
  "Convert C2 to be the same type of coordinate as C1."
  (if (= :world (:type c1))
    (canvas->world c2)
    (world->canvas c2)))

(defn coord+ [c1 c2]
  "Add two coordinates."
  (let [{x1 :x y1 :y} c1
        {x2 :x y2 :y} (coerce-coord c1 c2)]
    {:x (+ x1 x2) :y (+ y1 y2) :type (:type c1)}))

(defn coord- [c1 c2]
  "Subtract two coordinates."
  (let [{x1 :x y1 :y} c1
        {x2 :x y2 :y} (coerce-coord c1 c2)]
    {:x (- x1 x2) :y (- y1 y2) :type (:type c1)}))

(defn distance [c1 c2]
  (let [{x1 :x y1 :y} c1
        {x2 :x y2 :y} (coerce-coord c1 c2)
        dx (- x1 x2)
        dy (- y1 y2)]
    (.sqrt js/Math (+ (* dx dx) (* dy dy)))))

(defn world-edge [type]
  "Get a world coordinate lying at the extreme of an axis.

Accepts the following keywords for their corresponding axes:
:top (+Y)
:bottom (-Y)
:left (-X)
:right (+X)"
  (let [canvas (get-canvas)
        x-range (/ (.-width canvas) 2)
        y-range (/ (.-height canvas) 2)]
    (cond
     (= :right type) (assoc origin :x x-range)
     (= :left type) (assoc origin :x (- x-range))
     (= :top type) (assoc origin :y y-range)
     (= :bottom type) (assoc origin :y (- y-range)))))
