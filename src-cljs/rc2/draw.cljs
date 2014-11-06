(ns rc2.draw
  (:require [rc2.util :as util]))

(def default-color "#C9EAF9")
(def dark-color "#627279")
(def grid-color "#2E3639")
(def highlight-color "#FAC1B1")
(def error-color "#FF0000")
(def delete-color "#FF0000")
(def source-color "#ACAB69")
(def sink-color "#8869AC")

(def waypoint-radius 5)
(def animation-radius 3)
(def button-size 100)

(defn fix-canvas-size! [canvas]
  "Set the internal canvas size properties so that it matches its on-screen size."
  (let [window-width (.-offsetWidth canvas)
        window-height (.-offsetHeight canvas)]
    (set! (.-width canvas) window-width)
    (set! (.-height canvas) window-height)))

;;;;;;;;;;;;
;; Drawing

;; These functions should not modify application state, they should only render
;; it to the screen. Updates to state should happen below.

(defn draw-rect [context coord width height color]
  (let [{:keys [x y]} (util/world->canvas (.-canvas context) coord)]
    (set! (.-strokeStyle context) color)
    (.strokeRect context x y width height)))

(defn draw-line [context c1 c2 color]
  (let [{x1 :x y1 :y} (util/world->canvas (.-canvas context) c1)
        {x2 :x y2 :y} (util/world->canvas (.-canvas context) c2)]
    (set! (.-strokeStyle context) color)
    (.beginPath context)
    (.moveTo context x1 y1)
    (.lineTo context x2 y2)
    (.stroke context)))

(defn draw-circle [context coord r color]
  (let [{:keys [x y]} (util/world->canvas (.-canvas context) coord)]
    (set! (.-strokeStyle context) color)
    (.beginPath context)
    (.arc context x y r 0 (* 2 (.-PI js/Math)))
    (.stroke context)))

(defn draw-text [context coord text color & {:keys [size] :or {size 12}}]
  (let [canvas-coord (util/world->canvas (.-canvas context) coord)]
    (set! (.-font context) (str size "px monospace"))
    (set! (.-fillStyle context) color)
    (.fillText context text (:x canvas-coord) (:y canvas-coord))))

(defn clear-canvas! [canvas]
  (let [ctx (util/get-context canvas)]
    (.clearRect ctx 0 0 (.-width canvas) (.-height canvas))))

(defn text-size [context text & {:keys [size] :or {size 12}}]
  (set! (.-font context) (str size "px monospace"))
  {:width (.-width (.measureText context text))
   :height (.-height (.measureText context text))})

(defn draw-crosshairs
  ([canvas coord] (draw-crosshairs canvas coord default-color))
  ([canvas coord color]
     (let [context (util/get-context canvas)
           {:keys [x y] :as canvas-coord} (util/world->canvas canvas coord)]
       (set! (.-lineWidth context) 1.0)
       (draw-line context (assoc canvas-coord :x 0)
                  (assoc canvas-coord :x (.-width canvas)) color)
       (draw-line context (assoc canvas-coord :y 0)
                  (assoc canvas-coord :y (.-height canvas)) color))))

(defn draw-grid [canvas]
  (let [grid-spacing 50
        vert-count (.ceil js/Math (/ (:y (util/world-edge canvas :top)) grid-spacing))
        horiz-count (.ceil js/Math (/ (:x (util/world-edge canvas :right)) grid-spacing))
        count (if (< vert-count horiz-count) horiz-count vert-count)]
    ;; Draw crosshairs along the diagonal
    (doseq [coord (map (fn [i] (util/->world (* 50 i) (* 50 i))) (range (- count) count))]
      (draw-crosshairs canvas coord grid-color))))

(defn indexed [seq] (map-indexed vector seq))

(defn index-of [elt seq]
  (first (first (filter #(= elt (second %)) (indexed seq)))))

(defn button-render-details [canvas buttons button]
  (let [buttons (filter :visible buttons)
        section-width (* (count buttons) (+ button-size 10))
        index (index-of button buttons)
        offset (util/->world (- (+ (* (+ button-size 10) index) 5) (/ section-width 2)) 50)]
    {:coord (util/coord+ canvas offset (util/world-edge canvas :bottom))
     :width button-size :height 20}))

(defn draw-buttons [canvas buttons]
  (doseq [button (filter :visible buttons)]
    (let [{:keys [coord width height]} (button-render-details canvas buttons button)
          context (util/get-context canvas)
          color (if (:hover button)
                  highlight-color
                  default-color)]
      (draw-rect context coord width height color)
      (draw-text context (util/coord+ canvas coord (util/->world 10 -12))
                 (:text button) color))))

(defn draw-ui-elements [canvas elts]
  (let [{:keys [buttons]} elts]
    (draw-buttons canvas (vals buttons))))

(defn mode-color [mode]
  (let [primary (.-keyword (:primary mode))
        secondary (.-keyword (:secondary mode))]
   (condp = primary
     :delete delete-color
     :insert (condp = secondary
               :source source-color
               :sink sink-color
               default-color)
     :edit dark-color
     default-color)))

(defn draw-coordinates [canvas mode coord]
  (let [context (util/get-context canvas)
        world-coords (util/canvas->world canvas coord)]
    (draw-text context (util/coord+ canvas coord (util/->world 5 5))
               (util/pp-coord world-coords) (mode-color mode))))

(defn draw-current-part [canvas mode coord parts]
  (let [part-name (:name (first (filter (fn [p] (:highlight p)) (vals parts))))
        part-name (or part-name "")
        context (util/get-context canvas)]
    (draw-text context
               (util/coord+ canvas
                            (util/->world (- 0 (:width (text-size context part-name)) 5) 5)
                            coord)
               part-name (mode-color mode))))

(defn draw-mode-info [canvas {:keys [primary secondary]}]
  (let [context (util/get-context canvas)
        primary-text (.-lighter primary)
        secondary-text (.-lighter secondary)
        text (str secondary-text " " primary-text " MODE")
        x-off (- (/ (:width (text-size context text :size 14)) 2))
        y-off 60
        coord (util/coord+ canvas (util/world-edge canvas :bottom) (util/->world x-off y-off))]
    (draw-text context coord text default-color :size 14)))

(defn draw-connection-info [canvas connection time]
  (let [context (util/get-context canvas)
        text (if (:connected connection) "CONNECTED" "OFFLINE")
        x (- (.-width canvas) (:width (text-size context text :size 14)) 30)
        y (- (.-height canvas) 60)]
    (draw-text context (util/->canvas x y) text
               (if (:connected connection) default-color error-color) :size 14)
    (draw-text context (util/->canvas (- x 72) (+ 30 y))
               (str "TIME " time) default-color :size 14)))

(defn draw-state-info [canvas state]
  (let [context (util/get-context canvas)
        x 230
        y 90]
    (doall (map-indexed
            (fn [i kv]
              (draw-text context (util/->canvas x (+ y (* 20 i))) (str kv) dark-color))
            state))))

(defn draw-section [canvas & {:keys [title coord items xform]
                       :or {xform identity}}]
  "Draw a section of text on the screen containing the given items.

A section consists of a title followed by each item in items. If there is not enough space to draw
all of the items, items from the end of the list will be preferred." ;; Scrolling?
  (let [context (util/get-context canvas)
        {:keys [x y]} (util/world->canvas canvas coord)
        width 300]
    (draw-text context (util/->canvas (+ x 2) y) title default-color :size 14)
    (draw-line context
               (util/->canvas x (+ y 3))
               (util/->canvas (+ x width) (+ y 3))
               default-color)
    (doseq [[item offset] (map list items (iterate (fn [offset] (+ offset 18)) 22))]
      (draw-text context (util/->canvas x (+ y offset)) (str (xform item))
                 (if (:highlight item) highlight-color default-color)))))

(defn get-waypoint-text [parts current [idx wp]]
  (let [kind (if (= :source (:kind wp)) "SOURCE " "SINK   ")
        coord (util/pp-coord (:location wp))
        part (:name (get parts (:part-id wp)))]
   (str (if (not (= current idx)) "   " "-> ") kind coord ": " part)))

(defn draw-waypoints [canvas waypoints]
  (let [context (util/get-context canvas)]
    (doseq [wp waypoints]
      (let [loc (:location wp)
            color (if (= :source (:kind wp)) source-color sink-color)
            color (if (:highlight wp) highlight-color color)]
        (draw-circle context loc waypoint-radius color)
        (draw-text context (util/coord+ canvas (util/->world 5 3) loc)
                   (:part-id wp) default-color :size 10)))))

(defn pairs [coll]
  (map vector coll (drop 1 coll)))

(defn draw-plan-segments [canvas plan]
  (let [context (util/get-context canvas)
        segments (pairs (map #(:location %) plan))]
    (doseq [segment segments]
      (let [start (first segment)
            end (second segment)]
        (draw-line context start end default-color)))))

(defn draw-plan-animation [canvas plan anim-state]
  (when (not (empty? plan))
    (let [base (:location (nth plan (:index anim-state)))
          loc (util/coord+ canvas (:offsets anim-state) base)
          context (util/get-context canvas)]
      (draw-circle context loc animation-radius default-color))))

(defn draw-actuator-position [canvas position]
  (let [context (util/get-context canvas)]
    (draw-circle context position animation-radius default-color)))

(defn draw-plan [canvas plan parts current]
  (draw-section canvas
                :title "PLAN"
                :coord (util/->canvas (- (.-width canvas) 250 80) 30)
                :items (indexed plan)
                :xform (partial get-waypoint-text parts current)))

(defn draw-part-list [canvas parts]
  (draw-section canvas
                :title "PARTS"
                :coord (util/->canvas 30 (+ 30 (/ (.-height canvas) 2)))
                :items (sort-by :id (map (fn [[id part]] (assoc part :id id)) parts))
                :xform (fn [part] (str (:id part) ": " (:name part)))))

(defn draw [canvas state]
  (fix-canvas-size! canvas)
  (clear-canvas! canvas)
  (draw-grid canvas)
  (draw-crosshairs canvas util/origin dark-color)
  (draw-crosshairs canvas (get-in state [:mouse :location]))
  (draw-ui-elements canvas (get state :ui))
  (draw-coordinates canvas (get state :mode) (get-in state [:mouse :location]))
  (draw-current-part canvas (get state :mode) (get-in state [:mouse :location]) (get state :parts))
  (draw-connection-info canvas (get state :connection) (get state :time))
  (draw-mode-info canvas (get state :mode))
  (draw-state-info canvas state)
  (draw-plan-segments canvas (get-in state [:route :plan]))
  (draw-plan canvas
             (get-in state [:route :plan])
             (get state :parts)
             (get-in state [:route :execution :current]))
  (draw-waypoints canvas (get-in state [:route :waypoints]))
  (if (not (= :execute (.-keyword (get-in state [:mode :primary]))))
    (draw-plan-animation canvas (get-in state [:route :plan]) (get-in state [:route :animation]))
    (draw-actuator-position canvas (get-in state [:robot :position])))
  (draw-part-list canvas (get state :parts))
  )
