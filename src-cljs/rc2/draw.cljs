(ns rc2.draw
  (:require [rc2.util :as util]))

(def default-color "#C9EAF9")
(def dark-color "#627279")
(def grid-color "#2E3639")
(def highlight-color "#FAC1B1")
(def click-color "#00FF00")

(def waypoint-radius 5)
(def button-size 100)

(defn size-canvas-to-window! []
  (let [window-width (.-innerWidth js/window)
        window-height (.-innerHeight js/window)
        canvas (util/get-canvas)]
    (set! (.-width canvas) window-width)
    (set! (.-height canvas) window-height)))

;;;;;;;;;;;;
;; Drawing

;; These functions should not modify application state, they should only render
;; it to the screen. Updates to state should happen below.

(defn draw-rect [coord width height color]
  (let [ctx (util/get-context)
        {:keys [x y]} (util/world->canvas coord)]
    (set! (.-strokeStyle ctx) color)
    (.strokeRect ctx x y width height)))

(defn draw-line [context c1 c2 color]
  (let [{x1 :x y1 :y} (util/world->canvas c1)
        {x2 :x y2 :y} (util/world->canvas c2)]
    (set! (.-strokeStyle context) color)
    (.beginPath context)
    (.moveTo context x1 y1)
    (.lineTo context x2 y2)
    (.stroke context)))

(defn draw-circle [context coord r color]
  (let [{:keys [x y]} (util/world->canvas coord)]
    (set! (.-strokeStyle context) color)
    (.beginPath context)
    (.arc context x y r 0 (* 2 (.-PI js/Math)))
    (.stroke context)))

(defn draw-text [context coord text color & {:keys [size] :or {size 12}}]
  (let [canvas-coord (util/world->canvas coord)]
    (set! (.-font context) (str size "px monospace"))
    (set! (.-fillStyle context) color)
    (.fillText context text (:x canvas-coord) (:y canvas-coord))))

(defn clear-canvas! []
  (let [ctx (util/get-context)
        canvas (util/get-canvas)]
    (.clearRect ctx 0 0 (.-width canvas) (.-height canvas))))

(defn text-size [context text]
  {:width (.-width (.measureText context text))
   :height (.-height (.measureText context text))})

(defn draw-crosshairs
  ([coord] (draw-crosshairs coord default-color))
  ([coord color]
     (let [context (util/get-context)
           {:keys [x y] :as canvas-coord} (util/world->canvas coord)]
       (set! (.-lineWidth context) 1.0)
       (draw-line context (assoc canvas-coord :x 0)
                  (assoc canvas-coord :x (.-width (.-canvas context))) color)
       (draw-line context (assoc canvas-coord :y 0)
                  (assoc canvas-coord :y (.-height (.-canvas context))) color))))

(defn draw-grid []
  (let [grid-spacing 50
        vert-count (.ceil js/Math (/ (:y (util/world-edge :top)) grid-spacing))
        horiz-count (.ceil js/Math (/ (:x (util/world-edge :right)) grid-spacing))
        count (if (< vert-count horiz-count) horiz-count vert-count)]
    ;; Draw crosshairs along the diagonal
    (doseq [coord (map (fn [i] (util/->world (* 50 i) (* 50 i))) (range (- count) count))]
      (draw-crosshairs coord grid-color))))

(defn index-of [elt seq]
  (first (first (filter #(= elt (second %)) (map-indexed vector seq)))))

(defn button-render-details [buttons button]
  (let [section-width (* (count buttons) (+ button-size 10))
        index (index-of button buttons)
        offset (util/->world (- (+ (* (+ button-size 10) index) 5) (/ section-width 2)) 50)]
    {:coord (util/coord+ offset (util/world-edge :bottom)) :width button-size :height 20}))

(defn draw-buttons [buttons]
  (doseq [button buttons]
    (let [{:keys [coord width height]} (button-render-details buttons button)
          context (util/get-context)
          color (if (:hover button)
                  (if (:click button) click-color highlight-color)
                  default-color)]
      (draw-rect coord width height color)
      (draw-text context (util/coord+ coord (util/->world 10 -12)) (:text button) color))))

(defn draw-ui-elements [elts]
  (let [{:keys [buttons]} elts]
    (draw-buttons buttons)))

(defn draw-coordinates [coord]
  (let [context (util/get-context)
        world-coords (util/canvas->world coord)]
    (draw-text context (util/coord+ coord (util/->world 5 5)) (util/pp-coord world-coords) default-color)))

(defn draw-connection-info [connection time]
  (let [canvas (util/get-canvas)
        context (util/get-context)]
    (let [text (if (:connected connection) "CONNECTED" "OFFLINE")
          x (- (.-width canvas) (:width (text-size context text)) 30)
          y 30]
      (draw-text context (util/->canvas x y) text default-color :size 14)
      (draw-text context (util/->canvas (- x 72) (+ 30 y)) (str "TIME " time) default-color :size 14))))

(defn draw-state-info [state]
  (let [canvas (util/get-canvas)
        context (util/get-context)]
    (let [x 30
          y 90]
      (doall (map-indexed
              (fn [i kv]
                (draw-text context (util/->canvas x (+ y (* 20 i))) (str kv) dark-color))
              state)))))

(defn draw-section [& {:keys [title coord items xform] :or {xform identity}}]
  "Draw a section of text on the screen containing the given items.

A section consists of a title followed by each item in items. If there is not enough space to draw
all of the items, items from the end of the list will be preferred." ;; Scrolling?
  (let [canvas (util/get-canvas)
        context (util/get-context)
        {:keys [x y]} (util/world->canvas coord)]
    (draw-text context (util/->canvas x y) title default-color :size 14)
    (draw-line context
               (util/->canvas (- x 2) (+ y 3))
               (util/->canvas (+ x 250 (:width (text-size context title))) (+ y 3))
               default-color)
    (doseq [[item offset] (map list items (iterate (fn [offset] (+ offset 18)) (- y 5)))]
      (draw-text context (util/->canvas x (+ y offset)) (str (xform item))
                 (if (:highlight item) highlight-color default-color)))))

(defn draw-waypoints [waypoints]
  (draw-section :title "WAYPOINTS" :coord (util/->canvas 30 30)
                :items waypoints
                :xform (fn [wp] (util/pp-coord (:location wp))))
  (let [context (util/get-context)]
    (doseq [wp waypoints]
      (let [loc (:location wp)]
        (draw-circle context loc waypoint-radius
                     (if (:highlight wp) highlight-color default-color))))))

(defn draw-event-log [events]
  (draw-section :title "EVENT LOG"
                :coord (util/->canvas 30 (+ 30 (/ (.-height (util/get-canvas)) 2)))
                :items events))

(defn draw [state]
  (clear-canvas!)
  (draw-grid)
  (draw-crosshairs util/origin dark-color)
  (draw-crosshairs (get-in state [:mouse :location]))
  (draw-ui-elements (get-in state [:ui]))
  (draw-coordinates (get-in state [:mouse :location]))
  (draw-connection-info (get-in state [:connection]) (get-in state [:time]))
  (draw-state-info state)
  (draw-waypoints (get-in state [:waypoints]))
  (draw-event-log (get-in state [:events])))
