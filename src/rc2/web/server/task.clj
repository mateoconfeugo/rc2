(ns rc2.web.server.task
  (:require [schema.core :as s])
  (:use [clojure.core.async]))

;;;; Task management facilities for RC2 API server.

(defonce model (ref {:events {}
                     :last-event-id 0
                     :tasks {}
                     :last-task-id 0
                     :device-status {}}))

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

;; TODO Handle task based on type.
(defn- do-task! [task]
  "Perform a task."
  ;; (println "Executing task: " task)
  true
  )

(defn- dispatch-queues! [{:keys [dispatch serial parallel]}]
  "Allocate tasks from the 'dispatch queue into the 'serial and 'parallel queues."
  (go (loop [task (<! dispatch)]
        (when task
          (cond
           (= :serial (:affinity task)) (do (>! serial task)
                                            (println "Dispatched" task "to serial"))
           (= :parallel (:affinity task)) (do (>! parallel task)
                                              (println "Dispatched" task "to parallel")))
          (recur (<! dispatch))))))

(defn- process-queue! [queue]
  "Process tasks from the queue until it is closed."
  (go (loop [task (<! queue)]
        (when task (do-task! task) (recur (<! queue))))))

(defn init-workers! [num-parallel]
  "Create worker jobs to consume tasks. Creates one serial worker and 'num-parallel parallel ones.
   This function does not check to see if workers have already been created."
  (dispatch-queues! queues)
  (process-queue! (:serial queues))
  (dotimes [i num-parallel]
    (process-queue! (:parallel queues)))
  true)

(defn shutdown-queues! [& {:keys [dispatch serial parallel]}]
  "Shut down the queues so that workers will terminate when the last task is consumed."
  (map close! [dispatch serial parallel]))

(defn current-time []
  "Get the current time in milliseconds."
  (System/currentTimeMillis))

(defn dispatch-task! [task]
  (>!! (:dispatch queues) task))

(defn add-event! [event]
  "Add a new event to the log."
  (dosync
   (let [event-id (inc (:last-event-id @model))]
     (alter model assoc-in [:events event-id] event)
     (alter model assoc :last-event-id event-id)
     event-id)))

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
         task (assoc (get-in @model [:tasks task-id]) :update event-id)]
     (alter model assoc-in [:tasks task-id] (merge task (apply hash-map deltas))))))

(defn make-task [type {:keys [destination]}]
  "Create a new task."
  (let [template {:created (current-time) :type type :affinity :parallel}]
    (cond
     (= :move type) (assoc template :destination destination :affinity :serial))))

(defn add-task! [type & {:keys [destination] :as kw-args}]
  "Add a task to the task listing."
  (dosync
   (let [task-id (inc (:last-task-id @model))]
     (alter model assoc-in [:tasks task-id] (make-task type kw-args))
     (alter model assoc :last-task-id task-id)
     (update-task! task-id :state :new))))

(defn get-tasks []
  "Get the task registry."
  (:tasks @model))

(defn get-events []
  "Get the event registry."
  (:events @model))
