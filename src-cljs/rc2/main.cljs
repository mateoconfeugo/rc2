(ns rc2.main
  (:require [rc2.api :as api]
            [rc2.draw :as draw]
            [rc2.state :as state]))

(def timer-id (atom nil))
(def framerate 40)

(defn on-timer-tick! []
  "Handle timer ticks by triggering redraw of the application."
  (state/check-heartbeat!)
  (draw/draw (state/on-state-change!))
  (state/post-draw))

(defn main []
  (api/get-meta (fn [data] (.log js/console "API server uptime:" (get data "uptime")))
                (fn [err] (.log js/console "Error getting server metadata:" (str err))))
  (.log js/console "Setting up")
  (draw/size-canvas-to-window!)
  (state/attach-handlers)
  (state/on-state-change!)
  (swap! timer-id #(.setInterval js/window on-timer-tick! (/ 1000 framerate))))
