(ns rc2.web.core
  (:require [clojure.core.async :refer [<!!]]
            [serial-port :as serial]
            [schema.core :as s]
            [rc2.lib.math :as math]
             [rc2.lib.robot :as rbt :refer [initialize!]]
            [rc2.lib.descriptor.delta :as delta]
            [rc2.lib.driver.pololu :as pol]
            [rc2.lib.driver.gcode :as gcode]
            [rc2.lib.driver.fake :as fake]
            [rc2.web.settings :as settings]
            [rc2.web.server.api :as api]
            [rc2.web.server.handler :as handler]
            [rc2.web.server.task :as task]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.cli :as cli])
  (:gen-class))

(defn get-config [args]
  "Parse command line arguments into a map of their values."
  (let [{:keys [options summary errors]}
        (cli/parse-opts args
                        [["-s" "--serial PORT" "Serial port to use"]
                         ["-p" "--port PORT" "Network port to listen on"]
                         ["-c" "--config-file PATH" "Config file location"
                          :default "~/.rc2/config.clj"]])
        {:keys [config-file serial port]} options
        config (settings/load-config! config-file)
        serial (or serial (:serial-port config))
        port (or port (:http-port config) 8000)]
    (settings/change-setting! :config-file config-file)
    (settings/change-setting! :serial-port serial)
    (settings/change-setting! :http-port port)))

(defn get-descriptor [config]
  (condp = (:descriptor config)
    :delta (apply delta/->DeltaDescriptor (:descriptor-settings config))
    (throw (IllegalArgumentException. (str "Unrecognized descriptor type " (:descriptor config))))))

(defn get-calibration [config]
  (let [calibration (read-string (slurp (:calibration-file config)))
        driver (:driver config)]
    (if (= :default calibration)
      (condp = driver
        :pololu {:a pol/default-calibration
                 :b pol/default-calibration
                 :c pol/default-calibration}
        :gcode {}
        :fake {}
        (throw (IllegalArgumentException. (str "No default calibration for driver " driver))))
      (if (nil? calibration)
        (throw (IllegalArgumentException. "No calibration data provided!"))
        (do (println "Loaded calibration data")
          calibration)))))

(defn get-driver [config serial-port calibration]
  (let [driver (:driver config)]
    (condp = driver
      :pololu (pol/->PololuInterface serial-port calibration)
      :gcode (gcode/->GcodeInterface serial-port calibration)
      :fake (fake/->FakeInterface (:output config))
      (throw (IllegalArgumentException. (str "Unrecognized driver type " driver))))))

(defn connect-serial [config]
  "Connect the serial port."
  (let [port (:serial-port config)]
    (println "Connecting to serial: " port)
    (if (= "none" port)
      (println "Skipping serial connection")
      (serial/open port))))

(defn connect [config]
  (let [calibration (get-calibration config)
        serial-port (connect-serial config)
        driver (get-driver config serial-port calibration)
        max-velocity (:max-velocity config)
        max-accel (:max-accel config)]
    (rbt/initialize! driver)
    (rbt/set-parameters! driver
                         {:velocity {:a max-velocity :b max-velocity :c max-velocity}
                          :acceleration {:a max-accel :b max-accel :c max-accel}})
    {:interface driver
     :serial serial-port
     :calibration calibration}))

(defn -main [& args]
  (println "Starting up.")
  (let [config (get-config args)
        descriptor (get-descriptor config)
        connection (connect config)]
    (settings/change-setting! :connection connection)
    (settings/change-setting! [:connection :descriptor] descriptor)
    (println "Initializing task system")
    (task/init-workers! 5)
    (handler/attach-handlers!)
    (let [shutdown-chan (api/start-api-server! (Integer. (:http-port config)))]
      (<!! shutdown-chan) ;; Block until the API server shuts down
      (when-let [serial-port (:serial connection)] (serial/close serial-port))
      (settings/save-config! (:config-file config) (:calibration-file config)))))

(comment
(def cfg (load-file "/Users/mburns/projects/github/rc2/config.clj"))
(def driver (connect cfg))
(rbt/initialize! driver)
(pprint driver)

)
