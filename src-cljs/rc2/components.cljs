(ns rc2.components
  (:use-macros [dommy.macros :only [sel1]])
  (:require [reagent.core :as reagent :refer [atom]]
            [rc2.api :as api]
            [rc2.draw :as draw]
            [rc2.state :as state]))

(defn toggle-class [a k class1 class2]
  (if (= (@a k) class1)
    (swap! a assoc k class2)
    (swap! a assoc k class1)))

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
       (.log js/console "Mounting canvas")
       (let [canvas (sel1 :#target)]
         (state/attach-canvas-handlers canvas)
         (state/on-state-change!)))}))

(defn panel [side children]
  (let [default-class (str "panel " side)
        state (atom {:panel-class default-class})]
    (fn []
      (let [component (into []
                            (concat
                             [:div {:class (:panel-class @state)}] children
                             [[:div.tab {:class side
                                         :on-click (fn []
                                                     (.log js/console "Toggling panel state")
                                                     (toggle-class state :panel-class default-class
                                                                   (str default-class " invisible")))}
                               "Program"]]))]
        component))))

(def editor
  (with-meta (fn [text] [:div {:id "editor"} text])
    {:component-did-mount
     (fn []
       (.log js/console "Mounting editor")
       (let [editor (.edit js/ace "editor")]
         (.setTheme editor "ace/theme/monokai")
         (.setMode (.getSession editor) "ace/mode/clojure")
         (.setHighlightActiveLine editor true)))}))

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
     [label "time" (str (:time app-state))]
     [panel "right" [[editor ";; This is an editor"]
                     [:div#panel-buttons "Buttons will go here"]]]
     [panel "left" [";; This is a panel"
                    [:div#panel-buttons "Buttons will go here"]]]]))

(defn state-dump []
  [:div.app-state (str @state/app-state)])

(defn connection-info []
  (let [app-state @state/app-state]
   [:span#connection {:class (if (:connected (:connection app-state)) "normal" "error")}
    (if (:connected (:connection app-state)) "CONNECTED" "OFFLINE")]))










