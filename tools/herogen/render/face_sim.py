"""HeroFace emulated for the previews (HEROES_V2.md 4.6): which eye, mouth and cheek frame a hero shows at a time.

Priority, highest first, as 4.6 lists it: (1) defeated: eyes closed, mouth neutral; (2) authored keys: the running
animation's `face_ctrl` (render.animfile.authored_face; each plane on its own); (3) talking: the mouth cycles talk A,
talk B, neutral every 2-3 ticks; (4) the mood's frames from visual.json `face.moods`; (5) a blink over the eye frames
in `face.blink.over_eyes`, never while `blink_ok` is false (a wind-up or a channel); (6) beyond 16 blocks the face
stays on the mood frame (`far`).

Blinks follow visual.json `face.blink`: the next blink starts a random interval (uniform in `interval` seconds, whole
ticks) after the last one ended; a blink is half, closed, half for `ticks` game ticks; `double_chance` of the blinks
are doubled, the second starting DOUBLE_GAP ticks after the first ends. The randomness is splitmix64 seeded from a
text (the hero id and an instance number here; the entity UUID in game), so two heroes never blink in step. This is a
preview stand-in written from the design: A2's HeroFace implements 4.6 and is the reference; DOUBLE_GAP is the one
number 4.6 leaves open (PROPOSED).
"""
from anim.clip import TICK
from util import Rng

DOUBLE_GAP = 2                  # open ticks between the two blinks of a double blink (PROPOSED for A2)
HALF, CLOSED = 1, 2             # eye frames
TALK = (2, 3, 0)                # talk A, talk B, neutral


class FaceSim:
    def __init__(self, visual, seed, mood="calm"):
        face = visual["face"]
        self.moods = face["moods"]
        if mood not in self.moods:
            raise KeyError("mood %r (%s)" % (mood, ", ".join(face.get("mood_order", sorted(self.moods)))))
        self.mood = mood
        b = face["blink"]
        self.interval = (float(b["interval"][0]), float(b["interval"][1]))
        self.ticks = tuple(int(x) for x in b["ticks"])
        self.double = float(b.get("double_chance", 0.15))
        self.over = set(b.get("over_eyes", [0, 4, 5, 8, 9]))
        self.seed = str(seed)
        self._blinks = []           # (start tick, end tick) of every blink so far, in order
        self._rng = Rng("blink:" + self.seed)
        self._next = self._interval_ticks()

    def _interval_ticks(self):
        lo, hi = self.interval
        return int(round((lo + (hi - lo) * self._rng.random()) / TICK))

    def blinks_until(self, tick):
        """Every blink starting at or before `tick`: [(start, end)), in ticks."""
        length = sum(self.ticks)
        while self._next <= tick:
            start = self._next
            self._blinks.append((start, start + length))
            end = start + length
            if self._rng.random() < self.double:
                start2 = end + DOUBLE_GAP
                self._blinks.append((start2, start2 + length))
                end = start2 + length
            self._next = end + self._interval_ticks()
        return [b for b in self._blinks if b[0] <= tick]

    def blink_frame(self, tick):
        """The eye frame a blink shows at this tick (HALF or CLOSED), or None."""
        for start, end in reversed(self.blinks_until(tick)):
            if start <= tick < end:
                k = tick - start
                a, b, _ = self.ticks
                return HALF if k < a or k >= a + b else CLOSED
            if end <= tick:
                break
        return None

    def frames(self, t, pose=None, blink_ok=True, talking=False, defeated=False, far=False):
        """(eyes, mouth, cheeks) at wall time t (seconds)."""
        from render.animfile import authored_face
        m = self.moods[self.mood]
        mood = (m["eyes"], m["mouth"], m["cheeks"])
        if defeated:
            return (CLOSED, 0, mood[2])
        if far:
            return mood
        tick = int(round(t / TICK))
        auth = authored_face(pose or {})
        eyes = auth[0] if auth[0] is not None else mood[0]
        mouth = auth[1] if auth[1] is not None else mood[1]
        cheeks = auth[2] if auth[2] is not None else mood[2]
        if talking and auth[1] is None:
            mouth = TALK[(tick // 3) % 3]
        if auth[0] is None and blink_ok and eyes in self.over:
            k = self.blink_frame(tick)
            if k is not None:
                eyes = k
        return (eyes, mouth, cheeks)
