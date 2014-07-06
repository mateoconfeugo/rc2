(ns rc2.lib.driver.serial-util
  (require [gloss.io :as gio]
           [serial-port :as serial]))

;; TODO Unit test...?
(defn encode-array [frame data]
  "Encode 'data into a byte array using the format specified by 'frame."
  (.array (gio/contiguous (gio/encode frame data))))

;; TODO Unit test
(defn write-vals [interface vals]
  "Write a sequence of byte values to the given interface."
  (serial/write (:serial interface) (byte-array (count vals) (map byte vals)))
  true)

;; TODO Unit test
(defn write-array [interface array]
  "Write the contents of a byte array to the given interface."
  (serial/write (:serial interface) array))

(defn close-interface [iface]
  "Close the serial interface."
  (serial/close iface))
