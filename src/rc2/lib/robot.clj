(ns rc2.lib.robot)

(defprotocol RobotBehavior
  "State transition functions for a robot"
  (find-pose [descriptor position] "Perform forward kinematics to find a pose that puts the robot in
  the given position with the given intrinsic parameters."))
