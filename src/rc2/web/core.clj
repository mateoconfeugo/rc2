(ns rc2.web.core
  (require [serial-port :as serial]
           [rc2.lib
            [math :as math]
            [robot :as rbt]]
           [rc2.lib.descriptor.delta :as delta]
           [rc2.lib.driver.pololu :as pol]
           [rc2.web.server.api :as api]
           [rc2.web.server.task :as task]
           [clojure.tools.cli :as cli])
  (:gen-class))

(def interpolate true)
(def interpolate-delay 1)
(def no-interpolate-delay 1000)

(defn parse-args [args]
  "Parse command line arguments into a map of their values."
  (cli/cli args
           ["-s" "--serial" "Serial port to use"]
           ["-p" "--port" "Networks port to listen on"]))

(defn connect [port & {:keys [descriptor calibrations]
                       :or {descriptor (delta/->DeltaDescriptor 10.2 15 3.7 4.7)
                            calibrations {:a pol/default-calibration
                                          :b pol/default-calibration
                                          :c pol/default-calibration}}}]
  "Connect to a robot and return a map of connection parameters."
  (let [serial-port (serial/open port)]
    (assoc {}
      :descriptor descriptor
      :serial-port serial-port
      :interface (pol/->PololuInterface serial-port calibrations))))

;; TODO Expose these values in the API and persist them when set
(def max-velocity (/ math/pi 40))
(def max-accel (/ math/pi 40))

(defn -main [& args]
  (println "Starting up.")
  (let [{:keys [serial port]} (first (parse-args args))
        connection (if (= serial "none") nil (connect serial))]
    (if connection
      (println "Connecting to serial: " serial)
      (println "Skipping serial port."))
    (when-let [interface (:interface connection)]
      (rbt/initialize! interface)
      (rbt/set-parameters! interface
                           {:velocity {:a max-velocity :b max-velocity :c max-velocity}
                            :acceleration {:a max-accel :b max-accel :c max-accel}}))
    (println "Initializing task system")
    (task/init-workers! 5)
    (api/start-api-server (Integer. port))
    (when-let [serial-port (:serial-port connection)] (serial/close serial-port))))
