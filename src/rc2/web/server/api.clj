(ns rc2.web.server.api
  (:require
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

(def start-time 0)
(def api-server (atom nil))

(defroutes api-routes
  (ANY "/api/v1/tasks" []
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:post :get]
                 :handle-ok (fn [ctx] (let [tasks (task/get-tasks)]
                                        (println "Tasks:" tasks)
                                        tasks))
                 :handle-created ::task
                 :post! (fn [ctx]
                          (println "Request:" (get-in ctx [:request :body]))
                          (let [options (get-in ctx [:request :body])
                                type (keyword (:type options))
                                task (task/add-task! type options)]
                            (println "Added task:" task)
                            {::id (:id task)}))
                 :post-redirect? (fn [ctx] {:location (format "/api/v1/tasks/%s" (::id ctx))})))
  (ANY "/api/v1/tasks/:id" [id]
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:delete :get]
                 :exists? (fn [_] (let [task (task/get-task (Integer. id))]
                                    (if task {::task task})))
                 :handle-ok ::task
                 ;; TODO Change this to a PATCH or PUT instead of DELETE.
                 :delete! (fn [_] (task/cancel-task! (Integer. id)))))
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
  (ANY "/api/v1/status" []
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
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "Not Found"))

(def api (-> api-routes
             (wrap-params)
             (wrap-json-body {:keywords? true})
             (wrap-json-response)
             (trace/wrap-stacktrace)))

(defn start-api-server [port]
  "Start the API server on the given port."
  (def start-time (System/currentTimeMillis))
  (println "Starting API server on port" port)
  (reset! api-server
          (server/run-server (reload/wrap-reload (site #'api)) {:port port :join? false}))
  (println "API server up/running"))

(defn stop-api-server []
  "Stop the API server"
  (@api-server :timeout 100))
