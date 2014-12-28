(ns rc2.lib.robot
  (:require [rc2.lib.position :as pos]
            [schema.core :as s])
  (:import [clojure.lang IPersistentMap IPersistentVector Keyword]))

;; A waypoint describing cartesian position and orientation.
(s/defrecord Waypoint [x :- s/Num
                       y :- s/Num
                       z :- s/Num
                       a :- s/Num
                       b :- s/Num
                       c :- s/Num])

;; A constraint limits the possible poses that a robot can take.
(s/defrecord PoseConstraint [kind :- s/Keyword])

;; This protocol defines pose creation functions. Passing a desired position and
;; descriptor allows the functions to create pose descriptions that can be
;; passed to robot drivers.
(defprotocol RobotDescriptor
  "State transition functions for a robot"
  (find-pose [descriptor position constraints]
    "Perform inverse kinematics to find a pose that puts the robot in 'position with parameters from
    'descriptor.")
  (reachable? [descriptor position constraints]
    "Returns true if the position is reachable, false otherwise."))

;; This protocol defines pose manipulation/access functions.
(defprotocol RobotPose
  "Interface functions for robot poses."
  (joint-angles [pose] "Get the joint angles for the pose as a map from servo to angle.")
  (waypoint [pose] "Get the waypoint reached by this pose."))

;; This protocol is intended to be used by functions which implement interfaces
;; to the actual device. These could be hardware or software interfaces; when
;; you give the function a pose the robot should change state to move to it.
(defprotocol RobotDriver
  "Interface functions to move robots."
  (initialize! [interface] "Perform setup operations on the interface. Return true if initialization
  succeeds.")
  (shut-down! [interface] "Perform shut down and clean up of the interface.")

  (set-parameters! [interface parameters] "Set motor parameters on the interface.")
  (calibrate! [interface calibrations] "Set calibration settings for the interface.")

  (take-pose! [interface pose] "Move the robot into the pose. Return true if the pose was reached
  successfully, false otherwise.")

  (set-output-state! [interface output state] "Change the output state.")
  (read-input! [interface input] "Read an input.")

  (emergency-stop! [interface] "Immediately halt all movement.")
  ;; Non-emergency halt & restart?
  )
