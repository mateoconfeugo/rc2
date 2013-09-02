"""Base class for servo controllers."""

import logging

__author__ = "Nick Pascucci (npascut1@gmail.com)"

_LOG = logging.getLogger(__name__)

class ServoController(object):
    def __init__(self):
        pass

    def move_servo(self, servo, angle):
        raise NotImplementedError()

    def get_position(self, servo):
        raise NotImplementedError()


# TODO Add option to invert servo direction logic
class Calibration(object):
    def __init__(self, low_usec=985, high_usec=1995, low_angle=-55, high_angle=35):
        self.low_usec = low_usec
        self.high_usec = high_usec
        self.low_angle = low_angle
        self.high_angle = high_angle

    def get_duty_cycle(self, angle):
        if angle > self.high_angle or angle < self.low_angle:
            raise OutOfRangeError("Cannot reach {}, range is [{} -> {}]"
                                  .format(angle, self.low_angle, self.high_angle))
        usec_target = (angle - self.low_angle) * self.get_usec_per_deg() + self.low_usec
        return usec_target

    def get_angle(self, duty_cycle):
        if duty_cycle > self.high_usec or duty_cycle < self.low_usec:
            raise servocontroller.OutOfRangeError(
                "Cannot convert impossible duty cycle {}, range is ({}, {})"
                .format(duty_cycle, self.low_usec, self.high_usec))
        return ((duty_cycle - self.low_usec) / self.get_usec_per_deg())

    def get_usec_per_deg(self):
        return (self.high_usec - self.low_usec) / (self.high_angle - self.low_angle)


class OutOfRangeError(Exception):
    pass
