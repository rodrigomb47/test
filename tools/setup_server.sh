#!/bin/bash
# Sets up a NeoForge 21.1.209 dedicated server for campaign tests (RCON on 25575, password fctest, fixed seed) and
# puts the freshly built mod jar in its mods folder. Re-running it only refreshes the jar and the settings.
#   tools/setup_server.sh <server_dir>        (build the jar first: ./gradlew build -x test)
set -e
REPO="$(cd "$(dirname "$0")/.." && pwd)"
DIR="${1:?usage: tools/setup_server.sh <server_dir>}"
mkdir -p "$DIR" && cd "$DIR"
if [ ! -f run.sh ]; then
  [ -f neoforge-installer.jar ] || curl -sSL -o neoforge-installer.jar \
    https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.209/neoforge-21.1.209-installer.jar
  java -jar neoforge-installer.jar --installServer > install.log 2>&1
fi
echo "eula=true" > eula.txt
echo "-Xmx8G" > user_jvm_args.txt
cat > server.properties <<'PROPS'
enable-rcon=true
rcon.port=25575
rcon.password=fctest
broadcast-rcon-to-ops=false
level-name=world
level-seed=20261107
level-type=minecraft\:normal
difficulty=normal
gamemode=survival
online-mode=false
spawn-protection=0
max-tick-time=-1
view-distance=6
simulation-distance=6
motd=Faction Clash test
PROPS
mkdir -p mods
cp "$REPO/build/libs/factionclash-0.1.0.jar" mods/
echo "ready: cd $DIR && ./run.sh nogui   (then: python3 $REPO/tools/rcon.py 'fc start 256')"
