(ns rc2.main
  (:require [dommy.core :as dommy]
            [rc2.api :as api])
  (:use-macros [dommy.macros :only [node sel sel1]]))

(def timer-id (atom nil))
(def app-state (atom {:mouse {:location {} :buttons {0 :up 1 :up 2 :up}}
                      :waypoints ["foo" "bar" "baz"]
                      :events []
                      :connection {:last-heartbeat 0
                                   :connected false}}))

(def default-color "#C9EAF9") ;;"#1AAE7C"
(def timer-interval (/ 1000 30))
(def heartbeat-interval (* 1000 3))


;; Conversion utilities
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

(defn canvas-coords [{:keys [x y]}]
  "Convert screen coordinates to canvas coordinates."
  (let [canvas (get-canvas)
        bounding-box (.getBoundingClientRect canvas)]
    {:x (* (- x (.-left bounding-box)) (/ (.-width canvas) (.-width bounding-box)))
     :y (* (- y (.-top bounding-box)) (/ (.-height canvas) (.-height bounding-box)))}))

(defn- color->style [{:keys [r g b a]}]
  "Convert a map of rgba values to a color style for use in canvas elements."
  (str "rgba(" r "," g "," b "," a ")"))

;; Drawing functions. These functions should not modify application state, they should only render
;; it to the screen. Updates to state should happen in on-state-change! below.

(defn draw-rect! [[width height] [x y] color]
  (let [ctx (get-context)]
    (set! (.-fillStyle ctx) (color->style color))
    (.fillRect ctx x y width height)))

(defn draw-line [context x1 y1 x2 y2]
  (.beginPath context)
  (.moveTo context x1 y1)
  (.lineTo context x2 y2)
  (.stroke context))

(defn clear-canvas! []
  (let [ctx (get-context)
        canvas (get-canvas)]
    (.clearRect ctx 0 0 (.-width canvas) (.-height canvas))))

(defn text-size [context text]
  {:width (.-width (.measureText context text))
   :height (.-height (.measureText context text))})

(defn draw-crosshairs [{:keys [x y]}]
  (let [context (get-context)]
    (set! (.-strokeStyle context) default-color)
    (set! (.-lineWidth context) 1.0)
    (draw-line context 0 y (.-width (.-canvas context)) y)
    (draw-line context x 0 x (.-height (.-canvas context)))))

(defn draw-coordinates [{:keys [x y] :as coords}]
  (let [context (get-context)]
    (set! (.-fillStyle context) default-color)
    (set! (.-font context) "12px monospace")
    (.fillText context (str coords) (+ x 10) (- y 10))))

;; TODO Set up heartbeat ping to server and update connection text based on it
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

(defn draw-section [& {:keys [title x y items]}]
  "Draw a section of text on the screen containing the given items.

A section consists of a title followed by each item in items. If there is not enough space to draw
all of the items, items from the end of the list will be preferred." ;; Scrolling?
  (let [canvas (get-canvas)
        context (get-context)]
    (set! (.-fillStyle context) default-color)
    (set! (.-font context) "14px monospace")
    (.fillText context title x y)
    (draw-line context (- x 2) (+ y 3) (+ x 250 (:width (text-size context title))) (+ y 3))
    (doseq [[item offset] (map list items (iterate (fn [offset] (+ offset 18)) (- y 5)))]
      (.fillText context (str item) x (+ y offset)))))

;; TODO Draw waypoint details
(defn draw-waypoints [waypoints]
  (draw-section :title "WAYPOINTS" :x 30 :y 30 :items waypoints))

;; TODO Draw event details
(defn draw-event-log [events]
  (draw-section :title "EVENT LOG" :x 30 :y (+ 30 (/ (.-height (get-canvas)) 2)) :items events))

(defn draw [state]
  (clear-canvas!)
  (draw-crosshairs (canvas-coords (get-in state [:mouse :location])))
  (draw-coordinates (canvas-coords (get-in state [:mouse :location])))
  (draw-connection-info (get-in state [:connection]) (get-in state [:time]))
  (draw-waypoints (get-in state [:waypoints]))
  (draw-event-log (get-in state [:events])))

;; Event handlers. Computed state changes go in on-state-change!.

(defn current-time [] (.getTime (js/Date.)))

(defn on-state-change! []
  (swap! app-state
         (fn [state]
           (-> state
               (update-in [:time] current-time))))
  )

(defn on-mouse-move! [event]
  "Handle mouse movement events."
  (swap! app-state update-in [:mouse :location]
         (fn [m] (assoc m :x (.-clientX event) :y (.-clientY event))))
  (on-state-change!))

(defn on-mouse-down! [event]
  "Handle mouse down events."
  (swap! app-state update-in [:mouse :buttons (.-button event)] (constantly :down))
  (on-state-change!))

(defn on-mouse-up! [event]
  "Handle mouse up events."
  (swap! app-state update-in [:mouse :buttons (.-button event)] (constantly :up))
  (on-state-change!))

(defn on-resize! [event]
  (size-canvas-to-window!)
  (on-state-change!))

(defn check-heartbeat! []
  (let [now (current-time)
        latest (get-in @app-state [:connection :last-heartbeat])]
    (when (< heartbeat-interval (- now latest))
      (.log js/console "Sending heartbeat request")
      (api/get-meta (fn [_]
                      (.log js/console "Got heartbeat")
                      (swap! app-state assoc :connection {:last-heartbeat (current-time)
                                                          :connected true}))
                    (fn [_]
                      (.log js/console "No heartbeat, switching to offline mode")
                      (swap! app-state update-in [:connection :connected] (constantly false)))))))

(defn on-timer-tick! []
  "Handle timer ticks by triggering redraw of the application."
  (check-heartbeat!)
  (on-state-change!)
  (draw @app-state))

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
  (swap! timer-id #(.setInterval js/window on-timer-tick! timer-interval)))
