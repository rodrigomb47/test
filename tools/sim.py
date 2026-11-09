#!/usr/bin/env python3
"""Drives a Faction Clash dedicated-server simulation over RCON and summarises the campaign state.

  python3 tools/sim.py <server_dir> summary          # dump + print summary
  python3 tools/sim.py <server_dir> sprint <ticks>    # /tick sprint and wait for completion, then summary
"""
import json
import os
import re
import sys
import time
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rcon  # noqa: E402


def log_path(sv):
    return os.path.join(sv, "logs", "latest.log")


def wait_for(sv, pattern, start_size, timeout=3600):
    rx = re.compile(pattern)
    t0 = time.time()
    while time.time() - t0 < timeout:
        with open(log_path(sv), "r", errors="replace") as f:
            f.seek(start_size)
            data = f.read()
        m = rx.search(data)
        if m:
            return m.group(0)
        time.sleep(2)
    raise TimeoutError(pattern)


def dump(sv):
    rcon.run(["fc admin dump"])
    with open(os.path.join(sv, "world", "factionclash_dump.json")) as f:
        return json.load(f)


SETTLEMENT_HALF = 24  # BaseLayout.HALF: the walls' half-size


def chebyshev(a, b):
    """Horizontal Chebyshev distance between two "x, y, z" strings."""
    ax, _, az = (int(v) for v in a.split(","))
    bx, _, bz = (int(v) for v in b.split(","))
    return max(abs(ax - bx), abs(az - bz))


def summary(d):
    print(f"== gameTime {d['gameTime']} (day {d['gameTime'] / 24000:.2f}) travellers {d['travellers']} winner {d.get('winner')}")
    for f in d["factions"]:
        alive = sum(1 for r in f["roster"] if r["alive"])
        dead = sum(1 for r in f["roster"] if not r["alive"])
        roles = Counter(r["role"] for r in f["roster"] if r["alive"])
        print(f"  {f['faction']:15s} alive={f['alive']} npcs={alive} dead={dead} reports={f['reportsDelivered']} map={f['knowledge']} "
              f"explored={f['explored']} raids={f['raidsLaunched']} raidTarget={f['raidTarget']} destroyed={f['basesDestroyed']} {dict(roles)}")
    for b in d["bases"]:
        line = f"  base {b['id']} {b['faction']:15s} {b['kind']:10s} {b['center']:>16s} destroyed={b['destroyed']}"
        if b["kind"] == "SETTLEMENT":
            st = b["stock"]
            keys = ["cobblestone", "cobbled_deepslate", "coal", "raw_iron", "raw_copper", "raw_gold", "redstone", "lapis_lazuli",
                    "diamond", "iron_ingot", "gold_ingot", "oak_log", "oak_sapling", "apple", "wheat", "bread", "beef", "porkchop",
                    "cooked_beef", "cod", "salmon", "torch", "iron_pickaxe"]
            line += f" station={b['stationPresent']} beds={b['beds']} repairs={b['pendingRepairs']} food={b['foodValue']} projects={b['projects']}"
            print(line)
            print("      stock " + ", ".join(f"{k}={st.get(k, 0)}" for k in keys))
            for w in b["workplaces"]:
                extra = ""
                if w["type"] == "MINE":
                    extra = (f" origin={w.get('origin')} facing={w.get('facing')} hut={w.get('hut')} bottom={w.get('bottom')}"
                             f" shaftLimit={w.get('shaftLimit')} maxRounds={w.get('maxRounds')} cur={w.get('cur')}")
                    if w.get("restTier"):
                        extra += f" resting(tier {w.get('restTier')})"
                    if w.get("workedOut"):
                        extra += f" WORKED-OUT({w.get('note')})"
                if w["type"] == "FISHERY":
                    extra = f" spot={w.get('spot')} origin={w.get('origin')} stand={w.get('extent')} open={w.get('open')}"
                    if w.get("spot") == "NONE":
                        extra += f" searched={w.get('searched')}"
                print(f"      {w['type']:9s} tier={w['tier']} output={w['output']} upgradeStep={w['upgradeStep']}{extra}")
                if w["type"] == "MINE":
                    for i, lv in enumerate(w.get("levels", [])):
                        mark = ">" if i == w.get("cur") else " "
                        print(f"        {mark} y={lv['y']:4d} {lv['band']:5s} {lv['progress']}/{lv['size']} rounds={lv['rounds']}"
                              + (" cut" if lv.get("cut") else ""))
                if w["type"] == "FISHERY" and w.get("spot") == "VALID" and w.get("origin"):
                    # a fishing spot lies 4 or more beyond every settlement's walls: never the farm's irrigation
                    for o in d["bases"]:
                        cheb = chebyshev(o["center"], w["origin"])
                        if o["kind"] == "SETTLEMENT" and not o["destroyed"] and cheb < SETTLEMENT_HALF + 4:
                            print(f"      ERROR: fishing spot #{w['id']} at {w['origin']} is only {cheb} from the centre of base {o['id']}")
            print(f"      recruit: {b['recruitNote'][:160]}")
            if b.get("pen"):
                print(f"      pen: {b['pen']}")
        else:
            print(line + f" mobs={b['lairMobs']}/{b['lairCap']}")
    st = Counter()
    for n in d["npcs"]:
        st[(n["faction"][:6], n["role"], re.sub(r"[-\d,() ]+%?", " ", n["status"])[:55].strip())] += 1
    for k, v in sorted(st.items()):
        print(f"    {v} x {k}")
    worn = [n for n in d["npcs"] if n["toolDamage"] > 0]
    print(f"  npcs loaded={len(d['npcs'])} with damaged tools={len(worn)} max level={max([n['level'] for n in d['npcs']] or [0])} "
          f"recoveries={sum(n['recoveries'] for n in d['npcs'])}")


if __name__ == "__main__":
    sv = sys.argv[1]
    cmd = sys.argv[2]
    if cmd == "sprint":
        size = os.path.getsize(log_path(sv))
        rcon.run([f"tick sprint {sys.argv[3]}"])
        print(wait_for(sv, r"Sprint completed[^\n]*", size))
    summary(dump(sv))
