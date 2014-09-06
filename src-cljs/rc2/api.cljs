(ns rc2.api
  (:require [ajax.core :refer [GET POST]]))

(defn keywordize [m]
  "Recursively convert map keys in m from strings to keywords."
  (cond
   (map? m) (into {}
                  (map (fn [[k v]] (let [k (if (string? k) (keyword k) k)
                                         v (keywordize v)]
                                     [k v])) m))
   (vector? m) (mapv keywordize m)
   (seq? m) (map keywordize m)
   :else m)
  )

(defn add-task! [task success error]
  (POST "/api/v1/tasks"
       {:params task
        :handler (comp success keywordize)
        :error-handler (comp error keywordize)
        :format :json}))

(defn get-tasks [success error]
  (GET "/api/v1/tasks" {:handler (comp success keywordize)
                        :error-handler (comp error keywordize)
                        :format :json}))

(defn get-task [id success error]
  (GET (str "/api/v1/tasks/" id) {:handler (comp success keywordize)
                                  :error-handler (comp error keywordize)
                                  :format :json}))

(defn get-status [success error]
  (GET "/api/v1/status" {:handler (comp success keywordize)
                :error-handler (comp error keywordize)
                :format :json}))

(defn get-meta [success error]
  (GET "/meta" {:handler (comp success keywordize)
                :error-handler (comp error keywordize)
                :format :json}))
