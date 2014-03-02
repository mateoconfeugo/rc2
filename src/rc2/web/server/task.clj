(ns rc2.web.server.task
  (:require [schema.core :as s])
  (:use [clojure.core.async]))

;;;; Task management facilities for RC2 API server.
(defonce model (ref {:events {}
                     :last-event-id 0
                     :tasks {}
                     :last-task-id 0
                     :device-status {}}))
(defonce handlers (atom {}))

(def initialized (atom false))
(defonce queues {:dispatch (chan 50)
                 :serial (chan 50)
                 :parallel (chan 50)})

(def Task {(s/required-key :id) s/Number
           (s/required-key :type) s/Keyword
           (s/required-key :created) s/Number
           (s/required-key :update) s/Number
           (s/required-key :state) s/Keyword
           :affinity s/Keyword
           :destination {(s/required-key :x) s/Number
                         (s/required-key :y) s/Number
                         (s/required-key :z) s/Number}})

(declare update-task!)

(defn set-handler [type handler]
  "Set the handler for tasks of the given 'type."
  (swap! handlers assoc type handler))

;; This function is intended to be used only within this module. It is public only for unit testing.
(defn do-task! [task handlers]
  "Performs a task and returns the state updates which should be applied to it."
  (if-let [handler (get handlers (:type task))]
    (if (handler task)
      {:state :complete}
      ;; TODO Decide on how handlers should indicate error details to the dispatcher
      {:state :failed})
    {:state :failed :errors [(str "No handler for task type " (:type task))]}))

(defn- dispatch-queues! [{:keys [dispatch serial parallel]}]
  "Allocate tasks from the 'dispatch queue into the 'serial and 'parallel queues."
  (go (loop [task (<! dispatch)]
        (when task
          (>! (case (:affinity task)
                :serial serial
                :parallel parallel)
              task)
          (recur (<! dispatch))))))

(defn- process-queue! [queue]
  "Process tasks from the queue until it is closed."
  (go (loop [task (<! queue)]
        (when task
          (update-task! (:id task) :state :processing)
          (update-task! (:id task) (do-task! task @handlers))
          (recur (<! queue))))))

(defn init-workers! [num-parallel]
  "Create worker jobs to consume tasks. Creates one serial worker and 'num-parallel parallel ones.
   This function does not check to see if workers have already been created."
  (if @initialized
    false
    (do (dispatch-queues! queues)
        (process-queue! (:serial queues))
        (dotimes [i num-parallel]
          (process-queue! (:parallel queues)))
        (reset! initialized true))))

(defn shutdown-queues! [& {:keys [dispatch serial parallel]}]
  "Shut down the queues so that workers will terminate when the last task is consumed."
  (map close! [dispatch serial parallel]))

(defn dispatch-task! [task]
  "Add a task to the dispatch queue for execution."
  (>!! (:dispatch queues) task)
  task)

(defn add-event! [event]
  "Add a new event to the log."
  (dosync
   (let [event-id (inc (:last-event-id @model))]
     (alter model assoc-in [:events event-id] event)
     (alter model assoc :last-event-id event-id)
     event-id)))

(defn- current-time []
  "Get the current time in milliseconds."
  (System/currentTimeMillis))

(defn- make-event [task-id errors & deltas]
  {:pre [(even? (count deltas))]}
  "Create a new event."
  {:created (current-time)
   :errors errors
   :task task-id
   :changed (apply hash-map deltas)})

(defn update-task! [task-id & deltas]
  "Update a task by field. Example: (update-task 1 :status :started)"
  (dosync
   (let [event (apply (partial make-event task-id nil) deltas)
         event-id (add-event! event)
         original-task (assoc (get-in @model [:tasks task-id]) :update event-id)
         updated-task (merge original-task (apply hash-map deltas))]
     (alter model assoc-in [:tasks task-id] updated-task)
     original-task)))

(defn make-task [type options]
  "Create a new task."
  (let [template {:created (current-time) :type type}
        task (merge options template)]
    ;; TODO This should probably dispatch out to type-specific functions so we can validate
    (case type
      :move (assoc task :affinity :serial)
      :connect (assoc task :affinity :serial)
      :calibrate (assoc task :affinity :serial))))

(defn add-task! [type options]
  "Add a task to the task listing."
  (dosync
   (let [task-id (inc (:last-task-id @model))
         task (assoc (make-task type options) :id task-id)]
     (alter model assoc-in [:tasks task-id] task)
     (alter model assoc :last-task-id task-id)
     (dispatch-task! (update-task! task-id :state :new)))))

(defn cancel-task! [id]
  "Cancel a task by ID."
  (update-task! id :state :canceled))

(defn get-tasks []
  "Get the task registry."
  (:tasks @model))

(defn get-task [id]
  "Get a specific task from the task registry by id."
  (get (get-tasks) id))

(defn get-events []
  "Get the event registry."
  (:events @model))

(defn get-event [id]
  "Get a specific event from the event registry by id."
  (get (get-events) id))
