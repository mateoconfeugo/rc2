(ns rc2.lib.driver.pololu-test
  (:require [speclj.core :refer :all]
            [rc2.lib.robot :as robot]
            [rc2.lib.descriptor.delta :as delta]
            [rc2.lib.driver.pololu :refer :all]
            [serial-port :as serial]))

(def port 'port)
(def calibration {:low_usec 0 :high_usec 100 :low_angle -50 :high_angle 50 :inverted false})
(def interface (->PololuInterface port {:a calibration :b calibration :c calibration}))
(def pose (delta/->DeltaPose {:a 10 :b 5 :c 1}))
(defn byte-arrays-equal [a1 a2]
  (= (seq a1) (seq a2)))

(describe
 "initialize!"
 (it "should return true if the right byte is written"
     (let [init-byte (byte-array 1 (byte -42))]
       (with-redefs [serial/write (fn [port array] (byte-arrays-equal init-byte array))]
        (should (robot/initialize! interface))))))

(describe
 "take-pose!"
 (pending "should use the calibration value for each servo"
          (let [interface (assoc interface :calibrations
                                 {:a (assoc calibration :low_angle 0 :high_angle 20)
                                  :b (assoc calibration :low_angle 0 :high_angle 10)
                                  :c (assoc calibration :low_angle 0 :high_angle 2)})]
            (with-redefs [move-servo! (fn [iface servo cycle] (and (= interface iface)
                                                                   (= 50 cycle)))]
             (should (robot/take-pose! interface pose))))))

(describe
 "->duty-cycle"
 (it "should return nil if the angle is higher than the :high_angle"
     (should= nil (->duty-cycle calibration 60)))
 (it "should return nil if the angle is lower than the :low_angle"
     (should= nil (->duty-cycle calibration -60)))
 (it "should return the midpoint duty cycle for 0 radians"
     (should= 50 (->duty-cycle calibration 0)))
 (it "should return the low end duty cycle for :low_angle radians"
     (should= 0 (->duty-cycle calibration -50)))
 (it "should return the high end duty cycle for :high_angle radians"
     (should= 100 (->duty-cycle calibration 50)))
 (it "should return the high duty cycle for :low_angle radians when inverted"
     (should= 100 (->duty-cycle (assoc calibration :inverted true) -50)))
 (it "should return the low duty cycle for :high_angle radians when inverted"
     (should= 0 (->duty-cycle (assoc calibration :inverted true) 50)))
 (it "should return a value below mid for args above mid when inverted"
     (should= 25 (->duty-cycle (assoc calibration :inverted true) 25)))
 (it "should return a value above mid for args below mid when inverted"
     (should= 75 (->duty-cycle (assoc calibration :inverted true) -25)))
 (it "should return an int"
     (should= 75 (->duty-cycle calibration 25.5))))

(describe
 "servo->index"
 (it "should return nil if the servo keyword is wrong"
     (should= nil (servo->index :blah)))
 (it "should return 1 for :b"
     (should= 1 (servo->index :b))))
