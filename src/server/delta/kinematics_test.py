#! /usr/bin/env python3.2

"""Unit tests for delta kinematics."""

import unittest
from delta import kinematics
import math
import positions

__author__ = "Nick Pascucci (npascut1@gmail.com)"

TEST_TOLERANCE = 0.000001

class DeltaKinematicsTest(unittest.TestCase):
    def setUp(self):
        self.kinematics = kinematics.DeltaKinematics(10, 10, 2, 2)

    def tearDown(self):
        pass

    def assertWithinTolerance(self, expected, actual, tolerance):
        if not math.fabs(expected - actual) <= tolerance:
            raise AssertionError("{} is not within {} of {}".format(actual, tolerance, expected))

    def testRotateHalfWay(self):
        original_point = positions.Vector(10, 10, 10)
        rotated_point = self.kinematics._rotate(original_point, math.pi)
        self.assertWithinTolerance(-10, rotated_point.x, TEST_TOLERANCE)
        self.assertWithinTolerance(-10, rotated_point.y, TEST_TOLERANCE)
        self.assertWithinTolerance(10, rotated_point.z, TEST_TOLERANCE)

    def testRotateQuarterTurn(self):
        original_point = positions.Vector(10, 10, 10)
        rotated_point = self.kinematics._rotate(original_point, math.pi/2)
        self.assertWithinTolerance(10, rotated_point.x, TEST_TOLERANCE)
        self.assertWithinTolerance(-10, rotated_point.y, TEST_TOLERANCE)
        self.assertWithinTolerance(10, rotated_point.z, TEST_TOLERANCE)


    # TODO Update this test to reflect fixed kinematics
    def testInverse3d(self):
        target = positions.Vector(0, 0, 10)
        actual = self.kinematics._inverse_3d(target)
        self.assertWithinTolerance(math.pi/4, actual, TEST_TOLERANCE)

        target = positions.Vector(10, 0, 10)
        actual = self.kinematics._inverse_3d(target)
        self.assertWithinTolerance(0, actual, TEST_TOLERANCE)

        target = positions.Vector(-10, 0, 10)
        actual = self.kinematics._inverse_3d(target)
        self.assertWithinTolerance(math.pi/2, actual, TEST_TOLERANCE)


if __name__ == "__main__":
    unittest.main()
