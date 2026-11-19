#!/usr/bin/env python3
"""Roll call tracker for a live campaign (docs/design/missing_npcs.md, docs/HANDOFF.md section 4).

Polls `fc admin dump` and appends to <out_dir>/events.log every change in each faction's view of its members (where,
alive, expected return, since) beside the truth (dead, vanished, witnessed), every member crossing its home area's edge
(truth, with the farthest point of the trip), news carried and dropped, and hero deaths and revivals; one JSON sample per
poll goes to <out_dir>/timeline.jsonl.

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


def main():
    ev = open(os.path.join(OUT, "events.log"), "a")
    tl = open(os.path.join(OUT, "timeline.jsonl"), "a")
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
                ev.write(f"{day:8.3f} t={t} HERO {h['id']} owner={h['owner']} alive={h['alive']} returnsIn={h.get('returnsIn')} "
                         f"deaths={h['deaths']} revivals={h['revivals']} kills={h['kills']}\n")
                prev_heroes[h["id"]] = hs
        tl.write(json.dumps(snap) + "\n")
        ev.flush()
        tl.flush()
        time.sleep(IV)


if __name__ == "__main__":
    main()
