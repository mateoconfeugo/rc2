(ns rc2.web.server.api
  (:require
   [clojure.core.async :refer [chan close!]]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [compojure.core :refer [defroutes ANY GET POST]]
   [compojure.handler :refer [site]]
   [compojure.route :as route]
   [liberator.core :refer [resource defresource]]
   [org.httpkit.server :as server]
   [rc2.web.server.task :as task]
   [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.reload :as reload]
   [ring.middleware.stacktrace :as trace]
   [ring.util.response :as resp]))

(declare stop-api-server!)

(def start-time 0)
(def api-server (atom nil))

(defroutes api-routes
  (ANY "/api/v1/tasks" []
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:post :get]
                 :handle-ok (fn [ctx] (let [tasks (task/get-tasks)]
                                        (println "Tasks:" tasks)
                                        tasks))
                 :post! (fn [ctx]
                          (println "Request:" (get-in ctx [:request :body]))
                          (let [options (get-in ctx [:request :body])
                                type (keyword (:type options))
                                task (task/add-task! type options)]
                            (println "Added task:" task)
                            {::id (:id task)}))
                 :post-redirect? (fn [ctx] {:location (format "/api/v1/tasks/%s" (::id ctx))})))
  (ANY "/api/v1/tasks/pause" []
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:post :get]
                 :handle-ok (fn [ctx] {:status @task/execution-state})
                 :post! (fn [ctx] (task/pause-task-execution!))
                 :post-redirect? (fn [ctx] {:location "/api/v1/tasks/pause"})))
  (ANY "/api/v1/tasks/resume" []
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:post :get]
                 :handle-ok (fn [ctx] {:status @task/execution-state})
                 :post! (fn [ctx] (task/resume-task-execution!))
                 :post-redirect? (fn [ctx] {:location "/api/v1/tasks/resume"})))
  (ANY "/api/v1/tasks/:id" [id]
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:get]
                 :exists? (fn [_] (let [task (task/get-task (Integer. id))]
                                    (if task {::task task})))
                 :handle-ok ::task))
  (ANY "/api/v1/tasks/:id/cancel" [id]
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:post]
                 :exists? (fn [_] (let [task (task/get-task (Integer. id))]
                                    (if task {::task task})))
                 :handle-ok ::task
                 :post! (fn [ctx]
                          (task/cancel-task! (Integer. id))
                          {::id id})
                 :post-redirect? (fn [ctx] {:location (format "/api/v1/tasks/%s" (::id ctx))})))
  (ANY "/api/v1/events" []
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:get]
                 :handle-ok (fn [ctx] (let [events (task/get-events)]
                                        (println "Events:" events)
                                        events))))
  (ANY "/api/v1/events/:id" [id]
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:get]
                 :exists? (fn [_] (let [event (task/get-event (Integer. id))]
                                    (if event {::event event})))
                 :handle-ok ::event))
  (ANY "/api/v1/robot/position" []
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:get]
                 ;; TODO Wire this up to actual position data
                 :handle-ok (fn [ctx] {:position {:x 0 :y 0 :z 0}})))
  (ANY "/meta" []
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:get]
                 :handle-ok (fn [_] {:status :online
                                     :uptime (int (/ (- (System/currentTimeMillis)
                                                        start-time) 1000))})))
  (ANY "/shutdown" []
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:get :post]
                 :handle-ok (fn [_] (stop-api-server!) {:good :bye})
                 :post! (fn [ctx] (stop-api-server!))
                 :post-redirect? (fn [ctx] {:location (format "/meta" (::id ctx))})))
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "Not Found"))

(def api (-> api-routes
             (wrap-params)
             (wrap-json-body {:keywords? true})
             (wrap-json-response)
             (trace/wrap-stacktrace)))

(defn start-api-server! [port]
  "Start the API server on the given port."
  (def start-time (System/currentTimeMillis))
  (println "Starting API server on port" port)
  (let [shutdown-chan (chan)]
    (reset! api-server
            {:shutdown-fn
             (server/run-server (reload/wrap-reload (site #'api)) {:port port :join? false})
             :shutdown-chan shutdown-chan})
    (println "API server up/running")
    shutdown-chan))

(defn stop-api-server! []
  "Stop the API server"
  (let [{:keys [shutdown-fn shutdown-chan] :as api-serv} @api-server]
    (shutdown-fn :timeout 100)
    (close! shutdown-chan)))
