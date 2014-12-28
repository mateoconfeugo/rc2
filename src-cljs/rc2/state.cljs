(ns rc2.state
  (:require [cljs.core.async :as async
             :refer [<! chan put!]]
            [reagent.core :as reagent :refer [atom]]
            [rc2.api :as api]
            [rc2.draw :as draw]
            [rc2.util :as util]
            [clojure.set :as set])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(declare plan-route!)
(declare move-to!)
(declare resume-execution!)
(declare pause-execution!)
(declare stop-execution!)

(def heartbeat-interval (* 1000 3))
(def heartbeat-timeout (* heartbeat-interval 3))
(def task-update-interval 500) ;; Check for task state changes every 500ms.
(def position-update-interval 500) ;; Check for position state changes every 500ms.

(def task-chan (chan))

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
                               \S #(enter-mode :sink %)
                               \F #(enter-mode :follow %)}
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
(def follow-mode (->InputMode :follow :primary
                              {\P #(enter-mode :source %)
                               \S #(enter-mode :sink %)
                               \backspace #(enter-mode :insert %)
                                \newline #(enter-mode :insert %)
                                \return #(enter-mode :insert %)
                                \formfeed #(enter-mode :insert %)
                                \space #(enter-mode :insert %)}
                               "FOLLOW"
                               [] []))

(defn enter-mode [mode state]
  (enter (condp = mode
           :default default-mode
           :insert insert-mode
           :delete delete-mode
           :edit edit-mode
           :execute execute-mode
           :follow follow-mode
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
         :current-page :home
         :mouse {:location util/origin
                 :buttons {0 :up 1 :up 2 :up}
                 :previous-buttons {0 :up 1 :up 2 :up}}
         :keyboard {:pressed #{}
                    :previous-pressed #{}}
         :route {:waypoints []
                 :plan []
                 :animation default-animation-state
                 :execution {:current 0
                             :plan []
                             :task-id nil}}
         :parts {0 {:name "DEFAULT" :highlight true}}
         :ui {
              ;; Buttons have a name (rendered on the screen), a target vector, and a
              ;; transform function. When the button is clicked the transform function
              ;; is called with the value in this state tree at the 'target' path,
              ;; which is then replaced with the returned value.
              :buttons {:plan {:text "Plan"
                               :inputs [[:route :waypoints]]
                               :target [:route :waypoints]
                               :hover false
                               :click false
                               :xform (fn [waypoints]
                                        (plan-route! waypoints)
                                        waypoints)
                               :visible-when (constantly true)}
                        :start {:text "Start"
                                :inputs [[]]
                                :target []
                                :hover false
                                :click false
                                :xform
                                (fn [state]
                                  (-> state
                                      (#(enter execute-mode %))
                                      (update-in [:route] resume-execution!)))
                                :visible-when (fn [state]
                                                (not
                                                 (= run-mode (get-in state [:mode :secondary]))))}
                        :pause {:text "Pause"
                                :inputs [[]]
                                :target []
                                :hover false
                                :click false
                                :xform
                                (fn [state]
                                  (-> state
                                      (#(enter pause-mode %))
                                      (update-in [:route] pause-execution!)))
                                :visible-when (fn [state]
                                                (and
                                                 (= execute-mode (get-in state [:mode :primary]))
                                                 (= run-mode (get-in state [:mode :secondary]))))}
                        :stop {:text "Stop"
                               :inputs [[]]
                               :target []
                               :hover false
                               :click false
                               :xform
                               (fn [state]
                                 (let [mode (:primary (:mode state))]
                                   (if (= execute-mode mode)
                                     (-> state
                                         (update-in [:route] stop-execution!)
                                         (#(enter insert-mode %)))
                                     state)))
                               :visible-when (constantly true)}
                        :clear {:text "Clear"
                                :inputs [[:route]]
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

(defn global-put! [k v]
  (swap! app-state assoc k v))

(defn global-state [k]
  (get @app-state k))

;;;;;;;;;;
;; State

;; Add new handlers for state paths in {pre,pos}-draw-transforms. State transform functions are
;; registered along with an input path and an output path. The function is applied to the current
;; (last-frame) state of the input and output paths and the returned value is stored in the output
;; path.

(defn apply-state-transforms [state transforms]
  "Apply a series of transforms of the form [in-path out-path transform] to a state map and return
  the updated map."
  (try
   (reduce (fn [prev-state [in-paths out-path xform]]
             (let [xform-state (apply xform (map #(get-in prev-state %) in-paths))]
               (if (empty? out-path)
                 xform-state
                 (assoc-in prev-state out-path xform-state))))
           state
           transforms)
   (catch js/Object e
     (.log js/console "Caught error" e "while updating state:" (str state))
     (throw e))))

;; TODO Refactor transforms to explicitly specify if they need the output destination as an input

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

(defn handle-waypoint-updates [mouse buttons primary secondary parts route]
  (let [buttons (vals buttons)]
    (if (and (not (some :hover buttons))
             (clicked? mouse 0))
      (cond
       (and (or (= follow-mode primary)
                (= insert-mode primary))
            (get-selected-part-id parts))
       (update-in route [:waypoints]
                  conj {:location (:location mouse)
                        :highlight true
                        :kind (.-keyword secondary)
                        :part-id (get-selected-part-id parts)})
       (= delete-mode primary)
       (-> route
           (update-in [:waypoints] (fn [wps] (filter #(not (:highlight %)) wps)))
           (assoc :plan []))
       :else route)
      route)))

(defn highlight-waypoints [canvas mouse waypoints]
  (let [mouse (util/canvas->world canvas mouse)]
    (map (fn [wp]
           (assoc wp :highlight (< (util/distance canvas (:location wp) mouse)
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

(defn handle-button-actions [buttons state]
  "Perform the on-click actions of the clicked UI buttons."
  (let [buttons (vals buttons)
        transforms (filterv
                    (comp not nil?)
                    (mapv (fn [btn] [(:inputs btn) (:target btn) (:xform btn)])
                          (filterv #(:click %) buttons)))]
    (apply-state-transforms state transforms)))

(defn handle-part-keys [keys parts mode state]
  "Set the selected part based on the current keys."
  (let [selected-part (get-selected-part-id (:parts state))]
    (if-let [part-num (first (->> keys
                                  (map js/parseInt)
                                  (filter (fn [k] (not (js/isNaN k))))))]
      (if (or (= edit-mode mode) (contains? parts part-num))
        (-> parts
            ((fn [ps] (if selected-part
                       (assoc-in ps [selected-part :highlight] false)
                       ps)))
            (assoc-in [part-num :highlight] true))
        parts)
      parts)))

(defn handle-mode-keys [pressed-keys primary secondary state]
  "Update state based on the current mode's keybindings."
  (-> state
      (#(reduce (fn [s k] (handle-keypress primary k s)) % pressed-keys))
      (#(reduce (fn [s k] (handle-keypress secondary k s)) % pressed-keys))))

(defn handle-edit-mode-keys [keys old-keys mode parts]
  "Handle keypresses in edit mode."
  (if (= edit-mode mode)
    (let [new-keys (set/difference keys old-keys)
          new-keys (filter (fn [k] (js/isNaN (js/parseInt k))) new-keys)
          new-keys (filter (fn [k] (not (contains? (keys (.-keys edit-mode)) k))) new-keys)
          part-id (get-selected-part-id parts)]
      (if part-id
        (-> parts
            (update-in [part-id :name]
                       (fn [name]
                         (reduce (fn [n c] (if (= "\b" c) (apply str (butlast n)) (str n c)))
                                 name new-keys))))
        parts))
    parts))

(defn handle-delete-mode-keys [keys old-keys mode parts]
  "Handle keypresses in delete mode."
  ;; TODO Maybe remove all waypoints for deleted parts?
  (if (= delete-mode mode)
    (let [new-keys (set/difference keys old-keys)
          part-id (get-selected-part-id parts)]
      (if (contains? new-keys \backspace)
        (-> parts
            ;; Remove the current part
            (dissoc part-id)
            ;; Re-highlight the first part
            ((fn [parts] (if-let [key (first (keys parts))]
                           (assoc-in parts [key :highlight] true)
                           parts))))
        parts))
    parts))

(defn update-plan-annotations [waypoints plan]
  (let [loc->wp (into {} (map (fn [wp] [(:location wp) wp]) waypoints))]
    (map (partial get loc->wp) (map #(:location %) plan))))

(defn update-plan-animation [canvas plan anim-state]
  (if (not (empty? plan))
    (let [last-point (:location (nth plan (:index anim-state)))
          next-point (:location (nth plan (+ 1 (:index anim-state))))
          current-offsets (:offsets anim-state)
          current-location (util/coord+ canvas current-offsets last-point)
          next-offsets (util/coord+
                        canvas
                        current-offsets
                        (util/scale-to
                         canvas
                         (util/coord- canvas next-point last-point)
                         animation-step-distance))
          next-location (util/coord+ canvas next-offsets last-point)
          past-next (< (util/distance canvas last-point next-point)
                       (util/distance canvas last-point next-location))]
      (if (not past-next)
        (assoc anim-state :offsets next-offsets)
        (assoc anim-state
          :offsets util/origin
          :index (mod (+ 1 (:index anim-state)) (- (count plan) 1)))))
    default-animation-state))

(defn move-robot-to-follow! [mouse-loc primary secondary parts]
  "Send a move command to the robot to go to the current mouse location."
  ;; TODO Add logic to control Z axis, maybe usings croll wheel.
  (when (= follow-mode primary)
    (let [waypoint {:location mouse-loc
                    :kind (.-keyword secondary)
                    :part-id (get-selected-part-id parts)}]
      (.log js/console "Moving robot to waypoint:" (str waypoint))
      (move-to! waypoint)
      ))
  mouse-loc)

(def pre-draw-transforms
  [
  [[] [:time] util/current-time]
   [[[:keyboard :pressed]
     [:keyboard :previous-pressed]
     [:mode :primary]
     [:parts]] [:parts] handle-edit-mode-keys]
   [[[:keyboard :pressed]
     [:keyboard :previous-pressed]
     [:mode :primary]
     [:parts]] [:parts] handle-delete-mode-keys]
   [[[:keyboard :pressed]
     [:mode :primary]
     [:mode :secondary] []] [] handle-mode-keys]
   [[[:keyboard :pressed] [:parts] [:mode :primary]] [:parts] handle-part-keys]
   [[[] [:ui :buttons]] [:ui :buttons] update-button-visibilities]
   [[[:ui :buttons] []] [] handle-button-actions]
   [[[:mouse]
     [:ui :buttons]
     [:mode :primary]
     [:mode :secondary]
     [:parts]
     [:route]] [:route] handle-waypoint-updates]
   [[[:mouse :location]
     [:mode :primary]
     [:mode :secondary]
     [:parts]] [:mouse :location] move-robot-to-follow!]
   [[[:canvas] [:mouse :location] [:route :waypoints]] [:route :waypoints] highlight-waypoints]
   [[[:route :waypoints] [:route :plan]] [:route :plan] update-plan-annotations]
   [[[:canvas] [:route :plan] [:route :animation]] [:route :animation] update-plan-animation]
   [[[:mouse :buttons] [:mouse :previous-buttons]] [:mouse :previous-buttons] copy]
   [[[:keyboard :pressed] [:keyboard :previous-pressed]] [:keyboard :previous-pressed] copy]
   ])

(defn on-state-change! []
  "Perform pre-draw transformations to application state."
  (swap! app-state apply-state-transforms pre-draw-transforms))

(defn on-event! []
  (on-state-change!))

(defn on-mouse-move! [event]
  "Handle mouse movement events."
  (.preventDefault event)
  (let [canvas (:canvas @app-state)]
    (swap! app-state update-in [:mouse :location]
           (fn [m] (util/canvas->world canvas (util/->canvas (.-clientX event) (.-clientY event))))))
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

(defn on-resize! [canvas event]
  "Handle resize events."
  (.preventDefault event)
  (draw/fix-canvas-size! canvas)
  (on-event!))

(defn on-button-click! [button-id event]
  (let [click-state (= :down event)]
    (swap! app-state assoc-in [:ui :buttons button-id :click] click-state))
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
    (.log js/console type "task" (:id task) "complete")
    (cond
     (= "plan" type) (-> state
                         (assoc-in [:route :plan]
                                   (annotate-plan (get-in state [:route :waypoints]) result))
                         (assoc-in [:route :animation] default-animation-state)
                         (assoc-in [:route :execution :current] 0))
     (= "move" type) (-> state
                         (assoc-in [:route :execution :task-id] nil)
                         (#(when (and (= execute-mode primary) (= run-mode secondary))
                             ;; TODO Switch out of execute mode when tasks are complete
                             (update-in % [:route] resume-execution!))))
     :else state)))

(defn move-to-complete [state task]
  "Move a task's ID from the pending list to the completed list in the given state."
  (-> state
      (update-in [:tasks :pending]
                 (fn [tasks] (filterv (fn [t] (not (= (:id task) (:id t)))) tasks)))
      (update-in [:tasks :complete] (fn [tasks] (conj tasks task)))))

(defn update-task-state [state task]
  "Update a task's status in the current state."
  (let [task-state (:state task)
        id (:id task)]
    (cond
     (= "new" task-state) (update-in state [:tasks :pending] conj task)
     (= "complete" task-state) (-> state
                                   (on-task-completion task)
                                   (move-to-complete task))
     (= "cancelled" task-state) (move-to-complete state task)
     :else state)))

(defn start-async-task-processor []
  (go
    (loop []
      (when-let [task (<! task-chan)]
        (swap! app-state update-task-state task)
        (recur)))))

(defn start-task! [task & {:keys [error success] :or {error nil success nil}}]
  "Send a task to the server and add its ID to the pending task list."
  (api/add-task!
   task
   (fn [resp]
     (.log js/console "Started task" (str resp))
     (when success (success resp))
     (put! task-chan resp))
   (fn [resp]
     (.log js/console "Failed to add task " (str task))
     (when error (error resp)))))

(defn stop-task! [task-id & {:keys [error success] :or {error nil success nil}}]
  "Stop execution of an existing task and add its ID to the completed task list."
  (api/cancel-task!
   task-id
   (fn [resp]
     (.log js/console "Canceled task" (str resp))
     (when success (success resp))
     (swap! app-state update-task-state resp))
   (fn [resp]
     (.log js/console "Failed to stop task" (str resp))
     (when error (error resp)))))

(defn clean-waypoint [waypoint]
  (let [{:keys [location kind part-id]} waypoint
        {:keys [x y]} location]
   {:x x :y y :z 0 :kind kind :part-id part-id}))

(defn plan-route! [waypoints]
  "Send a request to the server to plan a route using the current waypoints."
  (start-task! {:type :plan :waypoints (mapv clean-waypoint waypoints)}))

(defn move-to!
  ([waypoint] (start-task! {:type :move :waypoint (clean-waypoint waypoint)}))
  ([waypoint on-success on-error]
     (start-task! {:type :move :waypoint (clean-waypoint waypoint)}
                  :success on-success
                  :error on-error)))

(defn resume-execution! [route]
  ;; TODO Retry the previous task if it was cancelled by pause
  (let [exec-state (:execution route)
        plan (:plan route)
        ;; Start from the top if the plan has changed.
        current (if (= (:plan route) (:plan exec-state))
                  (:current exec-state) 0)
        next-step (if (< current (count plan)) (nth plan current) nil)]
    (if next-step
      (do
        (move-to! next-step
                  #(swap! app-state assoc-in [:route :execution :task-id] (:id %))
                  (fn [_] (swap! app-state #(enter pause-mode %))))
        (-> route
            (assoc-in [:execution :current] (+ 1 current))
            (assoc-in [:execution :plan] plan)))
      (do
        (.log js/console "Path execution complete.")
        (assoc-in route [:execution :task-id] nil)))))

(defn pause-execution! [route]
  "Pause the current path execution task on the server."
  (let [task-id (get-in route [:execution :task-id])]
    (when task-id (stop-task! task-id))
    (start-task! {:type :pause})
    (assoc-in route [:execution :task-id] nil)))

(defn stop-execution! [route]
  (-> route
      pause-execution!
      (assoc-in [:execution :current] 0)))

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
  (api/get-robot-position
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

(defn attach-canvas-handlers [canvas]
  (swap! app-state assoc :canvas canvas)
  (start-async-task-processor)
  (set! (.-onmousemove canvas) on-mouse-move!)
  (set! (.-onmouseup canvas) on-mouse-up!)
  (set! (.-onmousedown canvas) on-mouse-down!)
  (set! (.-onresize js/window) (partial on-resize! canvas)))

(defn attach-body-handlers [body]
  (set! (.-onkeydown body) on-key-down!)
  (set! (.-onkeyup body) on-key-up!))
