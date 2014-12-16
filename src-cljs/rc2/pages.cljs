(ns rc2.pages
  (:require [reagent.core :as reagent]
            [rc2.components :as components]))


(defn home-page []
  [:div [:h2 "Home"]])

(defn planner-page []
  [:div
   [components/ui-elements]
   [components/visualizer]])

(defn page-renderer [page]
  (get {:home home-page
        :plan planner-page}
       page))
