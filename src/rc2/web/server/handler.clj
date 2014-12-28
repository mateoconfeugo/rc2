(ns rc2.web.server.handler
  (require [rc2.lib.planner :as planner]
           [rc2.lib.robot :as rbt]
           [rc2.web.settings :as settings]
           [rc2.web.server.task :as task]))

(defn- handle-move-task [task]
  (let [{:keys [x y z]} (:waypoint task)
        position [x y z]
        descriptor (settings/get-setting :descriptor)
        driver (settings/get-setting [:connection :interface])]
    (println "Executing move task" task)
    (println "Descriptor:" descriptor)
    (println "Driver:" driver)
    (println "Position:" position)
    (let [result (when (rbt/reachable? descriptor position [])
                   (println "Pose seems reachable - attempting move.")
                   (->> (rbt/find-pose descriptor position [])
                       ((fn [p] (println "Pose: " p) p))
                       (rbt/take-pose! driver)))]
      (println "Move complete")
      result)))

(defn- handle-pause-task [task]
  ;; TODO Add handler to pause task execution.
  true)

(defn- waypoint->source [{:keys [part-id x y z] :or {:z 0}}]
  "Convert a waypoint into a source definition."
  [part-id [x y z]])

(defn- waypoint->sink [{:keys [part-id x y z] :or {:z 0}}]
  "Convert a waypoint into a source definition."
  [part-id [x y z]])

(defn- handle-plan-task [task]
  (let [waypoints (:waypoints task)
        sources (filter (fn [wp] (= "source" (:kind wp))) waypoints)
        sources (into {} (map waypoint->source sources))
        sinks (filter (fn [wp] (= "sink" (:kind wp))) waypoints)
        sinks (mapv waypoint->sink sinks)]
    (planner/plan-pick-and-place sources sinks)))

(defn attach-handlers! []
  "Attach handlers for the tasks API."
  (task/register-task-type! :move handle-move-task)
  (task/register-task-type! :pause handle-pause-task)
  (task/register-task-type! :plan handle-plan-task :affinity :parallel))
