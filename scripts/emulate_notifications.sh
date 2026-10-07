#!/usr/bin/env bash
# ==============================================================================
# Fast Notification & Event Emulation via ADB for Android Event Engine (Bash)
# ==============================================================================

set -euo pipefail

MODE="${1:-hard_cases}"
PARAM="${2:-}"
DELAY_SEC="${DELAY_SEC:-0.8}"

DATASET_FILE="$(dirname "$0")/../dataset/golden_dataset.json"

if ! command -v adb &> /dev/null; then
    echo "[ERROR] 'adb' command not found in PATH." >&2
    exit 1
fi

echo "=== Connected ADB Devices ==="
adb devices

if [ ! -f "$DATASET_FILE" ]; then
    echo "[ERROR] Golden dataset not found at $DATASET_FILE" >&2
    exit 1
fi

echo "=== Running Notification Emulation (Mode: $MODE) ==="

# Python one-liner to parse JSON and output pipe-delimited records: id|title|text|category|is_hard
python3 -c '
import json, sys

mode = "'"$MODE"'"
param = "'"$PARAM"'"
dataset_path = "'"$DATASET_FILE"'"

with open(dataset_path, "r", encoding="utf-8") as f:
    data = json.load(f)

samples = data.get("samples", [])
if mode == "hard_cases":
    samples = [s for s in samples if s.get("is_hard_case")]
elif mode == "category":
    samples = [s for s in samples if s.get("category") == param]

for s in samples:
    sid = s.get("id", "")
    title = (s.get("title") or "System").replace("|", " ")
    text = (s.get("text") or "").replace("|", " ").replace("\n", " ")
    cat = s.get("category", "")
    hard = str(s.get("is_hard_case", False))
    print(f"{sid}|{title}|{text}|{cat}|{hard}")
' | while IFS='|' read -r sid title text cat hard; do
    echo ">>> [ID: $sid] [$cat] [Hard: $hard]"
    echo "    Title: $title"
    echo "    Text:  $text"
    adb shell cmd notification post -S bigtext -t "aee_$sid" "$title" "$text"
    sleep "$DELAY_SEC"
done

echo "[DONE] Emulation complete."
