#!/usr/bin/env python3
"""Reads a tools/rolltrack.py output folder and checks the war and the roll call against the truth.

  python3 tools/warcheck.py <track_dir> [from_tick] [to_tick]

Sections (docs/design/knowledge.md 12, the knowledge review's live check):
  launches  each raid launched: the report the council's estimate rested on (prior: no look or a look that saw none of
            the yard; look: a look into the yard; losses: enemy soldiers seen falling at home), its "about N defenders"
            beside the soldiers truly in the target's walls, the party, the raiders who set out and how many of them died
  leak      each departure of 3 or more soldiers from a garrison, with every other council's estimate of that base at
            the poll before and the next five: an estimate that moves at a departure without a report of its own is a leak
  roll      every member reported missing or presumed dead, beside the truth (dead, vanished from the engine)
  miners    expected returns changed while a miner was away (the shift is fixed at the storehouse: none expected)
  surveys   each cave survey line with the explorer's distance and status at the polls around it (handed in at home)
  far       fighters with a target more than 24 blocks off (Pursuit.WATCH) at a poll, and how many polls in a row they
            kept it: one waiting out of reach lets go within about ten seconds (the archers' bug kept one for two days)
  frozen    members standing on one spot with a moving status ("Walking", "Marching", "Digging") for a quarter day or
            more: a stuck member the stuck recovery does not see (an archer holding a far target was one; a fisherman
            waiting at its spot for daylight keeps "Walking to the fishing spot" and is no fault)
"""
import json
import os
import re
import sys

T = sys.argv[1]
T0 = int(sys.argv[2]) if len(sys.argv) > 2 else 0
T1 = int(sys.argv[3]) if len(sys.argv) > 3 else 10 ** 12
COMBAT = {"swordsman", "archer", "crossbowman", "axe_fighter"}
NAMES = {"ember_legion": "Ember Legion", "grove_covenant": "Grove Covenant", "tide_alliance": "Tide Alliance",
         "stoneguard": "Stoneguard", "hollow": "The Hollow"}
MOVING = ("Walking", "Marching", "Digging", "Going", "Heading")


def xyz_of(text):
    try:
        v = [float(x) for x in text.replace(" ", "").split(",")[:3]]
        return v if len(v) == 3 else None
    except ValueError:
        return None


def day(t):
    return t / 24000.0


def lines(name):
    p = os.path.join(T, name)
    return [ln.rstrip("\n") for ln in open(p)] if os.path.exists(p) else []


def jsonl(name):
    out = []
    p = os.path.join(T, name)
    if os.path.exists(p):
        for ln in open(p):
            r = json.loads(ln)
            if T0 <= r["t"] <= T1:
                out.append(r)
    return out


tl = jsonl("timeline.jsonl")
war = jsonl("war.jsonl")
wl = lines("war.log")
ev = lines("events.log")
chat = lines("chat.log")
in_range = lambda d: T0 / 24000 <= d <= T1 / 24000 + 1e-3  # noqa: E731
if tl:
    print(f"### {T}: days {day(tl[0]['t']):.2f} to {day(tl[-1]['t']):.2f}, {len(tl)} polls")


def basis(rep, kind):
    if rep.startswith("ruins"):
        return "ruins"
    m = re.match(r"look (\d+) soldiers, (\d+)% of yard.*?losses (\d+)", rep)
    if not m:
        return "prior (no look)"
    soldiers, seen, losses = int(m.group(1)), int(m.group(2)), int(m.group(3))
    if kind == "lair":
        return "lair look" if seen or soldiers else "lair prior"
    if losses:
        return f"losses {losses}"
    return f"look {seen}%" if seen or soldiers else "prior (blind look)"


print("\n=== launches ===")
print(f"{'day':>7s} {'launcher':15s} {'target':15s} {'kind':10s} party about basis              truth           out dead")
for i, ln in enumerate(wl):
    m = re.match(r"\s*([\d.]+) (\S+)\s+LAUNCH #(\d+) on base (\d+) \((\S+) (\w+)\)", ln)
    if not m or not in_range(float(m.group(1))):
        continue
    d, fac, subject, kind = float(m.group(1)), m.group(2), m.group(5), m.group(6)
    rep = wl[i + 1].split("report used: ", 1)[1]
    tru = wl[i + 2].split("truth now: ", 1)[1]
    said = [c for c in chat if c.split()[0].replace(".", "", 1).isdigit() and abs(float(c.split()[0]) - d) < 0.002
            and f"[{NAMES[fac]}]" in c and "Launching a raid" in c]
    p = re.search(r"raid with (\d+) soldiers \(against about (\d+) defenders", said[0]) if said else None
    out = set()
    for e in ev:
        r = re.match(r"\s*([\d.]+) t=\d+ (\S+)\s+ROLL (.+?) \((\w+)\) where:HERE->AWAY", e)
        if r and r.group(2) == fac and r.group(4) in COMBAT and d - 1e-3 <= float(r.group(1)) <= d + 0.08:
            out.add(r.group(3))
    dead = set()
    for e in ev:
        r = re.match(r"\s*([\d.]+) t=\d+ (\S+)\s+ROLL (.+?) \((\w+)\) .*dead:False->True", e)
        if r and r.group(2) == fac and r.group(3) in out and d <= float(r.group(1)) <= d + 1.0:
            dead.add(r.group(3))
    g = re.match(r"(\d+) soldiers in its walls, heroes \[(.*?)\]", tru)
    truth = (f"{g.group(1)}" + (f" + {g.group(2).count(chr(39)) // 2} hero" if g.group(2) else "")) if g else tru
    counter = " counter" if said and "Counter" in said[0] else ""
    print(f"{d:7.3f} {fac:15s} {subject:15s} {kind:10s} {p.group(1) if p else '?':>5s} {p.group(2) if p else '?':>5s} "
          f"{basis(rep, kind):18s} {truth:15s} {len(out):3d} {len(dead):4d}{counter}")

print("\n=== leak: garrison departures and the other councils' estimate of that base ===")
bases = {b["id"]: b for b in war[-1]["bases"]} if war else {}
for k in range(1, len(war)):
    prev, rec = war[k - 1], war[k]
    for bid, g in rec["garrisons"].items():
        pg = prev["garrisons"].get(bid)
        if pg is None or pg["soldiers"] - g["soldiers"] < 3:
            continue
        b = bases.get(int(bid))
        owner = b["faction"] if b else "?"
        print(f"  {day(rec['t']):.3f} base {bid} ({owner}) garrison {pg['soldiers']} -> {g['soldiers']}")
        for j in range(k - 1, min(len(war), k + 6)):
            for f in war[j]["factions"]:
                c = f.get("council") or ""
                if f"base {bid} " not in c or f["faction"] == owner:
                    continue
                m = re.search(r"defence ([\d.]+)", c)
                reps = [x for x in f.get("enemyBasesKnown") or [] if x["subject"] == owner]
                rs = "; ".join(f"look day {x.get('lookAt', 0) / 24000:.2f} {int(100 * x.get('visible', 0))}% "
                               f"{x.get('soldiers')} seen, losses {x.get('losses')}" for x in reps)
                print(f"     {'before' if j < k else '+' + str(j - k):>6s} {day(war[j]['t']):.3f} {f['faction']:15s} "
                      f"estimate {m.group(1) if m else '?':>5s} | {rs}")

print("\n=== roll: missing and presumed dead beside the truth ===")
alive_missing = 0
for e in ev:
    m = re.match(r"\s*([\d.]+) t=(\d+) (\S+)\s+ROLL (.+?) \((\w+)\) where:(\w+)->(MISSING|PRESUMED_DEAD)", e)
    if not m or not (T0 <= int(m.group(2)) <= T1):
        continue
    snap = min(tl, key=lambda s: abs(s["t"] - int(m.group(2))))["roster"] if tl else {}
    rec = [v for v in snap.values() if v[1] == m.group(4) and v[0] == m.group(3)]
    dead, vanished = (rec[0][5], rec[0][6]) if rec else ("?", "?")
    flag = "" if dead is True else ("  <- vanished (engine lost it)" if vanished else "  <- ALIVE")
    alive_missing += 1 if dead is False and not vanished else 0
    print(f"  {m.group(1)} {m.group(3):15s} {m.group(4)} ({m.group(5)}) {m.group(6)}->{m.group(7)} dead={dead}{flag}")
print(f"  {alive_missing} living members reported missing or presumed dead")

print("\n=== miners: expected return changed while away ===")
where, changed = {}, 0
for e in ev:
    m = re.match(r"\s*([\d.]+) t=(\d+) (\S+)\s+ROLL (.+?) \((miner|cave_miner)\) (.*)", e)
    if not m or not (T0 <= int(m.group(2)) <= T1):
        continue
    w = re.search(r"where:(\w+)->(\w+)", m.group(6))
    if w:
        where[m.group(4)] = w.group(2)
    elif "expectedBy:" in m.group(6) and where.get(m.group(4)) == "AWAY":
        changed += 1
        print("  " + e[:200])
print(f"  {changed} changes")

print("\n=== surveys: the explorer at the polls around each line ===")
far = 0
for c in chat:
    m = re.match(r"\s*([\d.]+) \[(.+?)\] Cave explorer (.+?) surveyed", c)
    if not m or not in_range(float(m.group(1))):
        continue
    k = max([i for i, s in enumerate(tl) if day(s["t"]) <= float(m.group(1)) + 1e-6] or [0])
    seen = []
    for s in tl[max(0, k - 1):k + 2]:
        v = [n for n in s["npcs"].values() if n[1] == m.group(3) and n[2] == "cave_explorer"]  # (names repeat)
        seen.append((v[0][4], v[0][5][:26]) if v else None)
    ok = any(x and (x[0] <= 40 or "reporting station" in x[1] or "Handing in" in x[1]) for x in seen)
    far += 0 if ok else 1
    print(f"  {m.group(1)} {m.group(2):15s} {m.group(3):14s} {seen}{'' if ok else '  <- not seen at home'}")
print(f"  {far} lines with the explorer neither home nor bound for the station at the polls around them")

print("\n=== far: fighters keeping a target more than 24 blocks off (Pursuit.WATCH) ===")
streak, worst, seen_far = {}, {}, 0
for s in tl:
    now = set()
    for u, n in s["npcs"].items():
        if len(n) < 9 or not n[7] or "@" not in n[7]:
            continue
        a, b = xyz_of(n[3]), xyz_of(n[7].split("@", 1)[1])
        if a is None or b is None:
            continue
        d = ((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2) ** 0.5
        if d > 24:
            seen_far += 1
            now.add(u)
            k = streak.get(u, [s["t"], 0, n])
            k[1] += 1
            k[2] = n + [round(d)]
            streak[u] = k
    for u in list(streak):
        if u not in now:
            k = streak.pop(u)
            if k[1] > worst.get(u, [0, 0])[1]:
                worst[u] = k
for u, k in streak.items():
    if k[1] > worst.get(u, [0, 0])[1]:
        worst[u] = k
long = sorted((k for k in worst.values() if k[1] >= 3), key=lambda k: -k[1])
for t0, polls, n in long[:20]:
    print(f"  {day(t0):6.2f} {polls:4d} polls {n[0]:15s} {n[1]:16s} {n[2]:12s} {n[8]:6s} target {n[7][:40]} {n[-1]} blocks")
print(f"  {seen_far} fighter-polls with a far target; {len(worst)} fighters; {len(long)} kept one for 3 polls or more")

print("\n=== frozen: a moving status on one spot for a quarter day or more ===")
runs, found = {}, []
for s in tl:
    for u, n in s["npcs"].items():
        if n[6] <= 0:
            continue  # a body in a chunk that stopped ticking before its death animation ended
        key = (n[3], n[5])
        r = runs.get(u)
        if r and r[0] == key:
            r[2] = s["t"]
            continue
        if r and r[2] - r[1] >= 6000 and r[0][1].startswith(MOVING):
            found.append(r)
        runs[u] = [key, s["t"], s["t"], (n[0], n[1], n[2])]
found += [r for r in runs.values() if r[2] - r[1] >= 6000 and r[0][1].startswith(MOVING)]
for key, a, b, who in sorted(found, key=lambda r: r[1]):
    print(f"  {day(a):6.2f}-{day(b):6.2f} ({day(b - a):.2f} d) {who[0]:15s} {who[1]:16s} {who[2]:12s} {key[0]:>16s} {key[1][:48]}")
print(f"  {len(found)} found")
