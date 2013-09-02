#! /usr/bin/env python3.2

"""Unit tests for positions.py"""

import math
import positions
import unittest

__author__ = "Nick Pascucci (npascut1@gmail.com)"


class PositionsTest(unittest.TestCase):
    def setUp(self):
        pass

    def tearDown(self):
        pass

    def testNormalizeVectorOneAxis(self):
        v = positions.Vector(1, 0, 0)
        actual = positions.normalize(v)
        self.assertEqual(1, actual.x)
        self.assertEqual(0, actual.y)
        self.assertEqual(0, actual.z)

    def testNormalizeVectorThreeAxis(self):
        v = positions.Vector(1, 1, 1)
        actual = positions.normalize(v)
        self.assertEqual(1/math.sqrt(3), actual.x)
        self.assertEqual(1/math.sqrt(3), actual.y)
        self.assertEqual(1/math.sqrt(3), actual.z)


if __name__ == "__main__":
    unittest.main()
