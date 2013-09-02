#! /usr/bin/env python3.2

"""Unit tests for pololu.py"""

from servo import pololu

import unittest
import mock
import serial

__author__ = "Nick Pascucci (npascut1@gmail.com)"

NUM_SERVOS = 3

class PololuTest(unittest.TestCase):
    def setUp(self):
        self.serial = serial.Serial()
        self.serial.isOpen = mock.MagicMock(return_value=True)
        self.serial.write = mock.MagicMock(return_value=4)
        self.controller = pololu.PololuController(self.serial, NUM_SERVOS)

    def tearDown(self):
        pass

    def testServoCount(self):
        self.assertEqual(NUM_SERVOS, self.controller.num_servos)

if __name__ == "__main__":
    unittest.main()
