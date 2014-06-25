(ns rc2.main
  (:require [dommy.core :as dommy]
            [rc2.api :as api])
  (:use-macros [dommy.macros :only [node sel sel1]]))

(def app-state (atom {:mouse {:location {} :buttons {0 :up 1 :up 2 :up}}}))
(def default-color "#C9EAF9";"#1AAE7C"
  )

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

;; Drawing functions

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
(defn draw-connection-info []
  (let [canvas (get-canvas)
        context (get-context)]
    (set! (.-fillStyle context) default-color)
    (set! (.-font context) "14px monospace")
    (let [text "CONNECTED"
          x (- (.-width canvas) (.-width (.measureText context text)) 30)
          y 30]
      (.fillText context text x y))))

;; TODO Draw waypoint details
(defn draw-waypoints []
  (let [context (get-context)
        text "WAYPOINTS"
        y 30]
    (set! (.-fillStyle context) default-color)
    (set! (.-font context) "14px monospace")
    (.fillText context text 30 y)
    (draw-line context 28 (+ 3 y) (+ 30 250 (.-width (.measureText context text))) (+ 3 y))))

;; TODO Draw event details
(defn draw-event-log []
  (let [canvas (get-canvas)
        context (get-context)
        text "EVENT LOG"
        y (+ 30 (/ (.-height canvas) 2))
        ]
    (set! (.-fillStyle context) default-color)
    (set! (.-font context) "14px monospace")
    (.fillText context text 30 y)
    (draw-line context 28 (+ 3 y) (+ 30 250 (.-width (.measureText context text))) (+ 3 y))))

(defn draw [state]
  (clear-canvas!)
  (draw-crosshairs (canvas-coords (get-in state [:mouse :location])))
  (draw-coordinates (canvas-coords (get-in state [:mouse :location])))
  (draw-connection-info)
  (draw-waypoints)
  (draw-event-log))

(defn update-state! []
  "Update state in the model."
  ;; TODO Add handlers here
  )

;; Event handlers

(defn on-state-change! []
  (update-state!)
  (draw @app-state))

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

(defn main []
  (api/get-meta (fn [data] (.log js/console "API server uptime:" (:uptime data)))
                (fn [err] (.log js/console "Error getting server metadata:" (str err))))
  (.log js/console "Setting up")
  (size-canvas-to-window!)
  (set! (.-onmousemove (get-canvas)) on-mouse-move!)
  (set! (.-onmouseup (get-canvas)) on-mouse-up!)
  (set! (.-onmousedown (get-canvas)) on-mouse-down!)
  (set! (.-onresize js/window) on-resize!)
  (on-state-change!))
