#!/usr/bin/env python3
"""
Lirix Device Demo Asset Capturer
Captures screenshots and MP4 video recordings from connected Android device via ADB.
"""

import os
import sys
import time
import subprocess
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEVICE_SERIAL = "2440cbe2"
OUTPUT_DIR = REPO_ROOT / ".marketing" / "assets"

def get_adb_path():
    local_app_data = os.environ.get("LOCALAPPDATA", "")
    default_sdk = Path(local_app_data) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
    if default_sdk.exists():
        return str(default_sdk)
    return "adb"

def capture_screenshot(filename="screenshot_latest.png"):
    adb = get_adb_path()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    target_path = OUTPUT_DIR / filename
    
    cmd = f'"{adb}" -s {DEVICE_SERIAL} exec-out screencap -p > "{target_path}"'
    subprocess.run(cmd, shell=True, check=True)
    print(f"[OK] Screenshot saved to: {target_path}")
    return str(target_path)

def record_screen(duration_seconds=5, filename="demo_flow.mp4"):
    adb = get_adb_path()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    device_tmp = "/sdcard/lirix_record.mp4"
    local_target = OUTPUT_DIR / filename
    
    print(f"[*] Recording screen on {DEVICE_SERIAL} for {duration_seconds}s...")
    record_proc = subprocess.Popen([adb, "-s", DEVICE_SERIAL, "shell", f"screenrecord --time-limit {duration_seconds} {device_tmp}"])
    record_proc.wait()
    
    print(f"[*] Pulling recording to {local_target}...")
    subprocess.run([adb, "-s", DEVICE_SERIAL, "pull", device_tmp, str(local_target)], check=True)
    subprocess.run([adb, "-s", DEVICE_SERIAL, "shell", f"rm {device_tmp}"], check=True)
    print(f"[OK] Video demo saved to: {local_target}")
    return str(local_target)

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--video":
        sec = int(sys.argv[2]) if len(sys.argv) > 2 else 5
        record_screen(sec)
    else:
        capture_screenshot()
