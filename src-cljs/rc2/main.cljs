(ns rc2.main
  (:require [dommy.core :as dommy]
            [rc2.api :as api]
            [schema.core :as s])
  (:use-macros [dommy.macros :only [node sel sel1]]))

(def origin {:x 0 :y 0 :type :world})
(def default-color "#C9EAF9")
(def dark-color "#627279")
(def button-size 100)
(def framerate 30)
(def heartbeat-interval (* 1000 3))

(def timer-id (atom nil))
(def app-state
  (atom {
         :mouse {
                 :location origin
                 :buttons {0 :up 1 :up 2 :up}
                 :previous-buttons {0 :up 1 :up 2 :up}
                 }
         :waypoints []
         :events []
         :ui {
              ;; Buttons have a name (rendered on the screen), a target vector, and a
              ;; transform function. When the button is clicked the transform function
              ;; is called with the value in this state tree at the 'target' path,
              ;; which is then replaced with the returned value.
              :buttons [{:text "Start" :target [:running] :xform (constantly true)
                         :hover false :click false}
                        {:text "Stop" :target [:running] :xform (constantly false)
                         :hover false :click false}
                        {:text "Reset" :target [:running] :xform not
                         :hover false :click false}]
              }
         :connection {
                      :last-heartbeat 0
                      :connected false
                      }
         :time 0
         :running false
         }))

(def WorldCoordinate {:x s/Num :y s/Num :type (s/eq :world)})
(def CanvasCoordinate {:x s/Num :y s/Num :type (s/eq :canvas)})
(def Coordinate (s/either WorldCoordinate CanvasCoordinate))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Canvas/Drawing environment utilities
(defn get-canvas [] (sel1 :#target))

(defn get-context []
  "Get a drawing context for the canvas."
  (let [canvas (get-canvas)]
    (.getContext canvas "2d")))

(defn size-canvas-to-window! []
  (let [window-width (.-innerWidth js/window)
        window-height (.-innerHeight js/window)
        canvas (get-canvas)]
    (set! (.-width canvas) window-width)
    (set! (.-height canvas) window-height)))

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

;;;;;;;;;;;;
;; Drawing

;; These functions should not modify application state, they should only render
;; it to the screen. Updates to state should happen below.

(defn draw-rect! [[width height] coord color]
  (let [ctx (get-context)
        {:keys [x y]} (world->canvas coord)]
    (set! (.-strokeStyle ctx) color)
    (.strokeRect ctx x y width height)))

(defn draw-line [context c1 c2 color]
  (let [{x1 :x y1 :y} (world->canvas c1)
        {x2 :x y2 :y} (world->canvas c2)]
    (set! (.-strokeStyle context) color)
    (.beginPath context)
    (.moveTo context x1 y1)
    (.lineTo context x2 y2)
    (.stroke context)))

(defn draw-circle [context coord r]
  (let [{:keys [x y]} (world->canvas coord)]
    (.beginPath context)
    (.arc context x y r 0 (* 2 (.-PI js/Math)))
    (.stroke context)))

(defn clear-canvas! []
  (let [ctx (get-context)
        canvas (get-canvas)]
    (.clearRect ctx 0 0 (.-width canvas) (.-height canvas))))

(defn text-size [context text]
  {:width (.-width (.measureText context text))
   :height (.-height (.measureText context text))})

(defn draw-crosshairs
  ([coord] (draw-crosshairs coord default-color))
  ([coord color]
     (let [context (get-context)
           {:keys [x y] :as canvas-coord} (world->canvas coord)]
       (set! (.-lineWidth context) 1.0)
       (draw-line context (assoc canvas-coord :x 0)
                  (assoc canvas-coord :x (.-width (.-canvas context))) color)
       (draw-line context (assoc canvas-coord :y 0)
                  (assoc canvas-coord :y (.-height (.-canvas context))) color))))

(defn index-of [elt seq]
  (first (first (filter #(= elt (second %)) (map-indexed vector seq)))))

(defn button-render-details [buttons button]
  (let [section-width (* (count buttons) (+ button-size 10))
        index (index-of button buttons)
        offset (->world (- (+ (* (+ button-size 10) index) 5) (/ section-width 2)) 50)]
    {:coord (coord+ offset (world-edge :bottom)) :width button-size :height 20}))

(defn draw-buttons [buttons]
  (doseq [button buttons]
    (let [{:keys [coord width height]} (button-render-details buttons button)
          context (get-context)]
      (draw-rect! [width height] coord
                  (if (:hover button) "#FF0000" default-color)))))

(defn draw-ui-elements [elts]
  (let [{:keys [buttons]} elts]
    (draw-buttons buttons)))

(defn draw-coordinates [coord]
  (let [context (get-context)
        world-coords (canvas->world coord)
        {:keys [x y]} (world->canvas coord)]
    (set! (.-fillStyle context) default-color)
    (set! (.-font context) "12px monospace")
    (.fillText context
               (str "(" (:x world-coords) "," (:y world-coords) ")")
               (+ x 10) (- y 10))))

(defn draw-connection-info [connection time]
  (let [canvas (get-canvas)
        context (get-context)]
    (set! (.-fillStyle context) default-color)
    (set! (.-font context) "14px monospace")
    (let [text (if (:connected connection) "CONNECTED" "OFFLINE")
          x (- (.-width canvas) (:width (text-size context text)) 30)
          y 30]
      (.fillText context text x y)
      (.fillText context (str "TIME " time) (- x 60) (+ 30 y)))))

(defn draw-state-info [state]
  (let [canvas (get-canvas)
        context (get-context)]
    (set! (.-fillStyle context) dark-color)
    (set! (.-font context) "12px monospace")
    (let [x 30
          y 90]
      (doall (map-indexed
              (fn [i kv]
                (.fillText context (str kv) x (+ y (* 20 i))))
              state)))))

(defn draw-section [& {:keys [title coord items]}]
  "Draw a section of text on the screen containing the given items.

A section consists of a title followed by each item in items. If there is not enough space to draw
all of the items, items from the end of the list will be preferred." ;; Scrolling?
  (let [canvas (get-canvas)
        context (get-context)
        {:keys [x y]} (world->canvas coord)]
    (set! (.-fillStyle context) default-color)
    (set! (.-font context) "14px monospace")
    (.fillText context title x y)
    (draw-line context
               (->canvas (- x 2) (+ y 3))
               (->canvas (+ x 250 (:width (text-size context title))) (+ y 3))
               default-color)
    (doseq [[item offset] (map list items (iterate (fn [offset] (+ offset 18)) (- y 5)))]
      (.fillText context (str item) x (+ y offset)))))

(defn draw-waypoints [waypoints]
  (draw-section :title "WAYPOINTS" :coord (->canvas 30 30) :items waypoints)
  (let [context (get-context)]
    (doseq [wp waypoints]
      (let [loc (:location wp)]
        (draw-circle context loc 5)))))

(defn draw-event-log [events]
  (draw-section :title "EVENT LOG"
                :coord (->canvas 30 (+ 30 (/ (.-height (get-canvas)) 2)))
                :items events))

(defn draw [state]
  (clear-canvas!)
  (draw-crosshairs origin dark-color)
  (draw-crosshairs (get-in state [:mouse :location]))
  (draw-ui-elements (get-in state [:ui]))
  (draw-coordinates (get-in state [:mouse :location]))
  (draw-connection-info (get-in state [:connection]) (get-in state [:time]))
  (draw-state-info state)
  (draw-waypoints (get-in state [:waypoints]))
  (draw-event-log (get-in state [:events])))

;;;;;;;;;;
;; State

;; Add new handlers for state paths in {pre,pos}-draw-transforms. State transform functions are
;; registered along with an input path and an output path. The function is applied to the current
;; (last-frame) state of the input and output paths and the returned value is stored in the output
;; path.

(defn copy [in out] in)

(defn current-time [] (.getTime (js/Date.)))

(defn update-waypoints [mouse waypoints]
  (if (and (= :down (get (:buttons mouse) 0))
           (= :up (get (:previous-buttons mouse) 0)))
    (conj waypoints {:location (:location mouse)})
    waypoints))

(defn in-button? [btns btn pos]
  (let [{:keys [coord width height]} (button-render-details btns btn)
        {bx :x by :y} (canvas->world coord)
        {:keys [x y]} (canvas->world pos)]
    (and (< bx x (+ bx width))
         (< (- by height) y by))))

(defn update-button-hover [mouse-pos buttons]
  (mapv (fn [btn] (assoc btn :hover (in-button? buttons btn mouse-pos))) buttons))

(defn update-button-click [mouse buttons]
  ;; TODO Update the clicked state of buttons
  buttons
  )

(def pre-draw-transforms
  [
   [[:time] [:time] (fn [_ _] (current-time))]
   [[:mouse] [:waypoints] update-waypoints]
   [[:mouse :location] [:ui :buttons] update-button-hover]
   [[:mouse] [:buttons] update-button-click]
   ])

(def post-draw-transforms
  [
   [[:mouse :buttons] [:mouse :previous-buttons] copy]
   ])

(defn merge-maps [result latter]
  "Merge two maps into one, preserving overall structure."
  (if (and (map? result) (map? latter))
    (merge-with merge-maps result latter)
    latter))

(defn apply-state-transforms [state transforms]
  "Apply a series of transforms of the form [in-path out-path transform] to a state map and return
  the updated map."
  (let [state-updates (map (fn [[in-path out-path xform]]
                             (assoc-in {} out-path
                                       (xform (get-in state in-path)
                                              (get-in state out-path))))
                           transforms)]
    (apply merge-with merge-maps state state-updates)))

(defn on-state-change! []
  "Perform pre-draw transformations to application state."
  (swap! app-state apply-state-transforms pre-draw-transforms))

(defn post-draw []
  "Perform post-draw transformations to application state."
  (swap! app-state apply-state-transforms post-draw-transforms))

(defn on-event! []
  (on-state-change!)
  (post-draw))

(defn on-mouse-move! [event]
  "Handle mouse movement events."
  (swap! app-state update-in [:mouse :location]
         (fn [m] (canvas->world (->canvas (.-clientX event) (.-clientY event)))))
  (on-event!))

(defn on-mouse-down! [event]
  "Handle mouse down events."
  (swap! app-state update-in [:mouse :buttons (.-button event)] (constantly :down))
  (on-event!))

(defn on-mouse-up! [event]
  "Handle mouse up events."
  (swap! app-state update-in [:mouse :buttons (.-button event)] (constantly :up))
  (on-event!))

(defn on-resize! [event]
  (size-canvas-to-window!)
  (on-event!))

(defn check-heartbeat! []
  (let [now (current-time)
        latest (get-in @app-state [:connection :last-heartbeat])]
    (when (< heartbeat-interval (- now latest))
      (api/get-meta (fn [_]
                      (swap! app-state assoc :connection {:last-heartbeat (current-time)
                                                          :connected true}))
                    (fn [_]
                      (swap! app-state update-in [:connection :connected] (constantly false)))))))

(defn on-timer-tick! []
  "Handle timer ticks by triggering redraw of the application."
  (check-heartbeat!)
  (on-state-change!)
  (draw @app-state)
  (post-draw))

(defn main []
  (api/get-meta (fn [data] (.log js/console "API server uptime:" (:uptime data)))
                (fn [err] (.log js/console "Error getting server metadata:" (str err))))
  (.log js/console "Setting up")
  (size-canvas-to-window!)
  (set! (.-onmousemove (get-canvas)) on-mouse-move!)
  (set! (.-onmouseup (get-canvas)) on-mouse-up!)
  (set! (.-onmousedown (get-canvas)) on-mouse-down!)
  (set! (.-onresize js/window) on-resize!)
  (on-state-change!)
  (swap! timer-id #(.setInterval js/window on-timer-tick! (/ 1000 framerate))))
