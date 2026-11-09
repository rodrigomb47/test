#!/bin/bash
# Runs a campaign on a running test server one in-game day at a time (/tick sprint 24000), appending tools/sim.py's
# full summary after each day; stops early on a crash report. Ends with a DONE line.
#   tools/days.sh <server_dir> <days> <out_log>
SV="$1"; N="$2"; OUT="$3"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"
for i in $(seq 1 "$N"); do
  echo "######## sprint $i" >> "$OUT"
  timeout 3600 python3 tools/sim.py "$SV" sprint 24000 >> "$OUT" 2>&1
  if ls "$SV"/crash-reports/* >/dev/null 2>&1; then echo CRASH >> "$OUT"; break; fi
done
echo DONE >> "$OUT"
