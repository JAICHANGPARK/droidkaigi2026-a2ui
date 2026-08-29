#!/usr/bin/env bash
# Sets up the stage project for the DroidKaigi live demo.
# Usage: ./setup-stage.sh [stage-dir]   (default: ~/stage/a2ui-live)
set -euo pipefail

KIT_DIR="$(cd "$(dirname "$0")" && pwd)"
STAGE_DIR="${1:-$HOME/stage/a2ui-live}"
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

if [ -e "$STAGE_DIR" ]; then
  echo "error: $STAGE_DIR already exists — remove it first or pass another path" >&2
  exit 1
fi

echo "==> 1/5 scaffolding project at $STAGE_DIR"
android create empty-activity --name "A2UI Live" --output "$STAGE_DIR"
cd "$STAGE_DIR"

echo "==> 2/5 patching gradle files (serialization dep, JDK 21)"
perl -pi -e 's/jvmToolchain\(17\)/jvmToolchain(21)/' app/build.gradle.kts
perl -pi -e 's/JavaVersion\.VERSION_17/JavaVersion.VERSION_21/g' app/build.gradle.kts
perl -pi -e 's{(implementation\(libs\.androidx\.lifecycle\.viewmodel\.navigation3\))}{$1\n\n  implementation(libs.kotlinx.serialization.json)}' app/build.gradle.kts
perl -pi -e 's{^(kotlinx-coroutines-test = .*)$}{$1\nkotlinx-serialization-json = \{ module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version = "1.9.0" \}}' gradle/libs.versions.toml

echo "==> 3/5 placing context files + fixture + prompt"
cp "$KIT_DIR/stage-context/CLAUDE.md" ./CLAUDE.md
cp "$KIT_DIR/stage-context/AGENTS.md" ./AGENTS.md
cp "$KIT_DIR/stage-context/spec-summary.md" ./spec-summary.md
mkdir -p app/src/main/assets
cp "$KIT_DIR/stage-context/fixtures/contact_form.jsonl" app/src/main/assets/
cp "$KIT_DIR/kickoff-prompt.txt" ./kickoff-prompt.txt

echo "==> 4/5 warm build (online, ONCE — caches everything for offline stage builds)"
./gradlew assembleDebug

echo "==> 5/5 sanity check: clean offline rebuild"
./gradlew clean assembleDebug --offline

cat <<EOF

============================================================
DONE. Stage project ready at: $STAGE_DIR

Rehearse now (same as on stage):
  cd $STAGE_DIR
  claude "\$(cat kickoff-prompt.txt)"
  # or: run 'claude', paste the contents of kickoff-prompt.txt, press Enter

Before the talk:
  android emulator start <your-avd>    # emulator must already be booted
  git -C $STAGE_DIR init && git -C $STAGE_DIR add -A && git -C $STAGE_DIR commit -m "clean scaffold"
  # → on-stage failure fallback: git reset --hard to this commit and replay the rehearsal recording
============================================================
EOF
