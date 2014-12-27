(ns rc2.components
  (:use-macros [dommy.macros :only [sel1]])
  (:require [reagent.core :as reagent]
            [rc2.api :as api]
            [rc2.draw :as draw]
            [rc2.state :as state]))

(defn- label
  "A simple label that contains text."
  ([id text] (label id text ""))
  ([id text classes]
     [:div.label {:id id :class classes} text]))

(defn- lighter [{:keys [primary secondary]}]
  "A mode information label showing primary and secondary modes."
  (let [primary-text (.-lighter primary)
        secondary-text (.-lighter secondary)
        text (str secondary-text " " primary-text " MODE")]
    [label "lighter" text]))

(defn- main-button [id text]
  "Clickable button element triggers state changes on click."
  [:button.ui-element
   {:id id
    :on-mouse-down #(state/on-button-click! id :down)
    :on-mouse-up #(state/on-button-click! id :up)} text])

(defn- section-item [text highlight]
  "A list item in a section."
  (let [class (if highlight "highlight" "")]
    [:li {:class class} text]))

(defn- section [id title items xform]
  "Component which displays a list of items with a title."
  [:div.ui-element.section {:id id}
   [:span.section-title title]
   [:ul (for [item items]
          [section-item (str (xform item)) (:highlight item)])]])

(defn- canvas []
  [:canvas {:id "target"}])

(def visualizer-canvas
  (with-meta canvas
    {:component-did-mount
     (fn []
       (let [canvas (sel1 :#target)]
         (state/attach-canvas-handlers canvas)
         (state/on-state-change!)))}))

(defn visualizer []
  "HTML5 canvas element which serves as a draw target for the route visualization."
  ;; We need to dereference the state atom here in order to get Reagent to re-render this component.
  (let [state @state/app-state]
   (when-let [canvas (sel1 :#target)]
     (draw/draw canvas state)))
  [:div {:hidden true} "[placeholder]"])

(defn ui-elements []
  "Top-level RC2 UI element."
  (let [app-state @state/app-state]
    [:div.ui-element
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
                          [main-button id (:text button)])]
     [lighter (:mode app-state)]
     [label "time" (str (:time app-state))]]))

(defn state-dump []
  [:div.app-state (str @state/app-state)])

(defn connection-info []
  (let [app-state @state/app-state]
   [:span#connection {:class (if (:connected (:connection app-state)) "normal" "error")}
    (if (:connected (:connection app-state)) "CONNECTED" "OFFLINE")]))
