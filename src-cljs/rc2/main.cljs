(ns rc2.main
  (:use-macros [dommy.macros :only [sel1]])
  (:require [reagent.core :as reagent]
            [rc2.api :as api]
            [rc2.draw :as draw]
            [rc2.state :as state]
            [rc2.routes :as routes]
            [rc2.pages :as pages]
            [rc2.components :as components]))

(def timer-id (atom nil))
(def framerate 40)

(defn on-timer-tick! []
  "Handle timer ticks by triggering redraw of the application."
  (state/update-periodic-tasks!)
  (state/on-state-change!))

(defn page-render []
  [:div.container
   [pages/header]
   [(pages/page-renderer (:current-page @state/app-state))]])

(defn page-component []
  (reagent/create-class {:component-will-mount routes/app-routes
                         :render page-render}))

(defn ^:export main []
  (api/get-meta (fn [data] (.log js/console "API server uptime:" (get data :uptime)))
                (fn [err] (.log js/console "Error getting server metadata:" (str err))))
  (.log js/console "Setting up")
  (reagent/render-component [page-component] (.getElementById js/document "app"))
  (swap! timer-id #(.setInterval js/window on-timer-tick! (/ 1000 framerate))))
