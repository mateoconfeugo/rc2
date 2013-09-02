"""Poses and positions for robot arms."""

# Should this be replaced with numpy arrays?

import logging
import math
from collections import namedtuple

__author__ = "Nick Pascucci (npascut1@gmail.com)"

_LOG = logging.getLogger(__name__)

class Pose:
    """A set of angles for robot arms describing their joint positions."""

    def __init__(self, angles={}):
        self.angles = angles

    def get_angles(self):
        return self.angles.items()

    def set_angle(self, idx, angle):
        self.angles[idx] = angle

Vector = namedtuple("Vector", ["x", "y", "z"])

def normalize(vector):
    vector_length = math.sqrt(vector.x**2 + vector.y**2 + vector.z**2)
    return Vector(vector.x/vector_length, vector.y/vector_length, vector.z/vector_length)


class Position:
    def __init__(self, coordinate=Vector(0, 0, 0), orientation=Vector(0, 0, 0)):
        self.coordinate = coordinate
        self.orientation = orientation
