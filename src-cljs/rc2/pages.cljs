(ns rc2.pages
  (:require [reagent.core :as reagent]
            [rc2.components :as components]))


(defn home-page []
  [:div.menu
   [:h1 "RC2 Home"]
   [:a {:href "/#/plan"} "Planner"]
   ])

(defn planner-page []
  [:div
   [components/visualizer-canvas]
   [components/visualizer]
   [components/ui-elements]])

(defn state-page []
  [:div
   [:h1 "Debug: State"]
   [components/state-dump]])

(defn page-renderer [page]
  (get {:home home-page
        :plan planner-page
        :debug.state state-page}
       page))
