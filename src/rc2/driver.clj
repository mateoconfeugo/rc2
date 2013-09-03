(ns rc2.driver
  (:gen-class)
  (:use clojure.string)
  (:require [rc2.lib.robot :as robot]))

(declare help-impl)

(def session-state (atom {}))

(defn quit-impl []
  (println "Goodbye!")
  (System/exit 0))

(defn connect-impl! [host port]
  (swap! session-state assoc :connection (robot/connect! host port)))

(defn state-impl [] (println @session-state))

(def cmd->impl 
  {'help #'help-impl
   'quit #'quit-impl
   'connect #'connect-impl!
   'state #'state-impl})

(defn help-impl []
  (println "Available commands:")
  (println (keys cmd->impl)))

(defn show-intro! []
  (println "Driver 0.0.1"))

(defn prompt! [prompt-text]
  (print prompt-text)
  (flush)
  (read-line))

(defn parse [line]
  (map read-string (split line #"\s+")))

(defn read-input! []
  (parse (prompt! "> ")))

(defn execute! [cmds]
  (if (empty? cmds)
    nil
    (let [cmd (get cmd->impl (first cmds))
          args (rest cmds)]
      (if (nil? cmd) 
        (println "Unrecognized command:" (first cmds))
        (apply cmd args)))))

(defn -main [& args]
  (show-intro!)
  (loop []
    (execute! (read-input!))
    (recur)))
