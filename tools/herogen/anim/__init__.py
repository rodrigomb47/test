"""The animation generator (HEROES_V2.md 13.6, task G3).

Modules:
  clip       keys, tracks and clips with GeckoLib 4.9.3's keyframe semantics (easings ported from its EasingType)
  skeleton   forward kinematics in file space (the bone transforms GeckoLib applies), sole points of the feet
  poses      the named pose library (herosrc/anim/poses), `extends`, mirroring, ground and level-feet fixes
  primitives procedural motions (idle, walk_cycle, run_cycle, breathe, ear_twitch, ...)
  recipes    recipe -> clip: pose keys, layers, face keys, markers, overshoot, settle
  secondary  the follow-through bake (damped springs on hair, ears, tails, sleeves, skirts, capes)
  writer     GeckoLib .animation.json and the server timing file
  quality    the animation rules of 13.6 / 14.2
  library    the core and style libraries, a hero's own file, output paths
  preview    filmstrips and GIFs for the owner
"""
