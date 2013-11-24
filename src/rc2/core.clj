(ns rc2.core
  (use clojure.tools.trace)
  (require [serial-port :as serial]
           [rc2.lib
            [math :as math]
            [position :as pos]
            [planner :as plan]
            [robot :as rbt]]
           [rc2.lib.descriptor.delta :as delta]
           [rc2.lib.driver.pololu :as pol]
           [clojure.tools.cli :as cli])
  (:gen-class))

(def interpolate true)
(def interpolate-delay 1)
(def no-interpolate-delay 1000)

(defn parse-args [args]
  "Parse command line arguments into a map of their values."
  (cli/cli args
           ["-p" "--port" "Serial port to use"]
           ["-f" "--targets-file" "List of positions to move to"]
           ["-s" "--source-file" "List of part sources"]
           ["-d" "--destination-file" "List of part destinations"]))

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
(defn do-move! [descriptor interface pos]
  (println "Moving to " pos)
  (let [intermediates (if (or (nil? @last-move) (not interpolate))
                        (list pos)
                        (pos/interpolate @last-move pos 0.05))
        find-pose (partial rbt/find-pose descriptor)
        take-pose! (partial rbt/take-pose! interface)]
    (doseq [p intermediates]
      (if-let [pose (find-pose p)]
        (take-pose! pose)
        (println "ERROR: Position " p " was not reachable."))
      (Thread/sleep (if interpolate interpolate-delay no-interpolate-delay)))
    (swap! last-move (constantly pos))))

(defn change-tool-state! [interface tool state]
  (rbt/set-tool-state! interface tool state))

(defn execute! [descriptor interface tasks]
  "Execute 'tasks using 'interface."
  (let [do-move! (partial do-move! descriptor interface)
        change-tool-state! (partial change-tool-state! interface)]
    (doseq [task tasks]
      (let [type (first task)
            params (rest task)]
        (cond
         (= :move type) (apply do-move! params)
         (= :tool type) (apply change-tool-state! params)
         :else (println "Unknown task" task))))))

(defn find-path [source-file destination-file]
  "Find a path which populates all of the part locations in 'destination-file with parts from the
   locations in 'source-file."
  (let [sources (load-file source-file)
        destinations (load-file destination-file)]
    (plan/plan-pick-and-place sources destinations)))

(def max-velocity (/ math/pi 40))
(def max-accel (/ math/pi 40))
(defn -main [& args]
  (println "Starting up.")
  (let [{:keys [port targets-file source-file destination-file]} (first (parse-args args))
        {:keys [serial-port descriptor interface]} (connect port)
        positions (if targets-file (do (println "Using targets from " targets-file)
                                       (load-file targets-file))
                      (do (println "Planning path using " source-file destination-file)
                          (find-path source-file destination-file)))]
    (println "Port: " port)
    (rbt/initialize! interface)
    ;; TODO Extract this into a macro that executes a user script.
    (rbt/set-parameters! interface
                         {:velocity {:a max-velocity :b max-velocity :c max-velocity}
                          :acceleration {:a max-accel :b max-accel :c max-accel}})
    (println "Initialized.")
    (execute! descriptor interface positions)
    (println "Movements Complete")
    (serial/close serial-port)))
