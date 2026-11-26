#!/usr/bin/env python3
"""Roll call tracker for a live campaign (docs/design/missing_npcs.md, docs/HANDOFF.md section 4).

Polls `fc admin dump` and appends to <out_dir>/events.log every change in each faction's view of its members (where,
alive, expected return, since) beside the truth (dead, vanished, witnessed), every member crossing its home area's edge
(truth, with the farthest point of the trip), news carried and dropped, and hero deaths and revivals; one JSON sample per
poll goes to <out_dir>/timeline.jsonl.

The war (docs/design/knowledge.md, docs/design/recon.md 10.4): each poll also appends to <out_dir>/war.jsonl every
faction's council line, raid counters and reports of enemy bases (the focused recon each holds) beside the truth (each
settlement's soldiers in its walls, each faction's raid under way), and <out_dir>/war.log gets each change of a council's
line, each launch with the plan and the recon it rested on and the truth, each raid's end and each report delivered or
changed. Faction lines from the server log go to <out_dir>/chat.log stamped with the game day of the poll that read them.

Focused reconnaissance (recon.md 10.4, task R6): <out_dir>/recon.jsonl is the record tools/warcheck.py reads. Each poll
writes one "poll" record (every faction's recon orders, strikes, kept recons, sightings and last plan; every scout's recon
trip; the truth of each base: its soldiers in its walls box and those of its own away from it, its heroes home and away);
each FC_RECON line of the server log (an order's end, a side given up, a watch's end with the truth at its last sample)
is copied as a "log" record; each launch writes a "launch" record with the plan the council launched on and the truth then.
<out_dir>/recon.log is the same for a reader: orders placed, taken, reported and ended, each scout's trip by phase, sides
given up, watches with the truth, recons delivered, launches with their plan. Nothing is judged here: warcheck.py does it,
so a fix there needs no new run.

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
    """What a kept report rests on (recon.md 4): ruins, its focused recon (good, by scout and day), or none yet."""
    if r is None:
        return "no report"
    if r.get("fallen"):
        return f"ruins (by {r.get('by')})"
    c = r.get("recon")
    fails = f", {r.get('reconFailures')} failed since" if r.get("reconFailures") else ""
    if not c:
        return f"no recon (position by {r.get('by')}{fails})"
    age = (t - c["at"]) / 24000.0
    heroes = ",".join(c.get("heroes", {}))
    return (f"recon {c['soldiers']} soldiers, {int(round(c['coverage'] * 100))}% of yard, middle {c['middle']}, power {c['power']}, "
            f"day {c['at'] / 24000.0:.2f} ({age:.2f}d old, {'usable' if c.get('usable') else 'not usable'}), {c.get('vantage')}"
            f"{' with ' + c['optic'] if c.get('optic') else ''}, heroes [{heroes}], by {c.get('by')}{fails}")


def box_half(b):
    """A base's walls box for the truth: a settlement's walls (24) plus 8; a lair's monsters' ground (10) plus 8."""
    return WALLS if b["kind"] == "SETTLEMENT" else 18


def base_truth(d):
    """The truth of each standing base: its soldiers (heroes apart) in its walls box (any faction member of its own
    faction, as garrisons() counts them) and its own soldiers away from it (by the roster's base and the truth's dead flag,
    at the loaded entity's place or else the roster's last), its heroes home and away; a lair's recorded monsters."""
    pos = {n["uuid"]: n["pos"] for n in d["npcs"]}
    out = {}
    for b in d["bases"]:
        if b["destroyed"]:
            continue
        c = xyz(b["center"])
        half = box_half(b)
        if b["kind"] != "SETTLEMENT":
            out[b["id"]] = {"kind": "lair", "monsters": b.get("lairMobs", 0)}
            continue
        home = sum(1 for n in d["npcs"] if n["faction"] == b["faction"] and n["role"] in COMBAT and not n.get("hero")
                   and cheb(xyz(n["pos"]), c) <= half)
        away = []
        for fac in d["factions"]:
            if fac["faction"] != b["faction"]:
                continue
            for r in fac["roster"]:
                if r.get("base") != b["id"] or r["role"] not in COMBAT or r.get("dead") or r.get("vanished"):
                    continue
                p = xyz(pos.get(r["uuid"], r["pos"]))
                if cheb(p, c) > half:
                    away.append(r["uuid"])
        h_home, h_away = [], []
        for h in d.get("heroes", []):
            if h.get("owner") != b["faction"] or h.get("base") != b["id"]:
                continue
            if h.get("alive") and h.get("pos") and cheb(xyz(h["pos"]), c) <= half:
                h_home.append(h["id"])
            else:
                h_away.append(h["id"])
        out[b["id"]] = {"kind": "settlement", "home": home, "away": len(away), "awayIds": away, "heroesHome": h_home,
                        "heroesAway": h_away}
    return out


class War:
    """The war record: council lines, launches with the plan and the recon used beside the truth, raids' ends, reports,
    and the focused reconnaissance (recon.jsonl, recon.log)."""

    def __init__(self):
        self.wl = open(os.path.join(OUT, "war.log"), "a")
        self.wj = open(os.path.join(OUT, "war.jsonl"), "a")
        self.chat = open(os.path.join(OUT, "chat.log"), "a")
        self.rj = open(os.path.join(OUT, "recon.jsonl"), "a")
        self.rl = open(os.path.join(OUT, "recon.log"), "a")
        self.council, self.launched, self.target, self.reports, self.left = {}, {}, {}, {}, {}
        self.orders, self.trips = {}, {}
        self.log_pos = 0
        if os.path.getsize(os.path.join(OUT, "chat.log")) > 0:
            # a restarted tracker on a folder that already has the record goes on from the server log's end, so it neither
            # repeats the lines it read nor stamps them with today's day
            try:
                self.log_pos = os.path.getsize(os.path.join(SV, "logs", "latest.log"))
            except OSError:
                pass

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
                plan = fac.get("plan") or {}
                bt = base_truth(d).get(tgt, {})
                if plan.get("base") != tgt:
                    plan = {}  # an operator's raid with no scout's report of it (or a plan of another base)
                self.wl.write(f"{day:8.3f} {fn:15s} LAUNCH #{n} on base {tgt} ({b['faction']} {b['kind'].lower()}): {line}\n"
                              f"{'':25s} report used: {look_text(report_of(fac, b), t)}\n"
                              f"{'':25s} truth now: " + (f"{g['soldiers']} soldiers in its walls, heroes {g['heroes']}" if g else
                                                         "ruins, its heart gone" if b.get("destroyed") else f"lair mobs {b['lairMobs']}")
                              + (f"; its own raid party out since day {left / 24000.0:.2f}" if away and left else "") + "\n")
                if plan:
                    self.wl.write(f"{'':25s} plan: {plan.get('counted')} counted + reserve {plan.get('reserve')} (share "
                                  f"{plan.get('shareSoldiers')}, seen away {len(plan.get('seenAway', []))}), heroes "
                                  f"{list(plan.get('heroes', {}))}; truth: {bt.get('away')} of its soldiers away, heroes away "
                                  f"{bt.get('heroesAway')}\n")
                lrec = {"kind": "launch", "t": t, "launcher": fn, "n": n, "base": tgt, "subject": b["faction"],
                        "baseKind": b["kind"], "council": line, "plan": plan or None, "truth": g, "baseTruth": bt,
                        "lairMobs": b.get("lairMobs"), "partyOut": bool(away and left)}
                self.rj.write(json.dumps(lrec) + "\n")
                self.rl.write(f"{day:8.3f} {fn:15s} LAUNCH on base {tgt} ({b['faction']}): plan {plan.get('counted') if plan else '-'}"
                              f" + {plan.get('reserve') if plan else '-'}; truth home {bt.get('home', bt.get('monsters'))}"
                              f" away {bt.get('away')} heroes away {bt.get('heroesAway')}\n")
            if fn in self.target and self.target[fn] >= 0 and tgt < 0:
                self.wl.write(f"{day:8.3f} {fn:15s} RAID OVER (was base {self.target[fn]})\n")
            if tgt >= 0 and self.target.get(fn, -1) < 0:
                self.left[fn] = t
            self.launched[fn] = n
            self.target[fn] = tgt
            for r in fac.get("enemyBasesKnown", []):
                key = (fn, r["subject"], r["pos"])
                c = r.get("recon") or {}
                sig = (c.get("at"), r.get("fallen"), r.get("reconFailures"), r.get("reconSidesTried"))
                if self.reports.get(key) != sig:
                    near = [b for b in d["bases"] if b["faction"] == r["subject"] and cheb(xyz(b["center"]), xyz(r["pos"])) < 32]
                    g = truth.get(near[0]["id"]) if near else None
                    self.wl.write(f"{day:8.3f} {fn:15s} REPORT {r['subject']} at {r['pos']}: {look_text(r, t)}"
                                  + (f" | truth now {g['soldiers']} soldiers, heroes {g['heroes']}" if g else "") + "\n")
                    self.reports[key] = sig
        self.wj.write(json.dumps(rec) + "\n")
        self.wl.flush()
        self.wj.flush()
        self.recon(d, t, day)
        self.tail(day)

    def recon(self, d, t, day):
        """One "poll" record of focused reconnaissance, and recon.log's changes: orders placed, taken, reported; each
        scout's trip by phase, step and side; recons delivered (a kept recon newer than before)."""
        bt = base_truth(d)
        rec = {"kind": "poll", "t": t, "factions": [], "scouts": [], "truth": bt}
        for fac in d["factions"]:
            fn = fac["faction"]
            rec["factions"].append({k: fac.get(k) for k in ("faction", "alive", "reconOrders", "struckBy", "reconUrgentAt", "plan",
                                                             "raidsLaunched", "raidTarget", "lastRaidAt", "council", "enemyBasesKnown",
                                                             "sightings", "raidReconHeroes")})
            now = {}
            for o in fac.get("reconOrders") or []:
                key = (fn, o["target"], o["orderedAt"])
                now[key] = o
                old = self.orders.get(key)
                what = f"{'urgent ' if o['urgent'] else ''}recon of {o['subject']} {o['kind'].lower()} at {o['target']}"
                if old is None:
                    self.rl.write(f"{day:8.3f} {fn:15s} ORDER placed: {what} (day {o['orderedAt'] / 24000:.2f})\n")
                if o["takenAt"] and (old is None or not old["takenAt"]):
                    self.rl.write(f"{day:8.3f} {fn:15s} ORDER taken by {o['scoutName']}: {what}, after "
                                  f"{(o['takenAt'] - o['orderedAt']) / 20:.0f} s\n")
                if o["reported"] != "NONE" and (old is None or old["reported"] == "NONE"):
                    self.rl.write(f"{day:8.3f} {fn:15s} ORDER reported {o['reported']} by {o['scoutName']}: {what}\n")
            for key in [k for k in self.orders if k[0] == fn and k not in now]:
                self.rl.write(f"{day:8.3f} {fn:15s} ORDER gone: recon at {key[1]} (its end: the FC_RECON order_end line)\n")
                del self.orders[key]
            self.orders.update(now)
        seen = set()
        for n in d["npcs"]:
            r = n.get("recon")
            if not r:
                continue
            seen.add(n["uuid"])
            rec["scouts"].append({"uuid": n["uuid"], "name": n["name"], "faction": n["faction"], "pos": n["pos"],
                                  "status": n["status"], "recon": r})
            sig = (r["phase"], r["step"], r["side"], r.get("holding"))
            if self.trips.get(n["uuid"]) != sig:
                self.rl.write(f"{day:8.3f} {n['faction']:15s} TRIP {n['name']}: {r['phase']}/{r['step']} side {r['side'] or '-'} "
                              f"of {r['subject']} at {r['target']} height {r['height']} {r['vantage']}"
                              f"{' with ' + r['optic'] if r['optic'] else ''}{' HOLDING' if r.get('holding') else ''}"
                              + (f" watch {r.get('counted')} counted, {r.get('points')} points, middle {r.get('middle')}"
                                 if 'counted' in r else "") + "\n")
                self.trips[n["uuid"]] = sig
        for u in [u for u in self.trips if u not in seen]:
            del self.trips[u]  # its trip ended, or it is no longer loaded
        self.rj.write(json.dumps(rec) + "\n")
        self.rj.flush()
        self.rl.flush()

    def tail(self, day):
        """Faction lines and world announcements from the server log, stamped with this poll's game day; the FC_RECON
        lines (recon.md 10.4) go to recon.jsonl as "log" records and to recon.log."""
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
            if "FC_RECON {" in ln:
                try:
                    j = json.loads(ln.split("FC_RECON ", 1)[1])
                except ValueError:
                    continue
                # (the event's own "kind" is the target's, ENEMY_BASE or LAIR: kept as "baseKind")
                j = {"kind": "log", **{("baseKind" if k == "kind" else k): v for k, v in j.items()}}
                self.rj.write(json.dumps(j) + "\n")
                jd = j.get("t", 0) / 24000.0
                if j.get("ev") == "watch_end":
                    tr = j.get("truth") or {}
                    self.rl.write(f"{jd:8.3f} {j['faction']:15s} WATCH END {j['scoutName']}: {j['subject']} {j['baseKind'].lower()} "
                                  f"side {j['side']} height {j['height']} {j['vantage']}{' with ' + j['optic'] if j['optic'] else ''}: "
                                  f"{j.get('counted')} counted, {j.get('points')} points, middle {j.get('middle')}, "
                                  f"{'good' if j.get('good') else 'BLIND'}, why {j.get('why')}, holds {j.get('holds')} "
                                  f"({j.get('heldTicks')} ticks) | truth {tr.get('soldiers', tr.get('monsters'))} heroes {tr.get('heroes')}"
                                  + (f" | attacker {j['attacker']}" if j.get("attacker") else "") + "\n")
                elif j.get("ev") == "side_over":
                    self.rl.write(f"{jd:8.3f} {j['faction']:15s} SIDE OVER {j['scoutName']}: {j['side']} of {j['subject']}: "
                                  f"{j.get('why')} (columns {j.get('columns')}, best {j.get('bestPoints')} points at "
                                  f"{j.get('bestHeight')})\n")
                elif j.get("ev") == "order_end":
                    self.rl.write(f"{jd:8.3f} {j['faction']:15s} ORDER {j.get('ending')}: recon of {j.get('subject')} at "
                                  f"{j.get('target')} (scout {j.get('scoutName') or '-'}, reported {j.get('reported')})\n")
                continue
            if "[com.factionclash.FactionClash/]" in ln and ("]: [" in ln):
                self.chat.write(f"{day:8.3f} {ln.split(']: ', 1)[1]}\n")
        self.chat.flush()
        self.rj.flush()
        self.rl.flush()


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
