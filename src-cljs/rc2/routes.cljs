(ns rc2.routes
  (:require [secretary.core :as secretary :refer-macros [defroute]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [rc2.state :as state]
            [rc2.components :as components])
  (:import goog.History))

(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn app-routes []
  (secretary/set-config! :prefix "#")

  (defroute "/" []
    (state/global-put! :current-page :home)
    (state/global-put! :nav "home"))

  (defroute "/plan" []
    (state/global-put! :current-page :plan)
    (state/global-put! :nav "home"))

  (hook-browser-navigation!))
