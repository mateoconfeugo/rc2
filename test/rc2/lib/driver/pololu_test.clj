(ns rc2.lib.driver.pololu-test
  (:use midje.sweet
        rc2.lib.driver.pololu)
  (:require [rc2.lib.robot :as robot]
            [rc2.lib.descriptor.delta :as delta]))

(def serial 'serial)
(def calibration {:low_usec 0 :high_usec 100 :low_angle -50 :high_angle 50 :inverted false})
(def interface (->PololuInterface serial {:a calibration :b calibration :c calibration}))
(def pose (delta/->DeltaPose {:a 10 :b 5 :c 1}))

(facts "About take-pose!"
  (fact "take-pose! should return true if all servo operations return true"
    (robot/take-pose! interface pose) => true
    (provided (move-servo! anything anything) => true :times 3))
  (fact "take-pose! should use the calibration value for each servo"
    (robot/take-pose! (assoc interface :calibrations
                             {:a (assoc calibration :low_angle 0 :high_angle 20)
                              :b (assoc calibration :low_angle 0 :high_angle 10)
                              :c (assoc calibration :low_angle 0 :high_angle 2)}) pose) => true
    (provided (move-servo! anything 50) => true)))

(facts "About ->duty-cycle"
  (fact "->duty-cycle should return nil if the angle is higher than the :high_angle"
    (->duty-cycle calibration 60) => nil)
  (fact "->duty-cycle should return nil if the angle is lower than the :low_angle"
    (->duty-cycle calibration -60) => nil)
  (fact "->duty-cycle should return the midpoint duty cycle for 0 radians"
    (->duty-cycle calibration 0) => 50)
  (fact "->duty-cycle should return the low end duty cycle for :low_angle radians"
    (->duty-cycle calibration -50) => 0)
  (fact "->duty-cycle should return the high end duty cycle for :high_angle radians"
    (->duty-cycle calibration 50) => 100)
  (fact "->duty-cycle should return the high duty cycle for :low_angle radians when inverted"
    (->duty-cycle (assoc calibration :inverted true) -50) => 100)
  (fact "->duty-cycle should return the low duty cycle for :high_angle radians when inverted"
    (->duty-cycle (assoc calibration :inverted true) 50) => 0)
  (fact "->duty-cycle should return a value below mid for args above mid when inverted"
    (->duty-cycle (assoc calibration :inverted true) 25) => 25)
  (fact "->duty-cycle should return a value above mid for args below mid when inverted"
    (->duty-cycle (assoc calibration :inverted true) -25) => 75))
