"""Assembles docs/heroes/hero-prompt.txt from tools/herotool/prompt-template.txt (run from the repository root), the catalogue, the schema and the example."""
import importlib.util, textwrap, re, sys
spec = importlib.util.spec_from_file_location("ht", "tools/herotool/herotool.py")
ht = importlib.util.module_from_spec(spec); spec.loader.exec_module(ht)
t = open('tools/herotool/prompt-template.txt').read()
old = """SKIN BRIEF: broad, bearded man in his fifties, weathered face, grey-brown hair (#6E6259) under an iron helm...
Arm model: classic"""
new = """SKIN BRIEF:
- Broad man in his fifties, weathered tan face, short grey-brown beard (#6E6259), calm dark eyes.
- Outer layer: a rounded iron helm (#B8B8B8) with a darker rim (#7A7A7A) on the hat layer.
- Body: a dull brown gambeson (#7B5E43) under a chainmail vest (#9A9A9A) on the jacket layer, a wide leather belt.
- Arms: rolled sleeves over thick forearms; leather bracers (#5A3E2B). Legs: dark wool trousers (#4A4038), boots.
- Small brass toll badge (#C9A227) on the chest.
Arm model: classic"""
if old in t:
    t = t.replace(old, new)
NB = " "
roman = {1: "I", 2: "II", 3: "III"}
def keep(s):  # keep a list item on one line
    return s.replace(" ", NB)
fill = {
    "GEAR": ", ".join(ht.GEAR),
    "ENCHANTS": ", ".join(keep(f"{k} {v}") for k, v in ht.ENCHANTS.items()),
    "ITEM_LOOKS": ", ".join(ht.ITEM_LOOKS),
    "PROPS": ", ".join(ht.HELD_PROPS),
    "PARTICLES": ", ".join(ht.PARTICLES),
    "SOUNDS": ", ".join(ht.SOUNDS),
    "PROJECTILES": ", ".join(ht.PROJECTILES) + " (arrow, spectral_arrow and trident arc like arrows; the others fly straight)",
    "HELPFUL": ", ".join(keep(f"{k} (max {roman[l]}, {s} s{', self only' if k == 'invisibility' else ''})")
                         for k, (l, s) in ht.HELPFUL_POTIONS.items()),
    "HARMFUL": ", ".join(keep(f"{k} (max {roman[l]}, {s} s)") for k, (l, s) in ht.HARMFUL_POTIONS.items()),
}
out = []
for ln in t.split("\n"):
    hit = next((k for k in fill if "{{" + k + "}}" in ln), None)
    if hit:
        w = textwrap.wrap(ln.replace("{{" + hit + "}}", fill[hit]), width=116, subsequent_indent="  ",
                          break_on_hyphens=False, break_long_words=False)
        out.append("\n".join(w).replace(NB, " "))
    else:
        out.append(ln)
t = "\n".join(out)
t = t.replace("{{SCHEMA}}", open('tools/herotool/hero.schema.compact.json').read().rstrip())
t = t.replace("{{EXAMPLE}}", open('docs/heroes/examples/bram_ostler.json').read().rstrip())
assert "{{" not in t and NB not in t
open('docs/heroes/hero-prompt.txt', 'w').write(t + "\n")
outside = [i + 1 for i, l in enumerate(t.split("\n")) if len(l) > 118]
print("lines", t.count("\n") + 1, "bytes", len(t.encode()), "long lines (schema only expected):", outside)
