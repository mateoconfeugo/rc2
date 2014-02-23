(ns rc2.web.server.api
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [compojure.core :refer [defroutes ANY GET POST]]
   [compojure.handler :refer [site]]
   [compojure.route :as route]
   [org.httpkit.server :as server]
   [rc2.web.server.task :as task]
   [ring.middleware.reload :as reload]
   [ring.middleware.stacktrace :as trace]
   [liberator.core :refer [resource defresource]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.json :refer [wrap-json-response wrap-json-body]]))

(def start-time 0)
(def api-server (atom nil))

(defroutes api-routes
  (GET "/" [] "RC2 API Server V1.0")
  (ANY "/api/v1/tasks" []
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:post :get]
                 :handle-ok (fn [ctx] (task/get-tasks))
                 :handle-created ::task
                 :post! (fn [ctx] (let [options (::data ctx)
                                       type (:type options)]
                                   {::task (task/add-task! type options)}))))
  (ANY "/api/v1/tasks/:id" [id]
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:delete :get]
                 :exists? (fn [_] (let [task (task/get-task id)]
                                    (if-not (nil? task) {::task task})))
                 :handle-ok ::task
                 :delete! (fn [_] (task/cancel-task! id))))
  (ANY "/meta" []
       (resource :available-media-types ["application/json"]
                 :allowed-methods [:get]
                 :handle-ok (fn [_] {:status :online
                                     :uptime (int (/ (- (System/currentTimeMillis)
                                                        start-time) 1000))})))
  (route/not-found "Not Found"))

(def api (-> api-routes
                 (wrap-params)
                 (wrap-json-body)
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
