#! /usr/bin/env python3.2

"""Unit tests for pololu.py"""

import servo.servocontroller as sc
import unittest
import mock
import serial

__author__ = "Nick Pascucci (npascut1@gmail.com)"

class ServoControllerTest(unittest.TestCase):
    def setUp(self):
        pass

    def tearDown(self):
        pass

class CalibrationTest(unittest.TestCase):
    def setUp(self):
        self.calibration = sc.Calibration(low_usec=10, high_usec=110, low_angle=0, high_angle=100)

    def tearDown(self):
        pass

    def testConvertAngleToDutyCycle(self):
        duty_cycle = self.calibration.get_duty_cycle(50)
        self.assertEqual(60, duty_cycle)

    def testConvertAngleToDutyCycleNegative(self):
        calibration = sc.Calibration(low_usec=0, high_usec=100, low_angle=-50, high_angle=50)
        duty_cycle = calibration.get_duty_cycle(-10)
        self.assertEqual(40, duty_cycle)

    def testConvertDutyCycleToAngle(self):
        angle = self.calibration.get_angle(90)
        self.assertEqual(80, angle)

    def testGetUsecPerDeg(self):
        usec_per_deg = self.calibration.get_usec_per_deg()
        self.assertEqual(1, usec_per_deg)


if __name__ == "__main__":
    unittest.main()
