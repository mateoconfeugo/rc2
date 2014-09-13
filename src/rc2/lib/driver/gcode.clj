(ns rc2.lib.driver.gcode
  (:use [rc2.lib.driver.serial-util :only [write-array write-vals encode-array close-interface]])
  (:require [rc2.lib.robot :as robot]
            [gloss.core :as gloss]))

(defrecord GcodeInterface [serial calibrations])

(defn controlled-move [position]
  (str "G1 X" (:x position) " Y" (:y position) " Z" (:z position)))

(defn set-velocities! [interface velocities]
  (write-line interface (str "M203 X" (:x accelerations)
                             " Y" (:y accelerations)
                             " Z" (:z accelerations))))

(defn set-accelerations! [interface accelerations]
  (write-line interface (str "M201 X" (:x accelerations)
                             " Y" (:y accelerations)
                             " Z" (:z accelerations))))

(extend-protocol robot/RobotDriver
  GcodeInterface
  (initialize! [interface]
    ;; Set units to mm
    (write-line interface "G21")
    ;; Positioning to absolute
    (write-line interface "G90")
    ;; Home device
    (write-line interface "G28"))
  (shut-down! [interface]
    ;; Sleep
    (write-line interface "M1"))
  (take-pose! [interface pose]
    (let [position (:position pose)]
     (write-line interface (controlled-move position))))
  (set-tool-state! [interface tool state])
  (set-parameters! [interface parameters]
    (when-let [velocities (:velocity parameters)]
      (println "Setting velocities to" velocities)
      (set-velocities! interface velocities))
    (when-let [accelerations (:acceleration parameters)]
      (println "Setting accelerations to" accelerations)
      (set-accelerations! interface accelerations)))
  (calibrate! [interface calibrations]
    (assoc interface :calibrations calibrations)))
