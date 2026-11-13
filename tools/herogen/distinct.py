"""G7: the distinctness NOTEs of HEROES_V2.md 14.2, numbers for review gate 2 (14.4) instead of impressions.

For every two heroes of the same archetype:
  silhouette  their front and side silhouettes, rendered by the preview renderer at 4x in the idle stance (weapon
              sheathed, phase 1, a level camera) in one shared frame, overlap more than 90% (intersection over union of
              the two views together)
  colours     the two largest colour regions of each (the palette slots covering most of the visible front and side,
              the faction-tinted sash left out) are both within CIE76 delta-E 15 of the other's two (best pairing)
Both are NOTEs, never errors: two foxes may share a silhouette on purpose, and the reviewer decides.

A colour region is a palette slot: every texel the painter writes is a tone of one slot's ramp, so a visible pixel's
texel names its slot (a face texel mixed from two colours goes to the nearest ramp colour), and the region's colour is
the slot's authored colour (tone 0).
"""
import numpy as np

import palette
from render import raster

SCALE = 4
VIEWS = (("front", 0.0), ("side", -90.0))
IOU_NOTE = 0.90
DELTA_E_NOTE = 15.0


class Look:
    """What the NOTEs compare for one hero: its silhouette masks (one per view, in a frame shared by the batch) and its
    largest colour regions [(slot, hex, pixels)]."""

    def __init__(self, hero_id, archetype, assets, ramps):
        self.id, self.archetype, self.assets, self.ramps = hero_id, archetype, assets, ramps
        self.masks = None
        self.regions = None

    def views(self):
        return [raster.View(yaw, 0.0, SCALE) for _, yaw in VIEWS]

    def own_bounds(self):
        a = self.assets
        return [raster.model_bounds(a.geo, [v], a.hidden(), a.stance(False)) for v in self.views()]


def _slot_lookup(ramps):
    cols, slots = [], []
    for slot in sorted(k for k in ramps if not k.startswith("_")):
        for rgb in ramps[slot]:
            cols.append(tuple(int(c) for c in rgb))
            slots.append(slot)
    exact = {}
    for c, s in zip(cols, slots):
        exact.setdefault(c, s)
    return exact, np.array(cols, dtype=float), slots


def measure(looks):
    """Render every hero's front and side in one frame per view (the union of their bounds), so masks line up."""
    if not looks:
        return
    per_view = list(zip(*[lk.own_bounds() for lk in looks]))
    frames = [(min(b[0] for b in bs), max(b[1] for b in bs), min(b[2] for b in bs), max(b[3] for b in bs))
              for bs in per_view]
    for lk in looks:
        a = lk.assets
        tint_bones = set(a.bones("tint"))
        masks, counts = [], {}
        exact, cols, slots = _slot_lookup(lk.ramps)
        for view, bounds in zip(lk.views(), frames):
            fr = a.render(view, a.stance(False), bounds=bounds, pad=2, frame=True, tint=None, glow=False)
            on = fr.ids >= 0
            masks.append(on)
            tex, _ = a.textures(1)
            tinted = np.array([q.bone in tint_bones for q in fr.quads] + [True], dtype=bool)
            sel = on & ~tinted[np.where(on, fr.ids, -1)]
            uv = fr.texels[sel]
            rgb = tex[uv[:, 1], uv[:, 0], :3].astype(np.int64)
            if not len(rgb):
                continue
            uniq, inv = np.unique(rgb, axis=0, return_inverse=True)
            n_of = np.bincount(inv.reshape(-1), minlength=len(uniq))
            for colour, n in zip(uniq, n_of):
                key = tuple(int(c) for c in colour)
                s = exact.get(key)
                if s is None:
                    s = slots[int(np.argmin(((cols - colour.astype(float)) ** 2).sum(axis=1)))]
                    exact[key] = s
                counts[s] = counts.get(s, 0) + int(n)
        lk.masks = masks
        hexes = lk.ramps.get("_hex", {})
        lk.regions = [(s, hexes.get(s) or palette.to_hex(lk.ramps[s][2]), n)
                      for s, n in sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))]


def iou(a, b):
    inter = sum(int((x & y).sum()) for x, y in zip(a.masks, b.masks))
    union = sum(int((x | y).sum()) for x, y in zip(a.masks, b.masks))
    return inter / union if union else 1.0


def colour_pairing(a, b):
    """The two largest regions of a and b, paired the way that matches best: (delta-E of each pair, the pairs)."""
    ra, rb = a.regions[:2], b.regions[:2]
    if len(ra) < 2 or len(rb) < 2:
        return None
    straight = [palette.delta_e76(ra[0][1], rb[0][1]), palette.delta_e76(ra[1][1], rb[1][1])]
    crossed = [palette.delta_e76(ra[0][1], rb[1][1]), palette.delta_e76(ra[1][1], rb[0][1])]
    if max(crossed) < max(straight):
        return crossed, [(ra[0], rb[1]), (ra[1], rb[0])]
    return straight, [(ra[0], rb[0]), (ra[1], rb[1])]


def notes(looks):
    """The NOTE lines for every pair of heroes of the same archetype (heroes without an archetype are skipped)."""
    groups = {}
    for lk in looks:
        if lk.archetype:
            groups.setdefault(lk.archetype, []).append(lk)
    todo = [lk for g in groups.values() if len(g) > 1 for lk in g]
    measure(todo)
    out = []
    for arch, group in sorted(groups.items()):
        group = sorted(group, key=lambda lk: lk.id)
        for i in range(len(group)):
            for j in range(i + 1, len(group)):
                a, b = group[i], group[j]
                o = iou(a, b)
                if o > IOU_NOTE:
                    out.append("%s and %s (archetype %s): front and side silhouettes overlap %.0f%% at %dx (more than "
                               "%.0f%%): give one a different head or body silhouette" % (
                                   a.id, b.id, arch, 100 * o, SCALE, 100 * IOU_NOTE))
                pair = colour_pairing(a, b)
                if pair and max(pair[0]) <= DELTA_E_NOTE:
                    desc = ", ".join("%s %s / %s %s (delta-E %.1f)" % (x[0], x[1], y[0], y[1], d)
                                     for (x, y), d in zip(pair[1], pair[0]))
                    out.append("%s and %s (archetype %s): their two largest colour regions are both within delta-E "
                               "%.0f: %s" % (a.id, b.id, arch, DELTA_E_NOTE, desc))
    return out
