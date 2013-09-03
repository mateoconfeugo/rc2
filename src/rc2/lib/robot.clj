(ns rc2.lib.robot
  (:use rc2.lib.net
        protobuf.core
        [clojure.string :only (upper-case)]
        clojure.core.async))

(import 'com.pascucci.rc2.Comms$Request)
(import 'com.pascucci.rc2.Comms$Response)
(import 'com.pascucci.rc2.Comms$Waypoint)

;;;; Functions for interacting with robots.
(def Request (protodef Comms$Request))
(def Response (protodef Comms$Response))
(def Waypoint (protodef Comms$Waypoint))

(defn- to-enum [sym]
  "Convert a Clojure-style symbol to a protobuf enum symbol. For example, :no-op becomes :NO_OP.
This is necessary to work around a bug in protobuf.core where symbols in the wrong format are not
recognized as enum values."
  (keyword (replace (upper-case (name sym)) "-" "_")))

(defn- make-waypoint [{:keys [x y z operation] :or {x 0 y 0 z 0 operation :no-op}}]
  "Convert a Clojure waypoint into a protocol buffer."
  (assoc (protobuf Waypoint) :x x :y y :z z :operation (to-enum operation)))

(defn- make-request [waypoint]
  "Wrap a Clojure waypoint into a request protocol buffer."
  (assoc (protobuf Request) :waypoint (make-waypoint waypoint) :type (to-enum :waypoint)))

(defn ping! [{:keys [outbound] :as conn}]
  (>!! outbound (assoc (protobuf Request) :type (to-enum :ping)))
  conn)

(defn send-cmds! [cmds {:keys [outbound] :as conn}]
  "Execute a series of waypoints with the given robot connection."
  (map #(>!! outbound (make-request %)) cmds)
  conn)

(defn connect! [host port]
  "Connect to the robot server running on host:port and return a descriptor of the connection."
  (let [outbound-chan (chan)
        connection {:outbound outbound-chan
                    :inbound (async-protobuf-connection Response host port outbound-chan)}]
    (ping! connection)))
