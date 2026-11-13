"""Emblem stamps (HEROES_V2 13.4, task G6c): small pixel emblems the painter stamps onto a cube face or, through a
paint op, onto whatever the op covers (a crest on a coat's back, a lily on a tabard and a shield, a bell on a choker).

Every emblem is drawn by hand at whole texels, in two to four sizes (5, 7, 9 and for some 11 texels square), because
a scaled bitmap at these sizes turns into noise; a stamp picks the size it is given, or the largest that fits the
face. Characters:
  .  untouched (the face's own paint shows)
  #  the stamp's slot, tone 0
  -  the stamp's slot, one tone darker (an inner line, a shadow)
  +  the stamp's second slot (`slot2`, default the stamp's slot one tone lighter): a highlight, an eye, a crown
  o  the second slot one tone darker
Rows are drawn as seen from outside the face (the uvpack convention), top row first. Every design is our own; the
heraldic lily, the plum blossom and the crane roundel are common motifs, drawn here from scratch.
"""
from util import HerogenError

EMBLEMS = {
    # a cat's paw: four toe beans over a big pad (Natsuho's harbour)
    "paw": {
        5: [".#.#.",
            "#...#",
            ".###.",
            ".###.",
            "..#.."],
        7: ["..#.#..",
            "#.#.#.#",
            "#.....#",
            "..###..",
            ".##+##.",
            ".#####.",
            "..#.#.."],
    },
    # a flame with a lighter heart (Benika's forge)
    "flame": {
        5: ["..#..",
            ".##..",
            ".#+#.",
            "#+++#",
            ".###."],
        7: ["...#...",
            "..##...",
            "..###.#",
            ".##+###",
            ".#+++##",
            "##+++##",
            ".#####."],
        9: ["....#....",
            "...##....",
            "...###...",
            "..####.#.",
            "..##+###.",
            ".##+++##.",
            ".#+++++##",
            ".##+++##.",
            "..#####.."],
    },
    # a round bell with its slit and a highlight (Natsuho's choker, Hinae's shrine)
    "bell": {
        5: ["..#..",
            ".###.",
            "#+###",
            "#---#",
            ".#-#."],
        7: ["..#.#..",
            "...#...",
            ".#####.",
            "#+#####",
            "#######",
            "#-----#",
            ".##-##."],
    },
    # a generic house crest: a ring round a diamond
    "crest": {
        5: [".###.",
            "#.#.#",
            "##+##",
            "#.#.#",
            ".###."],
        7: [".#####.",
            "#.....#",
            "#..#..#",
            "#.#+#.#",
            "#..#..#",
            "#.....#",
            ".#####."],
        9: ["..#####..",
            ".#.....#.",
            "#...#...#",
            "#..###..#",
            "#.##+##.#",
            "#..###..#",
            "#...#...#",
            ".#.....#.",
            "..#####.."],
    },
    # a five-pointed star
    "star": {
        5: ["..#..",
            ".###.",
            "#####",
            ".###.",
            ".#.#."],
        7: ["...#...",
            "..###..",
            "#######",
            ".##+##.",
            "..###..",
            ".##.##.",
            ".#...#."],
        9: ["....#....",
            "...###...",
            "...###...",
            "#########",
            ".###+###.",
            "..#####..",
            "..##.##..",
            ".##...##.",
            ".#.....#."],
    },
    # a moon disc with its lit crescent
    "moon": {
        5: [".###.",
            "#++##",
            "#+###",
            "#++##",
            ".###."],
        7: ["..###..",
            ".#++##.",
            "#++####",
            "#+#####",
            "#++####",
            ".#++##.",
            "..###.."],
    },
    # a leaf with its midrib
    "leaf": {
        5: ["...##",
            "..###",
            ".#-#.",
            "#-#..",
            "-...."],
        7: [".....##",
            "...####",
            "..##-##",
            ".##-##.",
            "##-##..",
            "#-##...",
            "-......"],
    },
    # the frost-lily: a three-petalled lily over a band, the petals' hearts in the second slot (Ilsabet's oath)
    "lily": {
        5: ["..#..",
            "#.#.#",
            "##+##",
            "..#..",
            ".###."],
        7: ["...#...",
            "..#+#..",
            "#.#+#.#",
            "##.#.##",
            ".#####.",
            "...#...",
            "..###.."],
        9: ["....#....",
            "...#+#...",
            "...#+#...",
            "#..#+#..#",
            "##..#..##",
            ".##.#.##.",
            ".#######.",
            "....#....",
            "..#####.."],
        11: [".....#.....",
             "....#+#....",
             "....#+#....",
             "#...#+#...#",
             "##..#+#..##",
             ".##..#..##.",
             "..##.#.##..",
             ".#########.",
             ".....#.....",
             "...#####...",
             "....#.#...."],
    },
    # a crane in flight seen from below: the head with its crown (+) and the long neck up, the wings spread, the legs
    # trailing (Kagenori's house)
    "crane": {
        5: ["..+..",
            "..#..",
            "#####",
            ".###.",
            ".#.#."],
        7: ["...+...",
            "...#...",
            "##.#.##",
            "#######",
            ".#####.",
            "...#...",
            "..#.#.."],
        9: ["....+....",
            "....#....",
            "....#....",
            "##..#..##",
            "####-####",
            ".#######.",
            "...###...",
            "...#.#...",
            "..#...#.."],
    },
    # a plum blossom: five round petals round a lighter heart (Yugen's court crest)
    "plum": {
        5: [".###.",
            "#####",
            "##+##",
            "#####",
            ".#.#."],
        7: ["..###..",
            "#.###.#",
            "###+###",
            "##+++##",
            ".##+##.",
            ".##.##.",
            "......."],
        9: ["...###...",
            "...###...",
            "##.###.##",
            "###.+.###",
            "##.+++.##",
            ".##.+.##.",
            ".###.###.",
            ".###.###.",
            "........."],
    },
    # an eye (a creature's: a crow's), one texel in the second slot with a dark rim below
    "eye": {
        3: ["...",
            ".+.",
            ".-."],
    },
    # a bell seen small, its slit (a choker's bell at 2 texels per px)
    "slit": {
        3: ["...",
            "...",
            "#-#"],
    },
    # brush strokes on a paper talisman: a band at the top, a vertical line of glyphs down the middle (3 wide; the
    # sides stay paper, so strips side by side read as strips, not as a pattern)
    "script": {
        3: ["###",
            "...",
            ".#.",
            ".-.",
            "...",
            ".#.",
            "#-#",
            ".#.",
            "...",
            "..."],
    },
}


def sizes(name):
    if name not in EMBLEMS:
        raise HerogenError("emblem %r is unknown (%s)" % (name, ", ".join(sorted(EMBLEMS))))
    return sorted(EMBLEMS[name])


def bitmap(name, size=None, fit=None):
    """The rows of an emblem: at `size`, or the largest that fits `fit` (w, h texels; the smallest when none fits)."""
    have = sizes(name)
    if size is not None:
        if int(size) not in EMBLEMS[name]:
            raise HerogenError("emblem %s has no size %s (sizes: %s)" % (name, size, ", ".join(map(str, have))))
        return EMBLEMS[name][int(size)]
    if fit is not None:
        ok = [s for s in have if len(EMBLEMS[name][s][0]) <= fit[0] and len(EMBLEMS[name][s]) <= fit[1]]
        return EMBLEMS[name][ok[-1] if ok else have[0]]
    return EMBLEMS[name][have[-1]]


def check_all():
    """Every bitmap is rectangular, uses the known characters and is the size it is filed under (its width)."""
    out = []
    for name, by in EMBLEMS.items():
        for s, rows in by.items():
            w = len(rows[0])
            if w != s:
                out.append("%s %d: rows are %d wide" % (name, s, w))
            for r in rows:
                if len(r) != w:
                    out.append("%s %d: rows differ in width" % (name, s))
                if set(r) - set(".#-+o"):
                    out.append("%s %d: unknown characters %s" % (name, s, "".join(sorted(set(r) - set(".#-+o")))))
    return out


def rotated(rows, deg):
    """The rows turned clockwise by 0, 90, 180 or 270 degrees (an emblem upright on a face lying on its side)."""
    for _ in range((deg // 90) % 4):
        rows = ["".join(rows[len(rows) - 1 - j][i] for j in range(len(rows))) for i in range(len(rows[0]))]
    return rows
