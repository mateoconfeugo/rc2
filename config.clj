;; Config file for RC2.
{
 :driver :fake ;; :gcode
 :output "output.txt"
 :descriptor :delta
 :max-velocity 400 ;; mm/s
 :max-accel 1000   ;; mm/s^2
 :http-port 8000
 :serial-port "none"
 :calibration :default
 }
