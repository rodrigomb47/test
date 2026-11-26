#!/usr/bin/env python3
"""Reads a tools/rolltrack.py output folder and checks the war and the roll call against the truth.

  python3 tools/warcheck.py <track_dir> [from_tick] [to_tick]

Sections (docs/design/knowledge.md 12, the knowledge review's live check; docs/design/recon.md 10.4, focused recon):
  launches  each raid launched: what it rested on (basis "recon N% day D": the focused recon of its plan; "no recon"
            for an operator's raid with no scout's report of it, labelled "by order"; anything else is a FAILURE), its
            "about N" (the count) and reserve beside the soldiers truly in the target's walls, the party, the raiders who
            set out and how many of them died
  leak      each departure of 3 or more soldiers from a garrison, with every other council's estimate of that base at
            the poll before and the next five: an estimate that moves at a departure without a recon of its own is a leak
  (recon.jsonl, written by rolltrack.py since task R6:)
  orders    recon orders placed, taken (the wait from order to taking), reported and ended (done, blind, empty-handed,
            lost, reopened, dropped); scouts lost on recons
  looks     every focused look (a watch's end): its side, pillar height or vantage, instrument, the yard's coverage, good
            or blind, the faces counted beside the truth in the box at the watch's last sample, why it ended, the holds;
            the coverage histogram and the share good; the recon against the truth (mean and worst miss, settlements and
            lairs apart; good recons above the truth); interrupted watches by cause; heroes seen against the heroes home
  sides     the sides given up and why (no vantage, could not get there, no column reached, no good look), and the
            targets never given a good recon on any side
  attacked  recon scouts hurt on the pillar and by whom (a guard of the target's yard must be 0), pillar blocks left
  plan      at each launch: the reserve and what it rested on (the share, the soldiers seen away, the heroes known) beside
            the target's soldiers truly away from its walls and its heroes away; the plans whose seen-away term was the
            larger; the sightings by source (home, field, watch), and how many of those in a launch's plan were dead or
            back home then; the waits on the reserve alone
  gaps      each faction's longest time without a launch and why (waiting for a scout's report and its reason, waiting
            on strength, its own raid out)
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


recon = jsonl("recon.jsonl")
logs = [r for r in recon if r.get("kind") == "log"]
polls = [r for r in recon if r.get("kind") == "poll"]
launch_recs = [r for r in recon if r.get("kind") == "launch"]
LAUNCH = re.compile(r"(Counter-attack! |By order! )?Launching a raid with (\d+) soldiers? on (.+?) reported at ([-\d, ]+?)"
                    r"(?:, with no scout's report of it|, against (.*))$")


def base_kind(r):
    """A log record's target kind (ENEMY_BASE or LAIR): "baseKind"; a record written before that key took "kind" for
    the record type, so the Hollow's (the only lair faction) is a lair then."""
    k = r.get("baseKind") or r.get("kind")
    return k if k in ("ENEMY_BASE", "LAIR") else ("LAIR" if r.get("subject") == "hollow" else "ENEMY_BASE")


def launch_basis(plan):
    """What a launch rested on (recon.md 10.4): "recon N% day D" (its plan's focused recon), else "no recon"."""
    if not plan:
        return "no recon"
    c = plan.get("recon") or {}
    return f"recon {int(round(c.get('coverage', 0) * 100))}% day {c.get('at', 0) / 24000:.2f}"


def plan_at(fac_name, t, base):
    """The plan a launch at tick t rested on: the launch record's (rolltrack.py since task R6), else none."""
    for r in launch_recs:
        if r["launcher"] == fac_name and abs(r["t"] - t) <= 24000 * 0.003 + 400 and r["base"] == base:
            return r
    return None


print("\n=== launches ===")
print(f"{'day':>7s} {'launcher':15s} {'target':15s} {'kind':10s} party about rsrv basis                 truth           out dead")
failures = 0
for i, ln in enumerate(wl):
    m = re.match(r"\s*([\d.]+) (\S+)\s+LAUNCH #(\d+) on base (\d+) \((\S+) (\w+)\)", ln)
    if not m or not in_range(float(m.group(1))):
        continue
    d, fac, subject, kind = float(m.group(1)), m.group(2), m.group(5), m.group(6)
    tru = wl[i + 2].split("truth now: ", 1)[1]
    said = [c for c in chat if c.split()[0].replace(".", "", 1).isdigit() and abs(float(c.split()[0]) - d) < 0.002
            and f"[{NAMES[fac]}]" in c and "Launching a raid" in c]
    lm = LAUNCH.search(said[0].split("] ", 1)[-1]) if said else None
    lr = plan_at(fac, int(d * 24000), int(m.group(4)))
    plan = (lr or {}).get("plan")
    by_order = bool(lm and lm.group(1) == "By order! ")
    basis = launch_basis(plan)
    if not plan and not by_order:
        basis += " FAILURE"
        failures += 1
    about = str(plan["counted"]) if plan else "-"
    reserve = str(plan["reserve"]) if plan else "-"
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
    label = " counter" if lm and lm.group(1) == "Counter-attack! " else " by order" if by_order else ""
    print(f"{d:7.3f} {fac:15s} {subject:15s} {kind:10s} {lm.group(2) if lm else '?':>5s} {about:>5s} {reserve:>4s} "
          f"{basis:21s} {truth:15s} {len(out):3d} {len(dead):4d}{label}")
print(f"  {failures} launches with no focused recon (an operator's raid excepted)")

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
                rs = "; ".join((f"recon day {x['recon']['at'] / 24000:.2f} {int(round(100 * x['recon']['coverage']))}% "
                                f"{x['recon']['soldiers']} counted" if x.get("recon") else "no recon")
                               + f", {x.get('reconFailures', 0)} failed" for x in reps)
                print(f"     {'before' if j < k else '+' + str(j - k):>6s} {day(war[j]['t']):.3f} {f['faction']:15s} "
                      f"estimate {m.group(1) if m else '?':>5s} | {rs}")

# ---------------------------------------------------------------- focused reconnaissance (recon.md 10.4, task R6)
in_t = lambda t: T0 <= t <= T1  # noqa: E731
ends = [r for r in logs if r.get("ev") == "order_end" and in_t(r["t"])]
watches = [r for r in logs if r.get("ev") == "watch_end" and in_t(r["t"])]
sides_over = [r for r in logs if r.get("ev") == "side_over" and in_t(r["t"])]
if not recon:
    print("\n(no recon.jsonl: a rolltrack.py run from before task R6; the recon sections are not measured)")

print("\n=== orders: recon orders placed, taken and ended ===")
placed, taken = {}, {}
for pr in polls:
    if not in_t(pr["t"]):
        continue
    for f in pr["factions"]:
        for o in f.get("reconOrders") or []:
            key = (f["faction"], o["target"], o["orderedAt"])
            placed.setdefault(key, o)
            if o["takenAt"]:
                taken.setdefault(key, o)
waits = sorted((o["takenAt"] - o["orderedAt"]) / 20 for o in taken.values())
by_end = {}
for e in ends:
    by_end[e["ending"]] = by_end.get(e["ending"], 0) + 1
print(f"  placed {len(placed)} ({sum(1 for o in placed.values() if o['urgent'])} urgent), taken {len(taken)}; "
      f"ended: {', '.join(f'{k.lower()} {v}' for k, v in sorted(by_end.items())) or 'none'}")
if waits:
    print(f"  wait from order to taking: median {waits[len(waits) // 2]:.0f} s, longest {waits[-1]:.0f} s")
for e in ends:
    if e["ending"] == "LOST":
        print(f"  {e['t'] / 24000:7.3f} {e['faction']:15s} scout lost on a recon: {e.get('scoutName') or '?'} (of {e['subject']} at {e['target']})")
print(f"  {by_end.get('LOST', 0)} scouts lost on recons")

print("\n=== looks: every focused look (a watch's end) beside the truth in the box at its last sample ===")
print(f"{'day':>7s} {'scout':15s} {'faction':15s} {'target':15s} side  height vantage        optic     cov good counted truth miss why")
hist = [0] * 11
good_n = blind_n = 0
miss = {"settlement": [], "lair": []}
above = 0
causes = {}
hero_rows = []
for w in watches:
    tr = w.get("truth") or {}
    lair = base_kind(w) == "LAIR"
    true_n = tr.get("monsters", 0) if lair else tr.get("soldiers")
    cov = (w.get("points") or 0) / 25
    hist[min(10, int(cov * 10))] += 1
    good = bool(w.get("good"))
    good_n += 1 if good else 0
    blind_n += 0 if good else 1
    d_miss = None
    if good and true_n is not None:
        d_miss = true_n - w.get("counted", 0)
        miss["lair" if lair else "settlement"].append(d_miss)
        above += 1 if d_miss < 0 else 0
    causes[w.get("why")] = causes.get(w.get("why"), 0) + 1
    if not lair:
        hero_rows.append((w, sorted(w.get("heroes") or []), sorted(tr.get("heroes") or [])))
    print(f"{w['t'] / 24000:7.3f} {w['scoutName'][:15]:15s} {w['faction']:15s} {w['subject']:15s} {w['side'] or '-':5s} {w['height']:6d} "
          f"{w['vantage'][:14]:14s} {w['optic'] or 'eyes':9s} {int(round(cov * 100)):3d}% {'good' if good else 'BLIND':5s} "
          f"{w.get('counted', 0):7d} {str(true_n):>5s} {'' if d_miss is None else d_miss:>4} {w.get('why')}"
          + (f" (holds {w['holds']}, {w['heldTicks']} ticks)" if w.get("holds") else ""))
n_looks = good_n + blind_n
print(f"  {n_looks} looks: {good_n} good ({100 * good_n / n_looks:.0f}%), {blind_n} blind" if n_looks else "  no focused look ended")
print("  coverage histogram (tenths of the yard): " + " ".join(f"{i * 10}%:{c}" for i, c in enumerate(hist) if c))
for k, v in miss.items():
    if v:
        print(f"  recon against the truth ({k}s): mean miss {sum(v) / len(v):.2f} soldiers, worst {max(v, key=abs)}, over {len(v)} good recons")
print(f"  {above} good recons above the truth (counted more than the box held at the last sample)")
print("  watches by how they ended: " + (", ".join(f"{k} {v}" for k, v in sorted(causes.items())) or "none"))
for w, seen_h, home_h in hero_rows:
    if seen_h or home_h:
        print(f"  heroes {w['t'] / 24000:7.3f} {w['subject']}: seen {seen_h}, in the box at the end {home_h}")

print("\n=== sides: sides given up and targets never given a good recon ===")
why_n = {}
for so in sides_over:
    why_n[so["why"]] = why_n.get(so["why"], 0) + 1
    print(f"  {so['t'] / 24000:7.3f} {so['faction']:15s} {so['scoutName'][:15]:15s} {so['subject']} {so['side']}: {so['why']}"
          f" (columns {so.get('columns')}, best look {so.get('bestPoints')} points, middle {so.get('bestMiddle')}, at {so.get('bestHeight')})")
print("  sides given up: " + (", ".join(f"{k} {v}" for k, v in sorted(why_n.items())) or "none"))
targets = {}
for e in ends:
    targets.setdefault((e["faction"], e["subject"], e["target"]), []).append(e["ending"])
never = [(k, v) for k, v in targets.items() if "DONE" not in v and any(x in ("BLIND", "EMPTY_HANDED") for x in v)]
for k, v in never:
    print(f"  never good: {k[0]} on {k[1]} at {k[2]}: {v}")
calls = sum(1 for v in targets.values() if v and all(x in ("BLIND", "EMPTY_HANDED") for x in v))
print(f"  {len(never)} targets with a failed recon and none good; orders blind on every side tried: {calls} of {len(targets)}"
      + (" (more than one in five: recon.md 10.4 calls for a look at the blind bands)" if targets and calls * 5 > len(targets) else ""))

print("\n=== attacked: recon scouts hurt on the pillar, and pillar blocks left ===")
guards = 0
for w in watches:
    a = w.get("attacker")
    if a is None:
        continue
    tr = w.get("truth") or {}
    who = ("player " + a["player"]) if a.get("player") else \
        ("hero " + a.get("faction", "?")) if a.get("hero") else \
        ("guard of the yard" if a.get("faction") == w["subject"] and a.get("base") == tr.get("base") else
         f"soldier of {a.get('faction')} (base {a.get('base')})") if a.get("faction") else \
        (a.get("type") or a.get("damage") or "unknown")
    guards += 1 if who == "guard of the yard" else 0
    print(f"  {w['t'] / 24000:7.3f} {w['scoutName'][:15]:15s} at {w['subject']} side {w['side']} height {w['height']}: {who} "
          f"({a.get('damage')}, {a.get('distance', '?')} blocks)")
print(f"  {guards} attacks by a guard of the watched yard (the standoff's promise: 0)")
left = [w for w in watches if w.get("why") == "off its pillar" and w.get("height")]
dead_on = []
last_trip = {}
for pr in polls:
    for sc in pr["scouts"]:
        last_trip[sc["uuid"]] = (pr["t"], sc)
dead_ids = set()
for e in ev:
    m = re.match(r"\s*([\d.]+) t=(\d+) (\S+)\s+ROLL (.+?) \((\w+)\) .*dead:False->True", e)
    if m and m.group(5) == "scout":
        dead_ids.add(m.group(4))
for u, (pt, sc) in last_trip.items():
    r = sc["recon"]
    if sc["name"] in dead_ids and r["phase"] == "PILLAR" and r["height"] > 0:
        dead_on.append((pt, sc["name"], r["height"]))
print(f"  pillar blocks left: {sum(w['height'] for w in left)} by {len(left)} scouts moved off their column; "
      f"{sum(h for _, _, h in dead_on)} by {len(dead_on)} scouts that died on their pillar (last polled height)")
holds = [w for w in watches if w.get("holds")]
print(f"  holds on the pillar after the watch: {sum(w['holds'] for w in holds)} in {len(holds)} watches, "
      f"{sum(w['heldTicks'] for w in holds) / 20:.0f} s in all, longest {max([w['heldTicks'] for w in holds] or [0]) / 20:.0f} s")

print("\n=== plan: the reserve at each launch beside the truth; sightings by source ===")
rost = {}
for pr in tl:
    for u, v in pr["roster"].items():
        rost[u] = v
seen_larger = 0
for lr in launch_recs:
    if not in_t(lr["t"]):
        continue
    p = lr.get("plan")
    bt = lr.get("baseTruth") or {}
    if not p:
        print(f"  {lr['t'] / 24000:7.3f} {lr['launcher']:15s} on base {lr['base']}: no plan (an operator's raid with no recon)")
        continue
    seen_larger += 1 if p.get("fromSightings") else 0
    away = p.get("seenAway") or []
    snap = min(tl, key=lambda q: abs(q["t"] - lr["t"]))["roster"] if tl else {}
    dead = sum(1 for x in away if snap.get(x["id"], [None] * 6)[5] is True)
    home = sum(1 for x in away if x["id"] not in (bt.get("awayIds") or []) and snap.get(x["id"], [None] * 6)[5] is False)
    src = {}
    for x in away:
        src[x["source"]] = src.get(x["source"], 0) + 1
    print(f"  {lr['t'] / 24000:7.3f} {lr['launcher']:15s} on base {lr['base']} ({lr['subject']}): counted {p['counted']}, share "
          f"{p['shareSoldiers']}, seen away {len(away)} {src or ''} -> reserve {p['reserve']}"
          f"{' (from sightings)' if p.get('fromSightings') else ''}; heroes {sorted(p.get('heroes', {}))} "
          f"(known only from sightings {[x.get('hero') for x in p.get('knownHeroes', [])]}) | truth: home {bt.get('home', bt.get('monsters'))}, "
          f"away {bt.get('away')}, heroes home {bt.get('heroesHome')}, away {bt.get('heroesAway')}; of the seen away "
          f"{dead} dead and {home} back home at the launch")
print(f"  {seen_larger} launches whose seen-away term was the larger")
by_src, fallen_n = {}, 0
uniq = {}
for pr in polls:
    if not in_t(pr["t"]):
        continue
    for f in pr["factions"]:
        for x in f.get("sightings") or []:
            uniq[(f["faction"], x["id"], x["source"])] = x
for (fn, _, srcn), x in uniq.items():
    by_src[srcn] = by_src.get(srcn, 0) + 1
    fallen_n += 1 if x.get("fallen") else 0
print("  sightings recorded by source: " + (", ".join(f"{k} {v}" for k, v in sorted(by_src.items())) or "none")
      + f" ({fallen_n} marked fallen)")
waits_reserve = 0
last_wait = {}
for pr in polls:
    if not in_t(pr["t"]):
        continue
    for f in pr["factions"]:
        p = f.get("plan")
        key = (p.get("base"), (p.get("recon") or {}).get("at")) if p else None
        if not p or p.get("verdict") != "WAIT" or key == last_wait.get(f["faction"]):
            continue
        last_wait[f["faction"]] = key  # once per target and recon (the council judges it again every pass)
        # two thirds of the count alone, head for head (the gear left out: an estimate of "the reserve held it")
        if p["party"] >= int(p["counted"] * 2 / 3 + 1e-9) and p["needed"] > p["party"]:
            waits_reserve += 1
print(f"  waits on the reserve alone, once per target and recon (the count's two thirds within the party, the plan's "
      f"not; gear left out): {waits_reserve}")

print("\n=== gaps: each faction's longest time without a launch, and why ===")
if war:
    t_first, t_last = war[0]["t"], war[-1]["t"]
    fac_names = sorted({f["faction"] for f in war[0]["factions"]} - {"hollow"})  # (the Hollow holds no council)
    for fn in fac_names:
        times = [t_first]
        for lr in wl:
            m = re.match(r"\s*([\d.]+) (\S+)\s+LAUNCH", lr)
            if m and m.group(2) == fn and in_range(float(m.group(1))):
                times.append(int(float(m.group(1)) * 24000))
        times.append(t_last)
        gap = max(((times[i + 1] - times[i], times[i], times[i + 1]) for i in range(len(times) - 1)), default=(0, 0, 0))
        why = {}
        for rec in war:
            if not gap[1] <= rec["t"] <= gap[2]:
                continue
            for f in rec["factions"]:
                if f["faction"] != fn:
                    continue
                c = f.get("council") or ""
                if f.get("raidTarget", -1) >= 0:
                    k = "its own raid out"
                elif "waiting for a scout's report" in c:
                    m = re.search(r"waiting for a scout's report on .*?\((\w+): (.+?)\)", c)
                    k = "no usable report" + (f" ({m.group(2)})" if m else "")
                elif ": wait" in c or "waiting on" in c:
                    k = "wait on strength (the plan)"
                elif not c:
                    k = "no council line"
                else:
                    k = "judged, no launch yet"
                why[k] = why.get(k, 0) + 1
        top = ", ".join(f"{k} {v}" for k, v in sorted(why.items(), key=lambda kv: -kv[1])[:4])
        print(f"  {fn:15s} longest gap {gap[0] / 24000:.2f} d (day {gap[1] / 24000:.2f} to {gap[2] / 24000:.2f}): {top or 'no polls'}")
    days_n = max(1e-9, (t_last - t_first) / 24000)
    n_launch = sum(1 for lr in wl if re.match(r"\s*[\d.]+ \S+\s+LAUNCH", lr))
    print(f"  war rate: {n_launch} launches in {days_n:.2f} days ({n_launch / days_n:.2f} a day); the replay's 35 to 50% of"
          f" the logged launches (recon.md 5.4) is for R8's campaign")

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
