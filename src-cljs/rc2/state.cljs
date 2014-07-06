(ns rc2.state
  (:require [rc2.api :as api]
            [rc2.draw :as draw]
            [rc2.util :as util]))

(def heartbeat-interval (* 1000 3))
(def heartbeat-timeout (* heartbeat-interval 3))
(def task-update-interval 500) ;; Check for task state changes every 500ms.

(declare plan-route!)

(def app-state
  (atom {
         :mouse {:location util/origin
                 :buttons {0 :up 1 :up 2 :up}
                 :previous-buttons {0 :up 1 :up 2 :up}}
         :keyboard {:pressed #{}}
         :waypoints []
         :plan []
         :events []
         :ui {
              ;; Buttons have a name (rendered on the screen), a target vector, and a
              ;; transform function. When the button is clicked the transform function
              ;; is called with the value in this state tree at the 'target' path,
              ;; which is then replaced with the returned value.
              :buttons [{:text "Plan" :target [:waypoints] :hover false :click false
                         :xform (fn [waypoints]
                                  (plan-route! waypoints)
                                  waypoints)}
                        {:text "Start" :target [:running] :hover false :click false
                         :xform (constantly true)}
                        {:text "Stop" :target [:running] :hover false :click false
                         :xform (constantly false)}
                        {:text "Clear" :target [:waypoints] :hover false :click false
                         :xform (constantly [])}]
              }
         :mode {:primary  :insert
                :secondary :sink}
         :tasks {:pending []
                 :complete []
                 :last-poll 0}
         :connection {:last-heartbeat 0 :connected false}
         :time 0
         :running false
         }))

;;;;;;;;;;
;; State

;; Add new handlers for state paths in {pre,pos}-draw-transforms. State transform functions are
;; registered along with an input path and an output path. The function is applied to the current
;; (last-frame) state of the input and output paths and the returned value is stored in the output
;; path.

(defn merge-maps [result latter]
  "Merge two maps into one, preserving overall structure."
  (if (and (map? result) (map? latter))
    (merge-with merge-maps result latter)
    latter))

(defn apply-state-transforms [state transforms]
  "Apply a series of transforms of the form [in-path out-path transform] to a state map and return
  the updated map."
  (reduce (fn [prev-state [in-path out-path xform]]
            (let [xform-state (xform (get-in prev-state in-path)
                                     (get-in prev-state out-path))]
              (if (empty? out-path)
                xform-state
                (assoc-in prev-state out-path xform-state))))
          state
          transforms))

(defn copy [in out] in)

(defn debug-print [in out]
  (.log js/console "debug-print in:" (str in) "out:" (str out))
  out)

(defn current-time [] (.getTime (js/Date.)))

(defn clicked? [mouse button]
  (and (= :down (get (:buttons mouse) button))
       (= :up (get (:previous-buttons mouse) button))))

(defn handle-new-waypoints [state waypoints]
  (let [mouse (:mouse state)
        buttons (get-in state [:ui :buttons])]
    (if (and (not (some #(:hover %) buttons))
             (clicked? mouse 0))
      (if (= :insert (get-in state [:mode :primary]))
        (conj waypoints {:location (:location mouse)
                         :highlight true
                         :kind (get-in state [:mode :secondary])
                         :part-id 1}) ;; TODO Fix part IDs
        (filter #(not (:highlight %)) waypoints))
      waypoints)))

(defn highlight-waypoints [mouse waypoints]
  (let [mouse (util/canvas->world mouse)]
    (map (fn [wp]
           (assoc wp :highlight (< (util/distance (:location wp) mouse)
                                   (+ draw/waypoint-radius 10))))
         waypoints)))

(defn in-button? [btns btn pos]
  (let [{:keys [coord width height]} (draw/button-render-details btns btn)
        {bx :x by :y} (util/canvas->world coord)
        {:keys [x y]} (util/canvas->world pos)]
    (and (< bx x (+ bx width))
         (< (- by height) y by))))

(defn update-button-hover [mouse-pos buttons]
  (mapv (fn [btn] (assoc btn :hover (in-button? buttons btn mouse-pos))) buttons))

(defn update-button-click [mouse buttons]
  "Update the clicked state of UI buttons."
  (mapv (fn [btn] (assoc btn :click (and (clicked? mouse 0) (:hover btn)))) buttons))

(defn handle-button-actions [buttons state]
  "Perform the on-click actions of the clicked UI buttons."
  (let [transforms (filterv
                    (comp not nil?)
                    (mapv (fn [btn] [(:target btn) (:target btn) (:xform btn)])
                          (filterv #(:click %) buttons)))]
    (apply-state-transforms state transforms)))

(defn handle-mode-keys [keys primary]
  "Set the primary mode based on the current keys."
  (condp #(contains? %2 %1) keys
    \D :delete
    \I :insert
    primary))

(defn handle-secondary-mode-keys [keys mode]
  "Set the secondary mode based on the current keys."
  (let [primary-mode (:primary mode)
        current-secondary (:secondary mode)
        secondary-mode (if (= :insert primary-mode)
                         (condp #(contains? %2 %1) keys
                           \P :source
                           \S :sink
                           (or current-secondary :sink))
                         nil)]
    (assoc mode :secondary secondary-mode)))

(def pre-draw-transforms
  [
   [[:time] [:time] (fn [_ _] (current-time))]
   [[:keyboard :pressed] [:mode :primary] handle-mode-keys]
   [[:keyboard :pressed] [:mode] handle-secondary-mode-keys]
   [[:mouse :location] [:ui :buttons] update-button-hover]
   [[:mouse] [:ui :buttons] update-button-click]
   [[:ui :buttons] [] handle-button-actions]
   [[] [:waypoints] handle-new-waypoints]
   [[:mouse :location] [:waypoints] highlight-waypoints]
   ])

(def post-draw-transforms
  [
   [[:mouse :buttons] [:mouse :previous-buttons] copy]
   ])

(defn on-state-change! []
  "Perform pre-draw transformations to application state."
  (swap! app-state apply-state-transforms pre-draw-transforms))

(defn post-draw []
  "Perform post-draw transformations to application state."
  (swap! app-state apply-state-transforms post-draw-transforms))

(defn on-event! []
  (on-state-change!)
  (post-draw))

(defn on-mouse-move! [event]
  "Handle mouse movement events."
  (swap! app-state update-in [:mouse :location]
         (fn [m] (util/canvas->world (util/->canvas (.-clientX event) (.-clientY event)))))
  (on-event!))

(defn on-mouse-down! [event]
  "Handle mouse down events."
  (swap! app-state update-in [:mouse :buttons (.-button event)] (constantly :down))
  (on-event!))

(defn on-mouse-up! [event]
  "Handle mouse up events."
  (swap! app-state update-in [:mouse :buttons (.-button event)] (constantly :up))
  (on-event!))

(defn on-key-down! [event]
  "Handle key down events."
  (swap! app-state update-in [:keyboard :pressed] conj (.fromCharCode js/String (.-keyCode event)))
  (on-event!))

(defn on-key-up! [event]
  "Handle key up events."
  (swap! app-state update-in [:keyboard :pressed] disj (.fromCharCode js/String (.-keyCode event)))
  (on-event!))

(defn on-resize! [event]
  "Handle resize events."
  (draw/size-canvas-to-window!)
  (on-event!))

(defn on-task-completion [app-state task]
  "Handle task completion events."
  (let [type (get task "type")
        result (get task "result")]
    (.log js/console type " complete: " (str result))
    (cond
     (= "plan" type) (assoc app-state :plan result)
     :else app-state)))

(defn start-task! [task]
  "Send a task to the server and add its ID to the pending task list."
  (api/add-task! task
                 (fn [resp]
                   (.log js/console "Started task " (str resp) "id:" (get resp "id"))
                   (swap! app-state update-in [:tasks :pending] #(conj % (get resp "id"))))
                 (fn [resp] (.log js/console "Failed to add task " (str task)))))

(defn clean-waypoint [waypoint]
  (let [{:keys [location kind part-id]} waypoint
        {:keys [x y]} location]
   {:x x :y y :z 0 :kind kind :part-id part-id}))

(defn plan-route! [waypoints]
  "Send a request to the server to plan a route using the current waypoints."
  (start-task! {:type :plan :waypoints (mapv clean-waypoint waypoints)}))

(defn check-heartbeat! []
  (let [now (current-time)
        latest (get-in @app-state [:connection :last-heartbeat])
        time-since-heartbeat (- now latest)]
    (when (< heartbeat-interval time-since-heartbeat)
      (api/get-meta (fn [_]
                      (swap! app-state assoc :connection {:last-heartbeat (current-time)
                                                          :connected true}))
                    (fn [_]
                      (swap! app-state update-in [:connection :connected] (constantly false)))))
    (when (< heartbeat-timeout time-since-heartbeat)
      (swap! app-state update-in [:connection :connected] (constantly false)))))

(defn update-task-state [app-state task]
  (let [state (get task "state")
        id (get task "id")]
    (if (= "complete" state)
      (-> app-state
          (on-task-completion task)
          (update-in [:tasks :pending] (fn [ids] (filterv (fn [x] (not (= id x))) ids)))
          (update-in [:tasks :complete] (fn [ids] (conj ids id))))
      app-state)))

(defn check-tasks! []
  (let [now (current-time)
        latest (get-in @app-state [:tasks :last-poll])
        time-since-poll (- now latest)]
    (when (< task-update-interval time-since-poll)
      (doseq [id (get-in @app-state [:tasks :pending])]
        (api/get-task
         id
         (fn [resp]
           (swap! app-state update-task-state resp))
         (fn [err]
           (swap! app-state update-in [:connection :connected] (constantly false)))))
      (swap! app-state update-in [:tasks :last-poll] (constantly now)))))

(defn attach-handlers []
  (set! (.-onmousemove (util/get-canvas)) on-mouse-move!)
  (set! (.-onmouseup (util/get-canvas)) on-mouse-up!)
  (set! (.-onmousedown (util/get-canvas)) on-mouse-down!)
  (set! (.-onkeydown (util/get-body)) on-key-down!)
  (set! (.-onkeyup (util/get-body)) on-key-up!)
  (set! (.-onresize js/window) on-resize!))
