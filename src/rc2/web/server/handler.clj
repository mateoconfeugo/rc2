(ns rc2.web.server.handler
  (require [rc2.web.server.task :as task]
           [rc2.lib.planner :as planner]))

;; TODO Flesh out handlers

(defn- handle-move-task [task]
  true)

(defn- waypoint->source [{:keys [part-id x y z] :or {:z 0}}]
  "Convert a waypoint into a source definition."
  [part-id [x y z]])

(defn- waypoint->sink [{:keys [part-id x y z] :or {:z 0}}]
  "Convert a waypoint into a source definition."
  [part-id [x y z]])

(defn handle-plan-task [task]
  (let [waypoints (:waypoints task)
        sources (filter (fn [wp] (= "source" (:kind wp))) waypoints)
        sources (into {} (map waypoint->source sources))
        sinks (filter (fn [wp] (= "sink" (:kind wp))) waypoints)
        sinks (mapv waypoint->sink sinks)]
    (planner/plan-pick-and-place sources sinks)))

(defn attach-handlers! []
  "Attach handlers for the tasks API."
  (task/register-task-type! :move handle-move-task)
  (task/register-task-type! :plan handle-plan-task :affinity :parallel))
