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

(defn visualizer []
  (let [canvas (sel1 :#target)]
    (draw/draw canvas @state/app-state))
  [:div "[placeholder]"])

(defn main-button [id text]
  [:button.ui-element {:id id
                       :on-mouse-down #(state/on-button-click! id :down)
                       :on-mouse-up #(state/on-button-click! id :up)} text])

(defn section-item [text highlight]
  (let [class (if highlight "highlight" "")]
    [:li {:class class} text]))

(defn section [id title items xform]
  "Component which displays a list of items with a title."
  [:div.ui-element.section {:id id}
   [:span.section-title title]
   [:ul (for [item items]
          [section-item (str (xform item)) (:highlight item)])]])

(defn ui-elements []
  (let [app-state @state/app-state]
    [:div
     [section "waypoint-list" "WAYPOINTS"
           (draw/indexed (get-in app-state [:route :waypoints]))
           (partial draw/get-waypoint-text (get app-state :parts)
                    (get-in app-state [:route :execution :current]))]
     [section "part-list" "PARTS"
      (sort-by :id (map (fn [[id part]] (assoc part :id id)) (get app-state :parts)))
      (fn [part] (str (:id part) ": " (:name part)))]
     [section "plan-list" "PLAN"
      (draw/indexed (get-in app-state [:route :plan]))
      (partial draw/get-waypoint-text (get app-state :parts)
               (get-in app-state [:route :execution :current]))]
     [:div#main-buttons (for [[id button] (filter (fn [[id btn]]
                                                    (when-let [pred (:visible-when btn)]
                                                              (pred app-state)))
                                                  (get-in app-state [:ui :buttons]))]
                          [main-button id (:text button)])]]))

(defn main []
  (api/get-meta (fn [data] (.log js/console "API server uptime:" (get data "uptime")))
                (fn [err] (.log js/console "Error getting server metadata:" (str err))))
  (.log js/console "Setting up")
  (let [body  (sel1 :body)
        canvas (sel1 :#target)]
    (state/attach-handlers body canvas)
    (state/on-state-change!))
  (reagent/render-component [ui-elements] (sel1 :#ui-elements))
  (reagent/render-component [visualizer] (sel1 :#placeholder))
  (swap! timer-id #(.setInterval js/window on-timer-tick! (/ 1000 framerate))))
