#!/usr/bin/env python3
"""Minimal Minecraft RCON client used by the server simulation tests.

Usage: python3 tools/rcon.py [--host H] [--port P] [--password PW] "command" ["command" ...]
"""
import argparse
import socket
import struct
import sys


def _packet(req_id, ptype, body):
    data = struct.pack("<ii", req_id, ptype) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(data)) + data


def _read(sock):
    raw = b""
    while len(raw) < 4:
        chunk = sock.recv(4 - len(raw))
        if not chunk:
            raise ConnectionError("rcon closed")
        raw += chunk
    (length,) = struct.unpack("<i", raw)
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise ConnectionError("rcon closed")
        data += chunk
    req_id, ptype = struct.unpack("<ii", data[:8])
    return req_id, ptype, data[8:-2].decode("utf-8", "replace")


def run(commands, host="127.0.0.1", port=25575, password="fctest", timeout=600):
    out = []
    with socket.create_connection((host, port), timeout=timeout) as s:
        s.sendall(_packet(1, 3, password))
        rid, _, _ = _read(s)
        if rid == -1:
            raise PermissionError("bad rcon password")
        for i, cmd in enumerate(commands):
            s.sendall(_packet(10 + i, 2, cmd))
            _, _, body = _read(s)
            out.append(body)
    return out


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=25575)
    ap.add_argument("--password", default="fctest")
    ap.add_argument("commands", nargs="+")
    a = ap.parse_args()
    for line in run(a.commands, a.host, a.port, a.password):
        print(line)
    sys.exit(0)
