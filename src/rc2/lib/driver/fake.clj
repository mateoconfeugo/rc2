(ns rc2.lib.driver.fake
  (:require [rc2.lib.robot :as robot]))

;;;; A fake RC2 driver which outputs the requested actions to a file.

(defrecord FakeInterface [filename])

(defn- write-to-file [interface s]
  (spit (.filename interface) (str (.toString (java.util.Date.)) " - " s "\n") :append true))

(extend-protocol robot/RobotDriver
  FakeInterface
  (initialize! [interface]
    (write-to-file interface "Initialization requested"))
  (shut-down! [interface]
    (write-to-file interface "Shutdown requested"))
  (take-pose! [interface pose]
    (write-to-file interface (str "Move to pose " pose)))
  (set-tool-state! [interface tool state]
    (write-to-file interface (str "Set tool state " tool "->" state)))
  (set-parameters! [interface parameters]
    (write-to-file interface (str "Set parameters " parameters)))
  (calibrate! [interface calibrations]
    (write-to-file interface (str "Calibrate with " calibrations))))
