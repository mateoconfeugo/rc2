(ns rc2.lib.driver.pololu-test
  (:use midje.sweet
        rc2.lib.driver.pololu)
  (:require [rc2.lib.robot :as robot]
            [rc2.lib.descriptor.delta :as delta]))

(def serial 'serial)
(def interface (->PololuInterface serial))
(def pose (delta/->DeltaPose {:a 10 :b 5 :c 1}))

(facts "About take-pose!"
  (fact "take-pose! should return true if all servo operations return true."
    (robot/take-pose! interface pose) => true
    (provided (move-servo! anything anything) => true :times 3)))
