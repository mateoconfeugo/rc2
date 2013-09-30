(ns rc2.lib.driver.pololu
  (:require [rc2.lib.robot :as robot]))

(defrecord PololuInterface [serial])

(defn ->duty-cycle [angle]
  "Convert an angle to a servo duty cycle in microseconds."
  0) ;; TODO

(defn move-servo! [servo duty-cycle]
  true) ;; TODO

(extend-protocol robot/RobotDriver
  PololuInterface
  (take-pose! [interface pose]
    (every? true?
            (map #(move-servo! % (->duty-cycle (% pose)))
                 (keys (robot/joint-angles pose))))))
