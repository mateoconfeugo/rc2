"""Kinematics functions for delta robots."""

import logging
import math
import positions

__author__ = "Nick Pascucci (npascut1@gmail.com)"

_LOG = logging.getLogger(__name__)

class Kinematics(object):
    def __init__(self):
        pass

    def find_pose(self, position):
        raise NotImplementedError()

    def find_position(self, pose):
        raise NotImplementedError()


class DeltaKinematics(Kinematics):
    """A Kinematics implementation for delta-style parallel robots."""

    def __init__(self, upper_arm_len, lower_arm_len, effector_radius, base_radius):
        # Should set arm segment lengths here
        self.upper_arm_len = upper_arm_len
        self.lower_arm_len = lower_arm_len
        self.effector_radius = effector_radius
        self.base_radius = base_radius
        _LOG.debug(("Initialized servo controller with params upper_arm_len={}, lower_arm_len={},"
                    " effector_radius={}, base_radius={}")
                   .format(upper_arm_len, lower_arm_len, effector_radius, base_radius))

    def find_pose(self, position):
        """Find a pose which will position the end effector at position.

        Args:
          position: A Position instance describing the desired end effector position.
        Returns:
          A Pose instance describing how to position the robot.
        Raises:
          UnreachablePositionError: There is no pose that can reach position.
        """
        self._check_reachability(position.coordinate)
        pose = positions.Pose()
        try:
            pose.set_angle(0, self._inverse_3d(position.coordinate))
            rotated_coordinate = self._rotate(position.coordinate, 2*math.pi/3)
            pose.set_angle(1, self._inverse_3d(rotated_coordinate))
            rotated_coordinate = self._rotate(position.coordinate, 4*math.pi/3)
            pose.set_angle(2, self._inverse_3d(rotated_coordinate))
        except ValueError:
            raise UnreachablePositionError("Desired position {} is unreachable".format(position))
        return pose

    def _check_reachability(self, coordinate):
        displacement = math.sqrt(coordinate.x ** 2 + coordinate.y ** 2 + coordinate.z ** 2)
        if displacement > self.upper_arm_len + self.lower_arm_len:
            raise UnreachablePositionError("Requested coordinate {} is outside of arm range"
                                           .format(coordinate))

    def _inverse_3d(self, coordinate):
        """Find the positioning for one actuator in the XZ plane in order to reach coordinate."""
        _LOG.debug("Calculating inverse kinematics for coordinate {}".format(coordinate))
        # c is the length of a vector from the servo to the target point where the arm meets the end
        # effector.
        c = math.sqrt((coordinate.x + self.effector_radius - self.base_radius)**2 + coordinate.z**2)
        _LOG.debug("c = {}".format(c))
        a2 = self.lower_arm_len**2 - coordinate.y**2
        _LOG.debug("a^2 = {}".format(a2))
        # alpha is the angle between the upper arm and the c vector.
        alpha = math.acos((self.upper_arm_len**2 + c**2 - a2) / (2 * self.upper_arm_len * c))
        _LOG.debug("alpha = {}".format(alpha))
        # beta is the angle between the XY plane and the c vector.
        beta = math.atan2(coordinate.z, (coordinate.x + self.effector_radius - self.base_radius))
        _LOG.debug("beta = {}".format(beta))
        # Subtracting alpha from beta gives us the angle between the upper arm and the XY plane,
        # where angles above the plane are negative and below are positive, to match our model.
        angle = alpha - beta
        _LOG.debug("Calculated angle: {}rad".format(angle))
        return angle

    # TODO Take a look at the angle calculations and double-check the range.
    def _normalize(self, angle):
        norm_angle = angle % (2 * math.pi)
        # if norm_angle > 1.5 * math.pi:
        #     norm_angle = norm_angle - 2 * math.pi
        # return norm_angle
        return norm_angle

    def _rotate(self, coordinate, theta):
        """Rotate a point around the Z axis by theta radians."""
        cos_theta = math.cos(theta)
        sin_theta = math.sin(theta)
        x = (cos_theta * coordinate.x) + (sin_theta * coordinate.y)
        z = coordinate.z
        y = (-sin_theta * coordinate.x) + (cos_theta * coordinate.y)
        return positions.Vector(x, y, z)

    def find_position(self, pose):
        """Find the position of the end effector when the robot is positioned according to pose.

        Args:
            pose: A Pose instance describing the robot pose state.
        Returns:
            A Position instance describing the location of the end effector.
        """
        # TODO Implement
        pass

class UnreachablePositionError(Exception):
    pass
