"""Cube rotations in file (Bedrock / GeckoLib) degrees, in one place.

A cube or bone rotation (rx, ry, rz) in a .geo.json is applied by GeckoLib in its x-mirrored render space as
(-rx, -ry, rz), X first, then Y, then Z (render/ortho.py emulates it; tests/test_geckolib.py pins it to GeckoLib's
code). Seen in the file's own coordinates (the hero's right at -x, the hero facing -z) the same rotation is

    R_file = Rz(-rz) . Ry(ry) . Rx(-rx)

because mirroring x flips the sense of the rotations about y and z. Parts and the validator think in file space:
`file_matrix` turns file angles into that matrix and `file_angles` turns a wanted matrix back into file angles, so a
generator can build any orientation (a strand flared outward and fanned sideways, a tail segment along a curve) and
write it as three numbers.
"""
import math

import numpy as np


def rx(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[1.0, 0.0, 0.0], [0.0, c, -s], [0.0, s, c]])


def ry(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0.0, s], [0.0, 1.0, 0.0], [-s, 0.0, c]])


def rz(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0.0], [s, c, 0.0], [0.0, 0.0, 1.0]])


def file_matrix(angles):
    """The rotation a file rotation (rx, ry, rz) applies, in file coordinates."""
    if angles is None:
        return np.eye(3)
    a, b, c = angles
    return rz(-c) @ ry(b) @ rx(-a)


def file_angles(m, digits=2):
    """File angles (rx, ry, rz) whose file_matrix is m (a proper rotation). Rounded to `digits` decimals."""
    m = np.asarray(m, dtype=float)
    # m = Rz(A) Ry(B) Rx(C); then rx = -C, ry = B, rz = -A
    sb = -m[2, 0]
    sb = max(-1.0, min(1.0, sb))
    B = math.degrees(math.asin(sb))
    if abs(sb) < 0.999999:
        A = math.degrees(math.atan2(m[1, 0], m[0, 0]))
        C = math.degrees(math.atan2(m[2, 1], m[2, 2]))
    else:                                   # gimbal lock: put everything in C
        A = 0.0
        C = math.degrees(math.atan2(-m[1, 2], m[1, 1]))
    out = (-C, B, -A)
    return tuple(0.0 if abs(round(v, digits)) == 0 else round(v, digits) for v in out)


def rotate_points(points, angles, pivot):
    """Points rotated by file angles about a pivot (file coordinates)."""
    m = file_matrix(angles)
    pv = np.asarray(pivot, dtype=float)
    return [tuple(pv + m @ (np.asarray(p, dtype=float) - pv)) for p in points]


def align(frm, to):
    """The smallest rotation taking unit vector `frm` to unit vector `to` (Rodrigues)."""
    a = np.asarray(frm, dtype=float)
    b = np.asarray(to, dtype=float)
    a = a / np.linalg.norm(a)
    b = b / np.linalg.norm(b)
    v = np.cross(a, b)
    c = float(np.dot(a, b))
    if np.linalg.norm(v) < 1e-12:
        if c > 0:
            return np.eye(3)
        # opposite: rotate 180 degrees about any axis perpendicular to a
        axis = np.cross(a, [1.0, 0.0, 0.0])
        if np.linalg.norm(axis) < 1e-6:
            axis = np.cross(a, [0.0, 0.0, 1.0])
        return about(axis, 180.0)
    k = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + k + k @ k * (1.0 / (1.0 + c))


def about(axis, deg):
    """Rotation by `deg` about an axis (right-handed)."""
    k = np.asarray(axis, dtype=float)
    k = k / np.linalg.norm(k)
    a = math.radians(deg)
    K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
    return np.eye(3) + math.sin(a) * K + (1 - math.cos(a)) * K @ K


def box_corners(origin, size, inflate=0.0):
    x, y, z = (o - inflate for o in origin)
    x1, y1, z1 = (o + s + inflate for o, s in zip(origin, size))
    return [(a, b, c) for a in (x, x1) for b in (y, y1) for c in (z, z1)]
