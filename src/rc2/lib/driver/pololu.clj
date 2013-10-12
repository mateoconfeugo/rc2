(ns rc2.lib.driver.pololu
  (:use [rc2.lib.driver.serial-util :only [write-array write-vals encode-array]]
        clojure.tools.trace)
  (:require [rc2.lib.robot :as robot]
            [gloss.core :as gloss]
            [serial-port :as serial]))

(defrecord PololuInterface [serial calibrations])

;; Calibration values are given for the low and high microsecond values that the servo can reach,
;; and the angles produced when these duty cycles are applied to servo. Positive angles are when the
;; servo arm rotates counter clockwise (when looking at the front/top of the servo) unless :inverted
;; is true.
;; TODO I have a hunch that these are wrong; they need to be verified again. It would be nice to
;; have a tool that lets you create these values interactively.
(def default-calibration {:low_usec 985
                          :high_usec 1995
                          :low_angle -0.96
                          :high_angle 0.61
                          :inverted false})
(def four-byte-frame (gloss/compile-frame [:ubyte :ubyte :ubyte :ubyte]))
(def servo-move-frame four-byte-frame)
(def servo-velocity-frame four-byte-frame)
(def servo-acceleration-frame four-byte-frame)
(def maestro-command-bytes {:move 0x84
                            :velocity 0x87
                            :acceleration 0x89})

(defn ->duty-cycle [calibration angle]
  "Convert 'angle to a servo duty cycle in microseconds, using values in 'calibration."
  (let [{:keys [low_usec high_usec low_angle high_angle inverted]} calibration]
    (if (<= low_angle angle high_angle)
      (let [angle-delta (- angle low_angle)
            usec-per-rad (/ (- high_usec low_usec) (- high_angle low_angle))
            usec-delta (* angle-delta usec-per-rad)]
        (int (if inverted
           (- high_usec usec-delta)
           (+ low_usec usec-delta))))
      (println "Angle " angle " is out of range [" low_angle "," high_angle "]"))))

(defn servo->index [servo]
  "Convert a servo keyword into a numeric index. If the keyword is not valid, returns nil."
  (let [index (.indexOf [:a :b :c] servo)]
    (when-not (neg? index) index)))

(defn ->4-byte-command [servo value command mask shift]
  "Create a byte array command"
  (let [low_bits (bit-and value mask)
        high_bits (bit-and (byte mask) (bit-shift-right value shift))
        index (servo->index servo)]
    (encode-array four-byte-frame [(get maestro-command-bytes command) index low_bits high_bits])))

;; TODO Unit test
(defn ->move-command [servo duty-cycle]
  "Build a byte array representing the Pololu target command for the given servo."
  (->4-byte-command servo (* 4 duty-cycle) :move 0x7F 7))

(defn ->velocity-command [servo velocity]
  "Create a byte array command to set 'servo to 'velocity."
  (->4-byte-command servo velocity :velocity 0xFF 8))

(defn ->acceleration-command [servo acceleration]
  "Create a byte array command to set 'servo to 'acceleration."
  (->4-byte-command servo acceleration :acceleration 0xFF 8))

(defn move-servo! [interface servo duty-cycle]
  "Move a single servo to the given duty cycle."
  (when-not (nil? duty-cycle) ;; TODO Do something better here. Add tests for nil values.
    (write-array interface (->move-command servo duty-cycle))))

(defn set-properties! [interface properties frame-fn]
  "Set the properties on the interface to 'properties. 'properties is a mapping from servo index to
  desired property value."
  (let [servos (keys properties)]
    (doseq [servo servos]
      (write-array interface (frame-fn servo (get properties servo))))))

;; The Maestro sets velocities in terms of change in PWM cycle per unit time, whereas we want change
;; in angle per unit time. This means we need to look up the servo and find the conversion rate
;; between the two units, and then pass the correct one to the next step.
(defn set-velocities! [interface velocities]
  ;; TODO Specify and convert between angular and Maestro units.
  "Set the velocities on the interface to 'velocities. 'velocities is a mapping from servo index to
  desired velocity."
  (set-properties! interface velocities '->velocity-command))

(defn set-accelerations! [interface accelerations]
  "Set the maximum accelerations on the interface to 'accelerations."
  (set-properties! interface accelerations '->acceleration-command))

(extend-protocol robot/RobotDriver
  PololuInterface
  (initialize! [interface] (write-vals interface [-42]))
  (shut-down! [interface] (serial/close (:serial interface)))
  (take-pose! [interface pose]
    (let [angles (robot/joint-angles pose)
          servos (keys angles)]
      (doseq [servo servos]
        (move-servo! interface servo
                     (->duty-cycle (get (:calibrations interface) servo)
                                   (get angles servo))))))
  (set-parameters! [interface parameters]
    (set-velocities! interface (:velocity parameters))
    (set-accelerations! interface (:acceleration parameters))))
