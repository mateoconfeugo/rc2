(ns rc2.core
  (use clojure.tools.trace)
  (require [serial-port :as serial]
           [rc2.lib
            [position :as pos]
            [robot :as rbt]]
           [rc2.lib.descriptor.delta :as delta]
           [rc2.lib.driver.pololu :as pol]
           [clojure.tools.cli :as cli])
  (:gen-class))

(defn parse-args [args]
  (cli/cli args
           ["-p" "--port" "Serial port to use"]
           ["-f" "--targets-file" "List of positions to move to"]))

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

(def last-move (atom nil))
(defn move-to [descriptor interface pos]
  (println "Moving to " pos)
  (let [intermediates (if (nil? @last-move) (list pos) (pos/interpolate @last-move pos 0.05))
        find-pose (partial rbt/find-pose descriptor)
        take-pose! (partial rbt/take-pose! interface)]
    (doseq [p intermediates]
      (if-let [pose (find-pose p)]
       (take-pose! pose)
       (println "ERROR: Position " p " was not reachable."))
      (Thread/sleep 1))
    (swap! last-move (constantly pos))))

(defn do-moves! [descriptor interface positions]
  (let [move-to (partial move-to descriptor interface)]
    (doseq [pos positions]
      (move-to pos))))

(defn -main [& args]
  (println "Starting up.")
  (let [{:keys [port targets-file]} (first (parse-args args))
        {:keys [serial-port descriptor interface]} (connect port)
        positions (trace 'positions (load-file (trace 'targets targets-file)))]
    (println "Port: " port)
    (rbt/initialize! interface)
    (println "Initialized. Descriptor: " descriptor)
    (println "Positions: " positions)
    (do-moves! descriptor interface positions)
    (println "Movements Complete")
    (serial/close serial-port)))


