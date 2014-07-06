(ns rc2.web.server.handler
  (require [rc2.web.server.task :as task]))

;; TODO Flesh out handlers

(defn- handle-move-task [task]
  true)

(defn- handle-plan-task [task]
  true)

(defn attach-handlers! []
  "Attach handlers for the tasks API."
  (task/register-task-type! :move handle-move-task)
  (task/register-task-type! :plan handle-plan-task :affinity :parallel))
