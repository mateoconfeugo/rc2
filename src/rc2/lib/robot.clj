(ns rc2.lib.robot)

;; This protocol defines pose creation functions. Passing a desired position and
;; descriptor allows the functions to create pose descriptions that can be
;; passed to robot drivers.
(defprotocol RobotBehavior
  "State transition functions for a robot"
  (find-pose [descriptor position]
    "Perform forward kinematics to find a pose that puts the robot in the given
    position with the given intrinsic parameters."))

;; This protocol is intended to be used by functions which implement interfaces
;; to the actual device. These could be hardware or software interfaces; when
;; you give the function a pose the robot should change state to move to it.
(defprotocol RobotDriver
  "Interface functions to move robots."
  (take-pose! [interface pose]
    "Move the robot into the given pose."))
