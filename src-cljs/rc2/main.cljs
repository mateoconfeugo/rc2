(ns rc2.main
  (:use-macros [dommy.macros :only [sel1]])
  (:require [reagent.core :as reagent]
            [rc2.api :as api]
            [rc2.draw :as draw]
            [rc2.state :as state]))

(def timer-id (atom nil))
(def framerate 40)

(defn on-timer-tick! []
  "Handle timer ticks by triggering redraw of the application."
  (state/update-periodic-tasks!)
  (state/on-state-change!))

(defn canvas-component []
  (let [canvas (sel1 :#target)]
    (draw/draw canvas @state/app-state))
  [:div "Canvas Component"])

(defn main []
  (api/get-meta (fn [data] (.log js/console "API server uptime:" (get data "uptime")))
                (fn [err] (.log js/console "Error getting server metadata:" (str err))))
  (.log js/console "Setting up")
  (let [body  (sel1 :body)
        canvas (sel1 :#target)]
    (state/attach-handlers body canvas)
    (state/on-state-change!))
  (reagent/render-component [canvas-component] (sel1 :#app))
  (swap! timer-id #(.setInterval js/window on-timer-tick! (/ 1000 framerate))))
