"""ServoController interface for Pololu controller."""

from collections import namedtuple
import logging
import servo.servocontroller as servocontroller
import struct
import time

__author__ = "Nick Pascucci (npascut1@gmail.com)"

Calibration = namedtuple("Calibration", ["low_usec", "high_usec", "max_angle"])

_LOG = logging.getLogger(__name__)
# MAX_USEC = 985
# MIN_USEC = 1995
# DEFAULT_CALIBRATION = Calibration(MIN_USEC, MAX_USEC, 90)

# TODO Provide API for: calibration, acceleration setting, moving multiple servos at once, soft
# limits

# TODO Catch and handle exceptions

# TODO Tweak calibration to have upper and lower ranges rather than assuming range starts at 0

class PololuController(servocontroller.ServoController):
    """Provides an API to control a Pololu Maestro serial servo controller."""

    def __init__(self, serial, num_servos):
        assert(serial.isOpen())
        self.serial = serial
        self.num_servos = num_servos
        self._calibrations = [servocontroller.Calibration() for i in range(num_servos)]
        # Serial baud rate detection requires we send a single byte.
        self.serial.write(struct.pack("B", 0xAA))

    # TODO Test set_acceleration. Doesn't seem to be doing the right thing.
    def set_acceleration(self, servo, accel):
        """Set the maximum acceleration of servo in degrees per second squared."""
        # The Pololu board expects velocity in terms of .25us/10ms/80ms.
        _LOG.debug("Setting servo {} acceleration to {}deg/sec^2".format(servo, accel))
        usec_per_deg = self._calibrations[servo].get_usec_per_deg()
        _LOG.debug("Servo {} is calibrated for {}usec/deg".format(servo, usec_per_deg))
        pololu_accel = usec_per_deg * accel * 4 / 100 / 12.5
        _LOG.debug("Calculated Pololu acceleration value of {} [0.25usec/10ms/80ms]"
                   .format(pololu_accel))
        pololu_accel = int(pololu_accel)
        if pololu_accel > 255:
            _LOG.warning("Cannot set acceleration higher than 255.")
            pololu_accel = 255
        low_bits = pololu_accel & 0x7F
        high_bits = (pololu_accel >> 7) & 0x7F
        command = struct.pack("BBBB", 0x89, servo, low_bits, high_bits)
        bytes_written = self.serial.write(command)
        _LOG.debug("Wrote {} bytes".format(bytes_written))

    def set_acceleration_rough(self, servo, accel):
        low_bits = accel & 0x7F
        high_bits = (accel >> 7) & 0x7F
        command = struct.pack("BBBB", 0x89, servo, low_bits, high_bits)
        bytes_written = self.serial.write(command)
        _LOG.debug("Wrote {} bytes".format(bytes_written))

    # TODO Test set_max_speed
    def set_max_speed(self, servo, speed):
        """Set the maximum servo speed in degrees per second."""
        # The Pololu board expects velocity in terms of .25us/10ms.
        _LOG.debug("Setting servo {} speed to {}deg/sec".format(servo, speed))
        pololu_speed = int(self._calibrations[servo].get_usec_per_deg() * speed / 100)
        low_bits = pololu_speed & 0x7F
        high_bits = (pololu_speed >> 7) & 0x7F
        command = struct.pack("BBBB", 0x89, servo, low_bits, high_bits)
        bytes_written = self.serial.write(command)
        _LOG.debug("Wrote {} bytes".format(bytes_written))

    def move_servo(self, servo, angle):
        _LOG.debug("Moving servo {} to {}deg".format(servo, angle))
        duty_cycle_us = self._calibrations[servo].get_duty_cycle(angle)
        _LOG.debug("Duty cycle: {} us".format(duty_cycle_us))
        self._move_servo_microseconds(servo, duty_cycle_us)

    def _move_servo_microseconds(self, servo, duty_cycle_us):
        # The Maestro expects duty cycles in quarter-microseconds
        pulse = int(duty_cycle_us * 4)
        _LOG.debug("Sending request for pulse rate {}".format(pulse))
        low_bits = pulse & 0x7F
        high_bits = (pulse >> 7) & 0x7F
        command = struct.pack("BBBB", 0x84, servo, low_bits, high_bits)
        bytes_written = self.serial.write(command)
        _LOG.debug("Wrote {} bytes".format(bytes_written))

    def get_position(self, servo):
        command = struct.pack("BB", 0x90, servo)
        self.serial.write(command)
        reply = self.serial.read(2)
        # The reply is a little-endian short
        duty_cycle_us = struct.unpack("<H", reply)[0]
        return self._calibrations[servo].get_angle(duty_cycle_us)

    def set_calibration(self, servo, calibration):
        if not isinstance(calibration, Calibration):
            raise ValueError("set_calibration requires Calibration object")
        self._calibrations[servo] = calibration

    def is_moving(self):
        command = struct.pack("B", 0x93)
        self.serial.write(command)
        reply = self.serial.read(1)
        # Reply is 0x00 if no servos are moving, 0x01 if they are.
        servo_moving = struct.unpack("B", reply)[0]
        _LOG.debug("Query for moving servos returned {}".format(servo_moving))
        return servo_moving > 0
