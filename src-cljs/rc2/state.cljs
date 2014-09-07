(ns rc2.state
  (:require [rc2.api :as api]
            [rc2.draw :as draw]
            [rc2.util :as util]
            [clojure.set :as set]))

(declare plan-route!)
(declare resume-execution!)
(declare pause-execution!)
(declare stop-execution!)

(def heartbeat-interval (* 1000 3))
(def heartbeat-timeout (* heartbeat-interval 3))
(def task-update-interval 500) ;; Check for task state changes every 500ms.
(def position-update-interval 500) ;; Check for position state changes every 500ms.

(defprotocol Mode
  "Protocol for interacting with RC2 input modes."
  (enter [this state] "Enable the given mode.")
  (exit [this state] "Disable the given mode.")
  (handle-keypress [this state key] "Handle a keypress while the given mode is active."))

;; A primary or secondary mode, like insert, edit, source or sink.
;; name: the name of the mode, e.g. :execute
;; kind: the type of mode this is. One of :primary or :secondary.
;; keys: a map from key to transform function, where the function takes the current mode and state
;; and returns an updated state.
;; lighter: a string to display in the UI when this mode is active
;; enter-fns: a collection of functions to call when the mode is entered. These functions should
;; take the current state and return an updated state.
;; exit-fns: a collection of functions to call when the mode exits. These functions should take the
;; current state and return an updated state.
(defrecord InputMode [keyword kind keys lighter enter-fns exit-fns]
  Mode
  (enter [this state]
    (.log js/console "Entering" lighter "mode")
    (-> state
        (#(reduce (fn [s f] (f s)) % enter-fns))
        (assoc-in [:mode (.-kind this)] this)))
  (exit [this state]
    (.log js/console "Exiting" lighter "mode")
    (reduce (fn [s f] (f s)) state exit-fns))
  (handle-keypress [this key state]
    (if-let [handler (get (.-keys this) key)]
      (handler state)
      state)))

(declare enter-mode)

;; Minor modes
(def default-mode (->InputMode :default :secondary {} "" [] []))
(def source-mode (->InputMode :source :secondary {} "SOURCE" [] []))
(def sink-mode (->InputMode :sink :secondary {} "SINK" [] []))
(def pause-mode (->InputMode :pause :secondary {} "PAUSE" [] []))
(def run-mode (->InputMode :run :secondary {} "RUN" [] []))

;; Major modes
(def edit-mode (->InputMode :edit :primary
                            {\newline #(enter-mode :insert %)
                             \return #(enter-mode :insert %)
                             \formfeed #(enter-mode :insert %)}
                            "EDIT"
                             [#(enter-mode :default %)] []))
(def delete-mode (->InputMode :delete :primary
                              {\I #(enter-mode :insert %)}
                              "DELETE"
                              [#(enter-mode :default %)] []))
(def insert-mode (->InputMode :insert :primary
                              {\D #(enter-mode :delete %)
                               \E #(enter-mode :edit %)
                               \P #(enter-mode :source %)
                               \S #(enter-mode :sink %)}
                              "INSERT"
                              [#(enter-mode :sink %)] []))
(def execute-mode (->InputMode :execute :primary
                               {\backspace #(enter-mode :insert %)
                                \newline #(enter-mode :run %)
                                \return #(enter-mode :run %)
                                \formfeed #(enter-mode :run %)
                                \space #(enter-mode :pause %)}
                               "EXECUTE"
                               [#(enter-mode :run %)] []))

(defn enter-mode [mode state]
  (enter (condp = mode
           :default default-mode
           :insert insert-mode
           :delete delete-mode
           :edit edit-mode
           :source source-mode
           :sink sink-mode
           :run run-mode
           :pause pause-mode)
         state))

(def default-animation-state {:index 0
                              :offsets (util/->world 0 0)})
(def animation-step-distance 5)

;; TODO Refactor the UI components out of this state tree. Buttons should probably implement some
;; kind of protocol and have a generic interface. Probably would be useful to encapsulate some of
;; the other chunks of information here.

(def app-state
  (atom {
         :mouse {:location util/origin
                 :buttons {0 :up 1 :up 2 :up}
                 :previous-buttons {0 :up 1 :up 2 :up}}
         :keyboard {:pressed #{}
                    :previous-pressed #{}}
         :route {:waypoints []
                 :plan []
                 :animation default-animation-state
                 :execution {:current 0
                             :plan []}}
         :parts {0 {:name "DEFAULT" :highlight true}}
         :ui {
              ;; Buttons have a name (rendered on the screen), a target vector, and a
              ;; transform function. When the button is clicked the transform function
              ;; is called with the value in this state tree at the 'target' path,
              ;; which is then replaced with the returned value.
              :buttons {:plan {:text "Plan"
                               :target [:route :waypoints]
                               :hover false
                               :click false
                               :xform (fn [waypoints]
                                        (plan-route! waypoints)
                                        waypoints)
                               :visible-when (constantly true)}
                        :start {:text "Start"
                                :target []
                                :hover false
                                :click false
                                :xform
                                (fn [state]
                                  (-> state
                                      (#(enter execute-mode %))
                                      (#(enter run-mode %))
                                      (update-in [:route] resume-execution!)))
                                :visible-when (fn [state]
                                                (not
                                                 (= run-mode (get-in state [:mode :secondary]))))}
                        :pause {:text "Pause"
                                :target []
                                :hover false
                                :click false
                                :xform
                                (fn [state]
                                  (-> state
                                      (#(enter execute-mode %))
                                      (#(enter pause-mode %))
                                      (update-in [:route] pause-execution!)))
                                :visible-when (fn [state]
                                                (and
                                                 (= execute-mode (get-in state [:mode :primary]))
                                                 (= run-mode (get-in state [:mode :secondary]))))}
                        :stop {:text "Stop"
                               :target []
                               :hover false
                               :click false
                               :xform
                               (fn [state]
                                 (let [mode (:mode state)]
                                   (if (= execute-mode (:primary mode))
                                     (-> state
                                         (update-in [:route] stop-execution!)
                                         (#(enter insert-mode %))
                                         (#(enter sink-mode %)))
                                     state)))
                               :visible-when (constantly true)}
                        :clear {:text "Clear"
                                :target [:route]
                                :hover false
                                :click false
                                :xform (fn [route] (assoc route :waypoints [] :plan []))
                                :visible-when (constantly true)}}}
         :mode {:primary insert-mode
                :secondary sink-mode}
         :robot {:position (util/->world 0 0)
                 :last-poll 0}
         :tasks {:pending []
                 :complete []
                 :last-poll 0}
         :connection {:last-heartbeat 0 :connected false}
         :time 0
         }))

;;;;;;;;;;
;; State

;; Add new handlers for state paths in {pre,pos}-draw-transforms. State transform functions are
;; registered along with an input path and an output path. The function is applied to the current
;; (last-frame) state of the input and output paths and the returned value is stored in the output
;; path.

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

(defn highlighted? [m]
  (:highlight m))

(defn clicked? [mouse button]
  (and (= :down (get (:buttons mouse) button))
       (= :up (get (:previous-buttons mouse) button))))

(defn get-selected-part-id [parts]
  (first (first (filter (fn [[k v]] (highlighted? v)) parts))))

(defn handle-waypoint-updates [state route]
  (let [mouse (:mouse state)
        buttons (vals (get-in state [:ui :buttons]))
        waypoints (:waypoints state)]
    (if (and (not (some :hover buttons))
             (clicked? mouse 0))
      (cond
       (and (= insert-mode (get-in state [:mode :primary]))
            (get-selected-part-id (:parts state)))
       (update-in route [:waypoints]
                  conj {:location (:location mouse)
                        :highlight true
                        :kind (.-keyword (get-in state [:mode :secondary]))
                        :part-id (get-selected-part-id (:parts state))})
       (= delete-mode (get-in state [:mode :primary]))
       (-> route
           (update-in [:waypoints] (fn [wps] (filter #(not (:highlight %)) wps)))
           (assoc :plan []))
       :else route)
      route)))

(defn highlight-waypoints [mouse waypoints]
  (let [mouse (util/canvas->world mouse)]
    (map (fn [wp]
           (assoc wp :highlight (< (util/distance (:location wp) mouse)
                                   (+ draw/waypoint-radius 10))))
         waypoints)))

(defn update-button-visibilities [state buttons]
  (into {}
        (map
         (fn [[id btn]]
           (let [handler (:visible-when btn)
                 is-visible (handler state)]
             [id (assoc btn :visible is-visible)]))
         buttons)))

(defn in-button? [btns btn pos]
  (let [{:keys [coord width height]} (draw/button-render-details btns btn)
        {bx :x by :y} (util/canvas->world coord)
        {:keys [x y]} (util/canvas->world pos)]
    (if (:visible btn)
        (and (< bx x (+ bx width))
             (< (- by height) y by))
        false)))

(defn update-button-hover [mouse-pos buttons]
  (into {} (map (fn [[k btn]] [k (assoc btn :hover
                                        (in-button? (vals buttons) btn mouse-pos))])
                buttons)))

(defn update-button-click [mouse buttons]
  "Update the clicked state of UI buttons."
  (into {} (map (fn [[k btn]]
                  (when (and (clicked? mouse 0) (:hover btn))
                      (.log js/console (:text btn) "clicked"))
                  [k (assoc btn :click (and (clicked? mouse 0) (:hover btn)))])
                buttons)))

(defn handle-button-actions [buttons state]
  "Perform the on-click actions of the clicked UI buttons."
  (let [buttons (vals buttons)
        transforms (filterv
                    (comp not nil?)
                    (mapv (fn [btn] [(:target btn) (:target btn) (:xform btn)])
                          (filterv #(:click %) buttons)))]
    (apply-state-transforms state transforms)))

(defn handle-part-keys [keys state]
  "Set the selected part based on the current keys."
  (let [parts (:parts state)
        primary-mode (:primary (:mode state))
        selected-part (get-selected-part-id (:parts state))]
    (if-let [part-num (first (->> keys
                                  (map js/parseInt)
                                  (filter (fn [k] (not (js/isNaN k))))))]
      (if (or (= edit-mode primary-mode) (contains? parts part-num))
        (-> state
            ((fn [s] (if selected-part
                       (assoc-in s [:parts selected-part :highlight] false)
                       s)))
            (assoc-in [:parts part-num :highlight] true))
        state)
      state)))

(defn handle-mode-keys [pressed-keys state]
  "Update state based on the current mode's keybindings."
  (let [primary (get-in state [:mode :primary])
        secondary (get-in state [:mode :secondary])]
    (-> state
        (#(reduce (fn [s k] (handle-keypress primary k s)) % pressed-keys))
        (#(reduce (fn [s k] (handle-keypress secondary k s)) % pressed-keys)))))

(defn handle-edit-mode-keys [keys state]
  "Handle keypresses in edit mode."
  (if (= edit-mode (get-in state [:mode :primary]))
    (let [new-keys (set/difference (get-in state [:keyboard :pressed])
                                   (get-in state [:keyboard :previous-pressed]))
          new-keys (filter (fn [k] (js/isNaN (js/parseInt k))) new-keys)
          new-keys (filter (fn [k] (not (contains? (keys (.-keys edit-mode)) k))) new-keys)
          part-id (get-selected-part-id (:parts state))]
      (if part-id
        (-> state
            (update-in [:parts part-id :name]
                       (fn [name]
                         (reduce (fn [n c] (if (= "\b" c) (apply str (butlast n)) (str n c)))
                                 name new-keys))))
        state))
    state))

(defn handle-delete-mode-keys [_ state]
  "Handle keypresses in delete mode."
  (if (= delete-mode (get-in state [:mode :primary]))
    (let [new-keys (set/difference (get-in state [:keyboard :pressed])
                                   (get-in state [:keyboard :previous-pressed]))
          part-id (get-selected-part-id (:parts state))]
      (if (contains? new-keys \backspace)
        (-> state
            ;; Remove the current part
            (update-in [:parts] dissoc part-id)
            ;; Re-highlight the first part
            (update-in [:parts] (fn [parts]
                                  (if-let [key (first (keys parts))]
                                    (assoc-in parts [key :highlight] true)
                                    parts))))
        state))
    state))

(defn update-plan-annotations [waypoints plan]
  (let [loc->wp (into {} (map (fn [wp] [(:location wp) wp]) waypoints))]
    (map (partial get loc->wp) (map #(:location %) plan))))

(defn update-plan-animation [plan anim-state]
  (if (not (empty? plan))
    (let [last-point (:location (nth plan (:index anim-state)))
          next-point (:location (nth plan (+ 1 (:index anim-state))))
          current-offsets (:offsets anim-state)
          current-location (util/coord+ current-offsets last-point)
          next-offsets (util/coord+
                        current-offsets
                        (util/scale-to (util/coord- next-point last-point) animation-step-distance))
          next-location (util/coord+ next-offsets last-point)
          past-next (< (util/distance last-point next-point)
                       (util/distance last-point next-location))]
      (if (not past-next)
        (assoc anim-state :offsets next-offsets)
        (assoc anim-state
          :offsets util/origin
          :index (mod (+ 1 (:index anim-state)) (- (count plan) 1)))))
    default-animation-state))

(def pre-draw-transforms
  [
   [[:time] [:time] (fn [_ _] (util/current-time))]
   [[:keyboard :pressed] [] handle-edit-mode-keys]
   [[:keyboard :pressed] [] handle-delete-mode-keys]
   [[:keyboard :pressed] [] handle-mode-keys]
   [[:keyboard :pressed] [] handle-part-keys]
   [[] [:ui :buttons] update-button-visibilities]
   [[:mouse :location] [:ui :buttons] update-button-hover]
   [[:mouse] [:ui :buttons] update-button-click]
   [[:ui :buttons] [] handle-button-actions]
   [[] [:route] handle-waypoint-updates]
   [[:mouse :location] [:route :waypoints] highlight-waypoints]
   [[:route :waypoints] [:route :plan] update-plan-annotations]
   [[:route :plan] [:route :animation] update-plan-animation]
   [[:mouse :buttons] [:mouse :previous-buttons] copy]
   [[:keyboard :pressed] [:keyboard :previous-pressed] copy]
   ])

(defn on-state-change! []
  "Perform pre-draw transformations to application state."
  (swap! app-state apply-state-transforms pre-draw-transforms))

(defn on-event! []
  (on-state-change!))

(defn on-mouse-move! [event]
  "Handle mouse movement events."
  (.preventDefault event)
  (swap! app-state update-in [:mouse :location]
         (fn [m] (util/canvas->world (util/->canvas (.-clientX event) (.-clientY event)))))
  (on-event!))

(defn on-mouse-down! [event]
  "Handle mouse down events."
  (.preventDefault event)
  (swap! app-state update-in [:mouse :buttons (.-button event)] (constantly :down))
  (on-event!))

(defn on-mouse-up! [event]
  "Handle mouse up events."
  (.preventDefault event)
  (swap! app-state update-in [:mouse :buttons (.-button event)] (constantly :up))
  (on-event!))

(defn on-key-down! [event]
  "Handle key down events."
  (.preventDefault event)
  (swap! app-state update-in [:keyboard :pressed] conj (.fromCharCode js/String (.-keyCode event)))
  (on-event!))

(defn on-key-up! [event]
  "Handle key up events."
  (.preventDefault event)
  (swap! app-state update-in [:keyboard :pressed] disj (.fromCharCode js/String (.-keyCode event)))
  (on-event!))

(defn on-resize! [event]
  "Handle resize events."
  (.preventDefault event)
  (draw/size-canvas-to-window!)
  (on-event!))

(defn annotate-plan [waypoints plan]
  (let [loc->wp (into {} (map (fn [wp] [(:location wp) wp]) waypoints))]
    (map (partial get loc->wp) (map util/->world plan))))

(defn on-task-completion [state task]
  "Handle task completion events."
  (let [type (:type task)
        result (:result task)
        primary (get-in state [:mode :primary])
        secondary (get-in state [:mode :secondary])]
    (.log js/console type " task complete")
    (cond
     (= "plan" type) (-> state
                         (assoc-in [:route :plan]
                                   (annotate-plan (get-in state [:route :waypoints]) result))
                         (assoc-in [:route :animation] default-animation-state)
                         (assoc-in [:route :execution :current] 0))
     (and (= "move" type)
          (= execute-mode primary)
          (= run-mode secondary)) (update-in state [:route] resume-execution!)
     :else state)))

(defn update-task-state [app-state task]
  (let [state (:state task)
        id (:id task)]
    (if (= "complete" state)
      (-> app-state
          (on-task-completion task)
          (update-in [:tasks :pending] (fn [tasks] (filterv (fn [t] (not (= id (:id t)))) tasks)))
          (update-in [:tasks :complete] (fn [tasks] (conj tasks task))))
      app-state)))

(defn start-task! [task]
  "Send a task to the server and add its ID to the pending task list."
  (api/add-task!
   task
   (fn [resp]
     (.log js/console "Started task " (str resp) "id:" (:id resp))
     (if (= "complete" (:state resp))
       (swap! app-state update-task-state resp)
       (swap! app-state update-in [:tasks :pending] #(conj % resp))))
   (fn [resp] (.log js/console "Failed to add task " (str task)))))

(defn clean-waypoint [waypoint]
  (let [{:keys [location kind part-id]} waypoint
        {:keys [x y]} location]
   {:x x :y y :z 0 :kind kind :part-id part-id}))

(defn plan-route! [waypoints]
  "Send a request to the server to plan a route using the current waypoints."
  (start-task! {:type :plan :waypoints (mapv clean-waypoint waypoints)}))

(defn resume-execution! [route]
  (let [exec-state (:execution route)
        plan (:plan route)
        current (if (= (:plan route) (:plan exec-state))
                  (:current exec-state) 0)
        next-step (if (< current (count plan)) (nth plan current) nil)]
    (.log js/console "Plan:" (str plan))
    (.log js/console "Next step:" (str next-step))
    (if next-step
      (do
        (start-task! {:type :move :waypoint (clean-waypoint next-step)})
        (-> route
            (assoc-in [:execution :current] (+ 1 current))
            (assoc-in [:execution :plan] plan)))
      (do
        (.log js/console "Path execution complete.")
        route))))

(defn pause-execution! [route]
  ;; TODO Pause the task on the server that's currently executing
  route
  )

(defn stop-execution! [route]
  ;; TODO Stop the task on the server that's currently executing
  route
  )

(defn check-heartbeat! [elapsed]
  (api/get-meta (fn [_] (swap! app-state assoc-in [:connection :connected] true))
                (fn [_] (swap! app-state assoc-in [:connection :connected] false)))
  (when (< heartbeat-timeout elapsed) (swap! app-state assoc-in [:connection :connected] false)))

(defn check-tasks! [_]
  (doseq [id (map :id (get-in @app-state [:tasks :pending]))]
    (api/get-task
     id
     (fn [resp]
       (swap! app-state update-task-state resp))
     (fn [err]
       (.log js/console "Error when checking on task state: " (str err))
       (swap! app-state update-in [:tasks :pending]
              #(filterv (fn [t] (not= id (:id t))) %))))))

(defn check-position! [_]
  (api/get-status
   (fn [resp]
     (let [position (util/->world (vals (:position resp)))]
       (swap! app-state assoc-in [:robot :position] position)))
   (fn [err] (.log js/console "Error when fetching position info: " (str err)))))

(defn do-periodically [period last-path f]
  (let [now (util/current-time)
        last (get-in @app-state last-path)
        elapsed (- now last)]
    (when (< period elapsed)
      (f elapsed)
      (swap! app-state update-in last-path (constantly now)))))

(defn update-periodic-tasks! []
  (do-periodically heartbeat-interval [:connection :last-heartbeat] check-heartbeat!)
  (do-periodically task-update-interval [:tasks :last-poll] check-tasks!)
  (do-periodically position-update-interval [:robot :last-poll] check-position!))

(defn attach-handlers []
  (set! (.-onmousemove (util/get-canvas)) on-mouse-move!)
  (set! (.-onmouseup (util/get-canvas)) on-mouse-up!)
  (set! (.-onmousedown (util/get-canvas)) on-mouse-down!)
  (set! (.-onkeydown (util/get-body)) on-key-down!)
  (set! (.-onkeyup (util/get-body)) on-key-up!)
  (set! (.-onresize js/window) on-resize!))
