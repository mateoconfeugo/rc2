;; Config file for RC2.
{
 :driver :gcode
 :output "output.txt"
 :descriptor :delta
 :descriptor-settings [10.2 15 3.7 4.7]
 :max-velocity 40 ;; mm/s
 :max-accel 100   ;; mm/s^2
 :http-port 8000
 :serial-port "/dev/cu.usbmodem00140271"
 :calibration-file "calibration.clj"
 }
