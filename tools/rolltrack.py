#!/usr/bin/env python3
"""Roll call tracker for a live campaign (docs/design/missing_npcs.md, docs/HANDOFF.md section 4).

Polls `fc admin dump` and appends to <out_dir>/events.log every change in each faction's view of its members (where,
alive, expected return, since) beside the truth (dead, vanished, witnessed), every member crossing its home area's edge
(truth, with the farthest point of the trip), news carried and dropped, and hero deaths and revivals; one JSON sample per
poll goes to <out_dir>/timeline.jsonl.

The war (docs/design/knowledge.md): each poll also appends to <out_dir>/war.jsonl every faction's council line, raid
counters and reports of enemy bases (the look each rests on) beside the truth (each settlement's soldiers in its walls,
each faction's raid under way), and <out_dir>/war.log gets each change of a council's line, each launch with the report
it used and the truth, each raid's end and each report delivered or changed. Faction lines from the server log go to
<out_dir>/chat.log stamped with the game day of the poll that read them.

  python3 tools/rolltrack.py <server_dir> <out_dir> [interval_s] [home_radius]
Stops when <out_dir>/STOP exists. The server must be up with RCON (tools/rcon.py defaults).
"""
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rcon  # noqa: E402

SV, OUT = sys.argv[1], sys.argv[2]
IV = float(sys.argv[3]) if len(sys.argv) > 3 else 8.0
HOME = int(sys.argv[4]) if len(sys.argv) > 4 else 64  # campaign.homeAreaRadius


def xyz(s):
    a = [int(v) for v in s.split(",")]
    return a[0], a[1], a[2]


def cheb(a, b):
    return max(abs(a[0] - b[0]), abs(a[2] - b[2]))


COMBAT = {"swordsman", "archer", "crossbowman", "axe_fighter"}
WALLS = 24 + 8  # BaseLayout.HALF plus the look's 8 blocks round the walls (campaign/BaseWatch)


def garrisons(d):
    """The truth: each standing settlement's soldiers (heroes apart) within its walls box plus 8, by base id."""
    out = {}
    for b in d["bases"]:
        if b["kind"] != "SETTLEMENT" or b["destroyed"]:
            continue
        c = xyz(b["center"])
        sol = sum(1 for n in d["npcs"] if n["faction"] == b["faction"] and n["role"] in COMBAT and not n.get("hero")
                  and cheb(xyz(n["pos"]), c) <= WALLS)
        her = [h["id"] for h in d.get("heroes", []) if h.get("owner") == b["faction"] and h.get("alive") and h.get("pos")
               and cheb(xyz(h["pos"]), c) <= WALLS]
        out[b["id"]] = {"soldiers": sol, "heroes": her}
    return out


def report_of(fac, base):
    """The report a council's line about this base rests on: the faction's report of that subject nearest the base."""
    c = xyz(base["center"])
    best = None
    for r in fac.get("enemyBasesKnown", []):
        if r["subject"] != base["faction"]:
            continue
        dist = cheb(xyz(r["pos"]), c)
        if dist < 32 and (best is None or dist < best[0]):
            best = (dist, r)
    return None if best is None else best[1]


def look_text(r, t):
    if r is None:
        return "no report"
    if r.get("fallen"):
        return f"ruins (by {r.get('by')})"
    if "lookAt" not in r:
        return f"no look (by {r.get('by')})"
    age = (t - r["lookAt"]) / 24000.0
    return (f"look {r['soldiers']} soldiers, {int(r['visible'] * 100)}% of yard, power {r['power']}, day "
            f"{r['lookAt'] / 24000.0:.2f} ({age:.2f}d old), losses {r['losses']}, heroes [{r.get('heroesSeen', '')}], by {r.get('by')}")


class War:
    """The war record: council lines, launches with the report used beside the truth, raids' ends, reports."""

    def __init__(self):
        self.wl = open(os.path.join(OUT, "war.log"), "a")
        self.wj = open(os.path.join(OUT, "war.jsonl"), "a")
        self.chat = open(os.path.join(OUT, "chat.log"), "a")
        self.council, self.launched, self.target, self.reports, self.left = {}, {}, {}, {}, {}
        self.log_pos = 0

    def poll(self, d):
        t = d["gameTime"]
        day = t / 24000.0
        bases = {b["id"]: b for b in d["bases"]}
        truth = garrisons(d)
        rec = {"t": t, "garrisons": truth, "bases": [{k: b[k] for k in ("id", "faction", "kind", "center", "destroyed", "lairMobs")}
                                                     for b in d["bases"]], "factions": []}
        for fac in d["factions"]:
            fn = fac["faction"]
            rec["factions"].append({k: fac.get(k) for k in ("faction", "alive", "raidsLaunched", "raidTarget", "raidPlanTicks",
                                                             "basesDestroyed", "knowledge", "council", "enemyBasesKnown")})
            line = fac.get("council", "")
            if line and line != self.council.get(fn):
                self.wl.write(f"{day:8.3f} {fn:15s} COUNCIL {line}\n")
            self.council[fn] = line
            n = fac.get("raidsLaunched", 0)
            tgt = fac.get("raidTarget", -1)
            if fn in self.launched and n > self.launched[fn] and tgt in bases:
                b = bases[tgt]
                g = truth.get(tgt)
                away = [o["faction"] for o in d["factions"] if o["faction"] == b["faction"] and o.get("raidTarget", -1) >= 0]
                left = self.left.get(b["faction"])
                self.wl.write(f"{day:8.3f} {fn:15s} LAUNCH #{n} on base {tgt} ({b['faction']} {b['kind'].lower()}): {line}\n"
                              f"{'':25s} report used: {look_text(report_of(fac, b), t)}\n"
                              f"{'':25s} truth now: " + (f"{g['soldiers']} soldiers in its walls, heroes {g['heroes']}" if g else
                                                         f"lair mobs {b['lairMobs']}")
                              + (f"; its own raid party out since day {left / 24000.0:.2f}" if away and left else "") + "\n")
            if fn in self.target and self.target[fn] >= 0 and tgt < 0:
                self.wl.write(f"{day:8.3f} {fn:15s} RAID OVER (was base {self.target[fn]})\n")
            if tgt >= 0 and self.target.get(fn, -1) < 0:
                self.left[fn] = t
            self.launched[fn] = n
            self.target[fn] = tgt
            for r in fac.get("enemyBasesKnown", []):
                key = (fn, r["subject"], r["pos"])
                sig = (r.get("lookAt"), r.get("fallen"), r.get("losses"), r.get("heroesSeen"))
                if self.reports.get(key) != sig:
                    near = [b for b in d["bases"] if b["faction"] == r["subject"] and cheb(xyz(b["center"]), xyz(r["pos"])) < 32]
                    g = truth.get(near[0]["id"]) if near else None
                    self.wl.write(f"{day:8.3f} {fn:15s} REPORT {r['subject']} at {r['pos']}: {look_text(r, t)}"
                                  + (f" | truth now {g['soldiers']} soldiers, heroes {g['heroes']}" if g else "") + "\n")
                    self.reports[key] = sig
        self.wj.write(json.dumps(rec) + "\n")
        self.wl.flush()
        self.wj.flush()
        self.tail(day)

    def tail(self, day):
        """Faction lines and world announcements from the server log, stamped with this poll's game day."""
        p = os.path.join(SV, "logs", "latest.log")
        try:
            size = os.path.getsize(p)
            if size < self.log_pos:
                self.log_pos = 0  # the log rotated (a restart)
            with open(p, "r", errors="replace") as f:
                f.seek(self.log_pos)
                data = f.read()
                self.log_pos = f.tell()
        except OSError:
            return
        for ln in data.splitlines():
            if "[com.factionclash.FactionClash/]" in ln and ("]: [" in ln):
                self.chat.write(f"{day:8.3f} {ln.split(']: ', 1)[1]}\n")
        self.chat.flush()


def main():
    ev = open(os.path.join(OUT, "events.log"), "a")
    tl = open(os.path.join(OUT, "timeline.jsonl"), "a")
    war = War()
    prev_roster, prev_inside, prev_news, prev_heroes, trip_max = {}, {}, {}, {}, {}
    while not os.path.exists(os.path.join(OUT, "STOP")):
        try:
            rcon.run(["fc admin dump"], timeout=60)
            with open(os.path.join(SV, "world", "factionclash_dump.json")) as f:
                d = json.load(f)
        except Exception as e:  # noqa: BLE001
            ev.write(f"# poll failed: {e}\n")
            ev.flush()
            time.sleep(IV)
            continue
        t = d["gameTime"]
        day = t / 24000.0
        homes = {}
        for b in d["bases"]:
            if b["kind"] == "SETTLEMENT" and not b["destroyed"]:
                homes.setdefault(b["faction"], []).append(xyz(b["center"]))
        snap = {"t": t, "roster": {}, "npcs": {}}
        for fac in d["factions"]:
            fn = fac["faction"]
            for r in fac["roster"]:
                key = r["uuid"]
                st = (r["where"], r["alive"], r["dead"], r["vanished"], r["witnessed"], r["expectedBy"], r["since"])
                snap["roster"][key] = [fn, r["name"], r["role"], *st, r["pos"]]
                old = prev_roster.get(key)
                if old is None:
                    ev.write(f"{day:8.3f} t={t} {fn:15s} NEW  {r['name']} ({r['role']}) where={r['where']} pos={r['pos']}\n")
                elif old != st:
                    diffs = []
                    names = ["where", "alive", "dead", "vanished", "witnessed", "expectedBy", "since"]
                    for i, n in enumerate(names):
                        if old[i] != st[i]:
                            o, nw = old[i], st[i]
                            if n in ("expectedBy", "since"):
                                o, nw = f"{o / 24000:.3f}d", f"{nw / 24000:.3f}d"
                            diffs.append(f"{n}:{o}->{nw}")
                    ev.write(f"{day:8.3f} t={t} {fn:15s} ROLL {r['name']} ({r['role']}) {' '.join(diffs)} pos={r['pos']}\n")
                prev_roster[key] = st
            news = {(n["carrier"], n["text"]) for n in fac.get("news", [])}
            pn = prev_news.get(fn, set())
            for n in news - pn:
                ev.write(f"{day:8.3f} t={t} {fn:15s} NEWS+ carrier={n[0][:8]} {n[1]}\n")
            for n in pn - news:
                ev.write(f"{day:8.3f} t={t} {fn:15s} NEWS- carrier={n[0][:8]} {n[1]}\n")
            prev_news[fn] = news
        for n in d["npcs"]:
            fn = n["faction"]
            p = xyz(n["pos"])
            hs = homes.get(fn, [])
            dist = min((cheb(p, h) for h in hs), default=9999)
            inside = dist <= HOME
            key = n["uuid"]
            snap["npcs"][key] = [fn, n["name"], n["role"], n["pos"], dist, n["status"][:60], round(n["health"], 1)]
            if n["role"] in COMBAT or n.get("hero"):
                # a fighter's target and pursuit mode (warcheck.py's "far targets": an archer waiting on one far off)
                snap["npcs"][key] += [n.get("target", ""), (n.get("pursuit") or "").split(" ")[0]]
            was = prev_inside.get(key)
            if was is not None and was != inside:
                tag = "EXIT" if not inside else "ENTER"
                extra = f" maxdist={trip_max.get(key, dist)}" if inside else ""
                ev.write(f"{day:8.3f} t={t} {fn:15s} {tag} {n['name']} ({n['role']}) dist={dist} pos={n['pos']} status={n['status'][:50]}{extra}\n")
                trip_max[key] = dist
            if not inside:
                trip_max[key] = max(trip_max.get(key, 0), dist)
            prev_inside[key] = inside
        for h in d.get("heroes", []):
            hs = (h["alive"], h.get("returnsIn"), h["deaths"], h["revivals"], h["kills"])
            if prev_heroes.get(h["id"]) != hs:
                ev.write(f"{day:8.3f} t={t} HERO {h['id']} owner={h.get('owner')} alive={h['alive']} returnsIn={h.get('returnsIn')} "
                         f"deaths={h['deaths']} revivals={h['revivals']} kills={h['kills']}\n")
                prev_heroes[h["id"]] = hs
        tl.write(json.dumps(snap) + "\n")
        war.poll(d)
        ev.flush()
        tl.flush()
        time.sleep(IV)


if __name__ == "__main__":
    main()
