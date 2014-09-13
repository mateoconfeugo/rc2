(ns rc2.web.core
  (:require [serial-port :as serial]
           [schema.core :as s]
           [rc2.lib
            [math :as math]
            [robot :as rbt]]
           [rc2.lib.descriptor.delta :as delta]
           [rc2.lib.driver.pololu :as pol]
           [rc2.lib.driver.gcode :as gcode]
           [rc2.web.server.api :as api]
           [rc2.web.server.task :as task]
           [rc2.web.server.handler :as handler]
           [clojure.tools.cli :as cli])
  (:gen-class))

(def ConfigFile
  "Schema for the RC2 config file"
  {
   :driver s/Keyword
   :descriptor s/Keyword
   :max-velocity s/Num
   :max-accel s/Num
   :http-port s/Int
   :serial-port s/Str
   :calibration s/Keyword
   })

(defn parse-args [args]
  "Parse command line arguments into a map of their values."
  (let [{:keys [options summary errors]}
        (cli/parse-opts args
                        [["-s" "--serial PORT" "Serial port to use"]
                         ["-p" "--port PORT" "Network port to listen on"]
                         ["-c" "--config-file PATH" "Config file location"
                          :default "~/.rc2/config.clj"]])
        {:keys [config-file serial port]} options
        config (read-string (slurp config-file))
        serial (if serial serial (:serial-port config))
        port (if port port (:http-port config))]
    (s/validate ConfigFile (assoc config :serial-port serial :http-port port))))

(defn get-descriptor [config]
  (condp = (:descriptor config)
    :delta (delta/->DeltaDescriptor 10.2 15 3.7 4.7)
    (throw (IllegalArgumentException. (str "Unrecognized descriptor type " (:descriptor config))))))

(defn get-calibration [config]
  (let [calibration (:calibration config)
        driver (:driver config)]
    (if (= :default calibration)
      (condp = driver
        :pololu {:a pol/default-calibration
                 :b pol/default-calibration
                 :c pol/default-calibration}
        :gcode {}
        (throw (IllegalArgumentException. (str "No default calibration for driver " driver))))
      (if (nil? calibration)
        (throw (IllegalArgumentException. "No calibration data provided!"))
        calibration))))

(defn get-driver [config serial-port calibration]
  (let [driver (:driver config)]
    (condp = driver
      :pololu (pol/->PololuInterface serial-port calibration)
      :gcode (gcode/->GcodeInterface serial-port calibration)
      (throw (IllegalArgumentException. (str "Unrecognized driver type " driver))))))

(defn connect-serial [config]
  "Connect the serial port."
  (println "Connecting to serial: " (:serial-port config))
  (serial/open (:serial-port config)))

(defn connect [config]
  (let [calibration (get-calibration config)
        serial-port (connect-serial config)
        driver (get-driver config serial-port calibration)]
    {:interface driver
     :serial serial-port}))

(defn -main [& args]
  (println "Starting up.")
  (let [config (parse-args args)
        descriptor (get-descriptor config)
        connection (if (= (:serial-port config) "none")
                     (do (println "Skipping serial connection.") nil)
                     (connect config))
        max-velocity (:max-velocity config)
        max-accel (:max-accel config)]
    (when-let [interface (:interface connection)]
      (rbt/initialize! interface)
      (rbt/set-parameters! interface
                           {:velocity {:a max-velocity :b max-velocity :c max-velocity}
                            :acceleration {:a max-accel :b max-accel :c max-accel}}))
    (println "Initializing task system")
    (task/init-workers! 5)
    (handler/attach-handlers!)
    (api/start-api-server (Integer. (:http-port config)))
    (when-let [serial-port (:serial connection)] (serial/close serial-port))))
