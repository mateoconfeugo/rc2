#! /usr/bin/env python2.7

"""Command line interface for executing roboscript programs."""

from delta import kinematics
from servo import pololu
from proto import comms_pb2 as pb
from net.s3psockets import AsyncS3PServerSocket

import argparse
import config
import controller
import logging
import parser
import positions
import serial
import socket
import time


__author__ = "Nick Pascucci (npascut1@gmail.com)"

arg_parser = argparse.ArgumentParser(description="Delta control server.")
arg_parser.add_argument("--log-level", help="set the logging verbosity",
                        default="WARNING", dest="loglevel",
                        choices=["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"])
arg_parser.add_argument("--log-file", help="set the logging output file",
                        dest="logfile")
arg_parser.add_argument("--serial-port", help="serial port attached to the servo controller",
                        dest="serial_port", required=True)
arg_parser.add_argument("--port", help="network port to listen on", type=int,
                        dest="port", required=True)

options = arg_parser.parse_args()

LOGFORMAT = "%(asctime)s %(levelname)s: %(message)s"
if hasattr(options, "logfile"):
    logging.basicConfig(level=getattr(logging, options.loglevel.upper()), 
                        format=LOGFORMAT, filename=options.logfile)
else:
    logging.basicConfig(level=getattr(logging, options.loglevel.upper()), 
                        format=LOGFORMAT)
_LOG = logging.getLogger(__name__)

def main():
    kin_calc = kinematics.DeltaKinematics(config.upper_arm_len, config.lower_arm_len, 
                                          config.effector_radius, config.base_radius)
    robot_serial = serial.Serial(options.serial_port, config.baud_rate, timeout=1)
    servo_controller = pololu.PololuController(robot_serial, 3)
    init_servos(servo_controller)
    robot_controller = controller.RobotController(kin_calc, servo_controller)

    # TODO Initialize network interface, read and execute from it
    sock = init_network()
    _LOG.info("Server initialized, now waiting for incoming connections.")
    try:
        read_and_execute(sock, robot_controller)
    finally:
        sock.stop()
        _LOG.info("Server shutdown complete.")

def init_servos(servo_controller):
    for i in range(3):
        servo_controller.set_acceleration_rough(i, 1)
        servo_controller.set_max_speed(i, 500)

def init_network():
    s = AsyncS3PServerSocket(options.port)
    s.start()
    return s

def read_and_execute(sock, controller):
    try:
        while True:
            packet = sock.next()
            if packet:
                _LOG.info("Packet received.")
                request = pb.Request()
                request.ParseFromString(packet)
                _LOG.info("Received request: < {} >".format(request))
                execute(request, sock, controller)
    except KeyboardInterrupt:
        return

def execute(request, sock, robot_controller):
    response = pb.Response()
    response.status == pb.Response.SUCCESS

    if request.type == pb.Request.PING:
        _LOG.debug("Ping request received")

    if request.type == pb.Request.WAYPOINT:
        # TODO This should fully specify the robot state. Some state needs to be stored; should it
        # be in the controller or here?
        coordinate = positions.Vector(request.waypoint.x, request.waypoint.y, request.waypoint.z)
        position = positions.Position(coordinate)
        robot_controller.move_to(position)
        # TODO Make the system wait for the move to complete before specifying the next one.

    sock.send(response.SerializeToString())
    _LOG.debug("Sent response: < {} >".format(response.SerializeToString()))

if __name__ == "__main__":
  main()
