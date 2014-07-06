(ns rc2.api
  (:require [ajax.core :refer [GET POST]]))

(defn add-task! [task success error]
  (POST "/api/v1/tasks"
       {:params task
        :handler success
        :error-handler error
        :format :json}))

(defn get-tasks [success error]
  (GET "/api/v1/tasks" {:handler success
                        :error-handler error
                        :format :json}))

(defn get-task [id success error]
  (GET (str "/api/v1/tasks/" id) {:handler success
                                  :error-handler error
                                  :format :json}))

(defn get-meta [success error]
  (GET "/meta" {:handler success
                :error-handler error
                :format :json}))
