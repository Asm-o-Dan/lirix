import subprocess
import sqlite3
import os
import sys

ADB_PATH = r"C:\Users\DaniilTuT\platform-tools\adb.exe"
DEVICE = "2440cbe2"
PKG = "com.eventengine.app.debug"

def pull_file(remote_rel_path, local_path):
    cmd = [ADB_PATH, "-s", DEVICE, "exec-out", f"run-as {PKG} cat {remote_rel_path}"]
    res = subprocess.run(cmd, capture_output=True)
    if res.returncode != 0:
        print(f"Failed to pull {remote_rel_path}: {res.stderr}")
        return False
    with open(local_path, "wb") as f:
        f.write(res.stdout)
    return True

def main():
    if sys.stdout and hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    db_dir = os.path.join(os.path.dirname(__file__), "..", "build_tmp")
    os.makedirs(db_dir, exist_ok=True)
    local_db = os.path.join(db_dir, "event_engine_device.db")
    local_wal = os.path.join(db_dir, "event_engine_device.db-wal")
    local_shm = os.path.join(db_dir, "event_engine_device.db-shm")

    print("[1] Pulling database binary files from Poco M7...")
    pull_file("databases/event_engine.db", local_db)
    pull_file("databases/event_engine.db-wal", local_wal)
    pull_file("databases/event_engine.db-shm", local_shm)

    print(f"[2] Connecting to pulled SQLite db: {local_db} (size: {os.path.getsize(local_db)} bytes, wal: {os.path.getsize(local_wal)} bytes)")
    conn = sqlite3.connect(local_db)
    cursor = conn.cursor()

    cursor.execute("SELECT count(*) FROM events;")
    total = cursor.fetchone()[0]
    print(f"Total events in database: {total}\n")

    cursor.execute("""
        SELECT id, timestamp, source, source_package, app_name, title, text, sub_text, 
               category, event_type, confidence, is_ongoing, media_track, media_artist, 
               media_playback_state, raw_payload, structured_data
        FROM events 
        ORDER BY timestamp ASC;
    """)
    rows = cursor.fetchall()

    for r in rows:
        eid, ts, src, pkg, app, title, text, subText, cat, evType, conf, ongoing, mTrack, mArtist, mState, raw, sData = r
        print("=" * 80)
        print(f"Event ID: #{eid} | Timestamp: {ts} | Source: {src} | Ongoing: {ongoing}")
        print(f"Package:  {pkg} (App: '{app}')")
        print(f"Title:    '{title}'")
        print(f"Text:     '{text}'")
        print(f"SubText:  '{subText}'")
        print(f"Category: [{cat}] | Type: {evType} | Confidence: {conf}")
        if mTrack or mArtist or mState:
            print(f"Media:    Artist='{mArtist}' | Track='{mTrack}' | State='{mState}'")
        print(f"Raw:      {raw}")
        print(f"Data:     {sData}")

if __name__ == "__main__":
    main()
