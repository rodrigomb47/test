"""Animated previews (HEROES_V2.md 13.8): heroes side by side playing their animation files through the controller
emulation (render.animfile.Playback), with HeroFace's frames (render.face_sim: moods, authored face keys, blinks),
written as looping GIFs with one shared palette (no colour flicker between frames).

A GIF frame is one game tick at 20 fps (blinks are 1-2 ticks, so tick-exact frames show every blink frame) or two
ticks at 10 fps. Keyframe events (the hit, the sweep sound) are written over the frame on which they fire.
"""
import os

import numpy as np
from PIL import Image, ImageDraw

from render import raster
from render.scene import PITCH, SASH_TINT, to_image
from render.sheet import BG, DIM, INK, font


class Actor:
    """One hero in a film: its assets, playback, face, camera and framing."""

    def __init__(self, assets, playback, face, view, drawn=False, tint=SASH_TINT, label=None, head_only=False):
        self.a, self.pb, self.face, self.view = assets, playback, face, view
        self.drawn, self.tint = drawn, tint
        self.label = label or assets.name
        self.head_only = head_only
        self.bounds = None

    def pose(self, t):
        return self.pb.pose(t)

    def frames(self, t, pose):
        w = self.pb.windup_until()
        running = self.pb.action_phase(t) in ("blend", "play")
        blink_ok = not (running and w is not None and t < w)     # no blinking during a wind-up (4.6)
        return self.face.frames(t, pose, blink_ok=blink_ok)

    def frame_bounds(self, times):
        """Bounds over the film (sampled), feet on one line just below the ground."""
        poses = [self.pose(t) for t in times[::max(1, len(times) // 16)] + [times[-1]]]
        b = self.a.bounds([self.view], poses, drawn=self.drawn)
        if self.head_only:
            hy = self.a.visual["head_y"]
            return (max(b[0], -12.0), min(b[1], 12.0), hy - 3.0, min(b[3], hy + 18.0))
        return (b[0] - 1.0, b[1] + 1.0, min(b[2], -1.0), b[3] + 1.0)

    def render(self, t):
        pose = self.pose(t)
        face = self.frames(t, pose)
        return to_image(self.a.render(self.view, pose, face=face, drawn=self.drawn, tint=self.tint,
                                      bounds=self.bounds, pad=2)), face


def film(actors, times, title, events=True, gap=14):
    """RGBA frames: the actors side by side, a caption with the clip time, each actor's name under it, and the
    events firing on a frame written over it."""
    for ac in actors:
        ac.bounds = ac.frame_bounds(times)
    small, tiny = font(13), font(11)
    frames = []
    dt = (times[1] - times[0]) if len(times) > 1 else 0.05
    for t in times:
        cells = [(ac, ac.render(t)[0]) for ac in actors]
        w = max(360, sum(c.width for _, c in cells) + gap * (len(cells) + 1))
        h = max(c.height for _, c in cells) + 48
        im = Image.new("RGBA", (w, h), BG)
        d = ImageDraw.Draw(im)
        d.text((10, 6), "%.2f s  %s" % (t, title), fill=INK, font=small)
        x = gap + (w - sum(c.width for _, c in cells) - gap * (len(cells) + 1)) // 2
        for ac, c in cells:
            im.alpha_composite(c, (x, h - 20 - c.height))
            d.text((x + c.width // 2, h - 16), ac.label, fill=DIM, font=tiny, anchor="ma")
            if events:
                evs = ac.pb.events(t - dt + 1e-6, t + 1e-6)              # fired since the last frame
                fired = [v for at, kind, v in evs if kind == "instruction"] + ["sound" for at, kind, v in evs
                                                                               if kind == "sound"]
                if fired:
                    d.text((x + c.width // 2, 24), " ".join(fired), fill=(200, 30, 30, 255), font=small, anchor="ma")
            x += c.width + gap
        frames.append(im)
    return frames


def write_gif(frames, path, fps=20):
    """A looping GIF with one palette for every frame (255 colours from a sample of the frames, leaving an index free
    for the transparency of unchanged pixels), no dithering."""
    rgb = [f.convert("RGB") if f.mode != "RGB" else f for f in frames]
    sample = rgb[::max(1, len(rgb) // 24)]
    w = max(f.width for f in sample)
    mosaic = Image.new("RGB", (w, sum(f.height for f in sample)))
    y = 0
    for f in sample:
        mosaic.paste(f, (0, y))
        y += f.height
    pal = mosaic.quantize(colors=255, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    out = [f.quantize(palette=pal, dither=Image.Dither.NONE) for f in rgb]
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    # optimize: Pillow writes each frame as the difference from the last (unchanged pixels transparent), which
    # decodes to the same frames (tests/test_render.py) at a fraction of the size
    out[0].save(path, save_all=True, append_images=out[1:], duration=int(round(1000 / fps)), loop=0,
                optimize=True, disposal=1)
    return path


def ticks(t0, t1, fps):
    n = max(1, int(round((t1 - t0) * fps)))
    return [round(t0 + i / fps, 6) for i in range(n)]


# ------------------------------------------------------------------ the standard films

def idle_film(heroes, t0=0.0, t1=24.0, fps=10, yaw=-30.0, scale=3, head_only=False, mood="calm"):
    """move.idle with blinking (each hero its own blink seed), from t0 to t1."""
    actors = []
    for i, a in enumerate(heroes):
        actors.append(Actor(a, a.playback("move.idle"), a.face_sim(mood, i), raster.View(yaw, 4.0 if head_only
                                                                                          else PITCH, scale),
                            head_only=head_only))
    title = "move.idle, blinking" + (" (heads)" if head_only else "")
    return film(actors, ticks(t0, t1, fps), title, events=False)


def move_film(heroes, name, fps=20, yaw=-55.0, scale=3):
    """A base loop (move.walk, move.run) over one cycle."""
    actors = [Actor(a, a.playback(name), a.face_sim("calm", i), raster.View(yaw, PITCH, scale))
              for i, a in enumerate(heroes)]
    length = actors[0].pb.base.length
    return film(actors, ticks(0.0, length, fps), "%s (%.2f s loop)" % (name, length), events=False)


def action_film(heroes, action="attack.1", lead=0.3, tail=0.4, fps=20, yaw=-34.0, scale=3):
    """An action over combat.idle: `lead` seconds of stance, the action (its blend, then the clip), `tail` seconds
    back on the stance; events are written on the frame they fire."""
    actors = []
    for i, a in enumerate(heroes):
        pb = a.playback(action=action, action_at=lead)
        actors.append(Actor(a, pb, a.face_sim("calm", i), raster.View(yaw, PITCH, scale), drawn=True))
    end = actors[0].pb.length() + tail
    return film(actors, ticks(0.0, end, fps), "%s at %.2f s over combat.idle" % (action, lead))


def strip(frames, every):
    """Every n-th frame side by side (a check image of a film)."""
    pick = frames[::every]
    w = sum(f.width for f in pick)
    out = Image.new("RGBA", (w, max(f.height for f in pick)), BG)
    x = 0
    for f in pick:
        out.alpha_composite(f, (x, 0))
        x += f.width
    return out


def frame_digest(frames):
    """A digest of the decoded frames (for the determinism tests)."""
    import hashlib
    h = hashlib.sha256()
    for f in frames:
        h.update(np.asarray(f).tobytes())
    return h.hexdigest()
