(ns rc2.draw
  (:require [rc2.util :as util]))

(def default-color "#C9EAF9")
(def dark-color "#627279")
(def grid-color "#2E3639")
(def highlight-color "#FAC1B1")
(def error-color "#FF0000")
(def source-color "#ACAB69")
(def sink-color "#8869AC")

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

(defn text-size [text & {:keys [size] :or {size 12}}]
  (let [context (util/get-context)]
    (set! (.-font context) (str size "px monospace"))
    {:width (.-width (.measureText context text))
     :height (.-height (.measureText context text))}))

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
                  highlight-color
                  default-color)]
      (draw-rect coord width height color)
      (draw-text context (util/coord+ coord (util/->world 10 -12)) (:text button) color))))

(defn draw-ui-elements [elts]
  (let [{:keys [buttons]} elts]
    (draw-buttons buttons)))

(defn draw-coordinates [coord]
  (let [context (util/get-context)
        world-coords (util/canvas->world coord)]
    (draw-text context (util/coord+ coord (util/->world 5 5))
               (util/pp-coord world-coords) default-color)))

(defn draw-mode-info [{:keys [primary secondary]}]
  (let [canvas (util/get-canvas)
        context (util/get-context)
        primary-text (cond
                      (= :insert primary) "INSERT"
                      (= :delete primary) "DELETE"
                      (= :edit primary) "EDIT")
        secondary-text (cond
                        (= :source secondary) "SOURCE "
                        (= :sink secondary) "SINK "
                        :else "")
        text (str secondary-text primary-text " MODE")
        x-off (- (/ (:width (text-size text :size 14)) 2))
        y-off 60
        coord (util/coord+ (util/world-edge :bottom) (util/->world x-off y-off))]
    (draw-text context coord text default-color :size 14)))

(defn draw-connection-info [connection time]
  (let [canvas (util/get-canvas)
        context (util/get-context)
        text (if (:connected connection) "CONNECTED" "OFFLINE")
        x (- (.-width canvas) (:width (text-size text :size 14)) 30)
        y 30]
    (draw-text context (util/->canvas x y) text
               (if (:connected connection) default-color error-color) :size 14)
    (draw-text context (util/->canvas (- x 72) (+ 30 y))
               (str "TIME " time) default-color :size 14)))

(defn draw-state-info [state]
  (let [canvas (util/get-canvas)
        context (util/get-context)
        x 230
        y 90]
    (doall (map-indexed
            (fn [i kv]
              (draw-text context (util/->canvas x (+ y (* 20 i))) (str kv) dark-color))
            state))))

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
               (util/->canvas (+ x 250 (:width (text-size title))) (+ y 3))
               default-color)
    (doseq [[item offset] (map list items (iterate (fn [offset] (+ offset 18)) 22))]
      (draw-text context (util/->canvas x (+ y offset)) (str (xform item))
                 (if (:highlight item) highlight-color default-color)))))

(defn get-waypoint-text [parts wp]
  (let [kind (if (= :source (:kind wp)) "SOURCE " "SINK   ")
        coord (util/pp-coord (:location wp))
        part (:name (get parts (:part-id wp)))]
   (str kind coord " -> " part)))

(defn draw-waypoints [waypoints parts]
  (draw-section :title "WAYPOINTS" :coord (util/->canvas 30 30)
                :items waypoints
                :xform (partial get-waypoint-text parts))
  (let [context (util/get-context)]
    (doseq [wp waypoints]
      (let [loc (:location wp)
            color (if (= :source (:kind wp)) source-color sink-color)
            color (if (:highlight wp) highlight-color color)]
        (draw-circle context loc waypoint-radius color)
        (draw-text context (util/coord+ (util/->world 5 3) loc)
                   (:part-id wp) default-color :size 10)))))

(defn pairs [coll]
  (map vector coll (drop 1 coll)))

(defn draw-plan-segments [plan]
  (let [context (util/get-context)]
    (doseq [segment (pairs plan)]
      (let [start (apply util/->world (first segment))
            end (apply util/->world (second segment))]
        (draw-line context start end default-color)))))

(defn draw-part-list [parts]
  (draw-section :title "PARTS"
                :coord (util/->canvas 30 (+ 30 (/ (.-height (util/get-canvas)) 2)))
                :items (sort-by :id (map (fn [[id part]] (assoc part :id id)) parts))
                :xform (fn [part] (str (:id part) ": " (:name part)))))

(defn draw-event-log [events]
  (draw-section :title "EVENT LOG"
                :coord (util/->canvas 30 (+ 30 (/ (.-height (util/get-canvas)) 2)))
                :items events))

(defn draw [state]
  (clear-canvas!)
  (draw-grid)
  (draw-crosshairs util/origin dark-color)
  (draw-crosshairs (get-in state [:mouse :location]))
  (draw-ui-elements (get state :ui))
  (draw-coordinates (get-in state [:mouse :location]))
  (draw-connection-info (get state :connection) (get state :time))
  (draw-mode-info (get state :mode))
  (draw-state-info state)
  (draw-plan-segments (get state :plan))
  (draw-waypoints (get state :waypoints) (get-in state [:parts]))
  (draw-part-list (get-in state [:parts]))
  ;;(draw-event-log (get state :events))
  )
