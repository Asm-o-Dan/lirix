"""Copy Lirix data from a connected phone before installing a new build.

Usage:
    python scripts/lirix_phone_snapshot.py [--serial SERIAL] [--out DIR] [--package com.lirix.app]

Reads only. Copies `databases/` and `shared_prefs/` of the installed app through `run-as`
(works for debuggable builds) into DIR/<timestamp>/ outside the repository, then checks the
copy with SQLite `quick_check` and prints row counts per table. Lyrics, titles and other
content are never printed.

If the installed build is not debuggable (a release APK from GitHub), `run-as` is refused:
export the library archive from the app instead (Settings -> Library -> Export) and keep the
file before installing anything.

Exit codes: 0 snapshot verified, 1 no usable device or app, 2 copy refused or incomplete,
3 copy made but the database check failed.
"""
import argparse
import datetime
import io
import os
import shutil
import sqlite3
import subprocess
import sys
import tarfile

DEFAULT_PACKAGE = "com.lirix.app"
DATABASE = "event_engine.db"


def find_adb() -> str:
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        root = os.environ.get(env)
        if root:
            candidate = os.path.join(root, "platform-tools", "adb.exe" if os.name == "nt" else "adb")
            if os.path.isfile(candidate):
                return candidate
    local = os.path.join(os.path.expanduser("~"), "AppData", "Local", "Android", "Sdk", "platform-tools", "adb.exe")
    if os.path.isfile(local):
        return local
    found = shutil.which("adb")
    if found:
        return found
    sys.exit("adb not found: set ANDROID_HOME or add platform-tools to PATH")


def adb(adb_path: str, serial: str | None, *args: str, binary: bool = False):
    cmd = [adb_path] + (["-s", serial] if serial else []) + list(args)
    result = subprocess.run(cmd, capture_output=True, timeout=300)
    return result.returncode, result.stdout if binary else result.stdout.decode("utf-8", "replace"), result.stderr.decode("utf-8", "replace")


def pick_device(adb_path: str, serial: str | None) -> str:
    _, out, _ = adb(adb_path, None, "devices")
    devices = [line.split()[0] for line in out.splitlines()[1:] if line.strip().endswith("device")]
    if serial:
        if serial not in devices:
            print(f"Device {serial} is not connected. Connected: {devices or 'none'}")
            sys.exit(1)
        return serial
    if len(devices) != 1:
        print(f"Expected exactly one device, found {len(devices)}: {devices}. Pass --serial.")
        sys.exit(1)
    return devices[0]


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial")
    parser.add_argument("--package", default=DEFAULT_PACKAGE)
    parser.add_argument("--out", default=os.path.join(os.path.expanduser("~"), "lirix-phone-snapshots"))
    args = parser.parse_args()

    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    out_root = os.path.abspath(args.out)
    if os.path.commonpath([out_root, repo_root]) == repo_root:
        print("Refusing to write personal data inside the repository; choose --out outside it.")
        return 1

    adb_path = find_adb()
    serial = pick_device(adb_path, args.serial)
    _, packages, _ = adb(adb_path, serial, "shell", "pm", "list", "packages", args.package)
    if f"package:{args.package}" not in packages.split():
        print(f"{args.package} is not installed on {serial}.")
        return 1

    code, archive, err = adb(adb_path, serial, "exec-out", f"run-as {args.package} tar cf - databases shared_prefs", binary=True)
    if code != 0 or not archive or b"run-as:" in archive[:200]:
        message = archive[:200].decode("utf-8", "replace") if archive else err
        print(f"Copy refused ({message.strip() or 'no data'}).")
        print("The installed build is probably not debuggable. Export the library archive in the app instead.")
        return 2

    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    target = os.path.join(out_root, f"{serial}-{stamp}")
    os.makedirs(target, exist_ok=False)
    with open(os.path.join(target, "app-data.tar"), "wb") as f:
        f.write(archive)
    try:
        with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
            members = [m for m in tar.getmembers() if m.isfile() and not os.path.isabs(m.name) and ".." not in m.name.split("/")]
            tar.extractall(target, members=members)
    except tarfile.TarError as e:
        print(f"Copy is not a valid archive: {e}")
        return 2

    db_path = os.path.join(target, "databases", DATABASE)
    if not os.path.isfile(db_path):
        print(f"Saved to {target}, but {DATABASE} is missing from the copy.")
        return 2

    # Check a separate copy so the saved files stay exactly as pulled.
    check_dir = os.path.join(target, "check")
    shutil.copytree(os.path.join(target, "databases"), check_dir)
    conn = sqlite3.connect(os.path.join(check_dir, DATABASE))
    try:
        status = conn.execute("PRAGMA quick_check").fetchone()[0]
        version = conn.execute("PRAGMA user_version").fetchone()[0]
        tables = [r[0] for r in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' "
            "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name")]
        counts = {t: conn.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0] for t in tables}
    finally:
        conn.close()
    shutil.rmtree(check_dir, ignore_errors=True)

    print(f"Saved to {target}")
    print(f"Schema version {version}, quick_check: {status}")
    for table, count in counts.items():
        print(f"  {table}: {count}")
    prefs = sorted(os.listdir(os.path.join(target, "shared_prefs"))) if os.path.isdir(os.path.join(target, "shared_prefs")) else []
    print(f"Preference files: {len(prefs)}")
    return 0 if status == "ok" else 3


if __name__ == "__main__":
    sys.exit(main())
