(ns rc2.lib.driver.pololu
  (:use clojure.tools.trace)
  (:require [rc2.lib.robot :as robot]))

(defrecord PololuInterface [serial calibrations])

;; Calibration values are given for the low and high microsecond values that the servo can reach,
;; and the angles produced when these duty cycles are applied to servo. Positive angles are when the
;; servo arm rotates counter clockwise (when looking at the front/top of the servo) unless :inverted
;; is true.
(def default-calibration {:low_usec 985
                          :high_usec 1995
                          :low_angle -0.96
                          :high_angle 0.61
                          :inverted false})

(defn ->duty-cycle [calibration angle]
  "Convert 'angle to a servo duty cycle in microseconds, using values in 'calibration."
  (let [{:keys [low_usec high_usec low_angle high_angle inverted]} calibration]
    (when (<= low_angle angle high_angle)
      (let [angle-delta (- angle low_angle)
            usec-per-rad (/ (- high_usec low_usec) (- high_angle low_angle))
            usec-delta (* angle-delta usec-per-rad)]
        (if inverted
          (- high_usec usec-delta)
          (+ low_usec usec-delta))))))

(defn move-servo! [servo duty-cycle]
  true) ;; TODO

(extend-protocol robot/RobotDriver
  PololuInterface
  (take-pose! [interface pose]
    (let [angles (robot/joint-angles pose)
          servos (keys angles)]
      (every? true?
              (map #(move-servo! % (->duty-cycle (% (:calibrations interface)) (% angles)))
                   servos)))))
