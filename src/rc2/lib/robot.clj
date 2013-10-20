(ns rc2.lib.robot
  (:require [clojure.core.typed :as type :refer [defprotocol>]])
  (:import [clojure.lang IPersistentMap IPersistentVector Keyword]))

;; This protocol defines pose creation functions. Passing a desired position and
;; descriptor allows the functions to create pose descriptions that can be
;; passed to robot drivers.
(type/ann-protocol RobotDescriptor
                   find-pose [RobotDescriptor (IPersistentVector Number) -> RobotPose])
(defprotocol> RobotDescriptor
  "State transition functions for a robot"
  (find-pose [descriptor position]
    "Perform inverse kinematics to find a pose that puts the robot in 'position with parameters from
    'descriptor."))

;; This protocol defines pose manipulation/access functions.
(type/ann-protocol RobotPose
                   joint-angles [RobotPose -> (IPersistentMap Keyword Number)])
(defprotocol> RobotPose
  "Interface functions for robot poses."
  (joint-angles [pose] "Get the joint angles for the pose as a map from servo to angle."))

;; This protocol is intended to be used by functions which implement interfaces
;; to the actual device. These could be hardware or software interfaces; when
;; you give the function a pose the robot should change state to move to it.
(type/ann-protocol RobotDriver
                   initialize! [RobotDriver -> nil]
                   shut-down! [RobotDriver -> nil]
                   take-pose! [RobotDriver RobotPose -> nil]
                   set-tool-state! [RobotDriver Keyword Keyword -> nil]
                   set-parameters! [RobotDriver (IPersistentMap Keyword Any) -> nil])
(defprotocol> RobotDriver
  "Interface functions to move robots."
  (initialize! [interface] "Perform setup operations on the interface. Return true if initialization
  succeeds.")
  (shut-down! [interface] "Perform shut down and clean up of the interface.")
  (take-pose! [interface pose] "Move the robot into the pose. Return true if the pose was reached
  successfully, false otherwise.")
  (set-tool-state! [interface tool state] "Change the tool state.")
  (set-parameters! [interface parameters] "Set motor parameters on the interface."))
