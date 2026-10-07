import subprocess

adb_path = r"C:\Users\DaniilTuT\AppData\Local\Android\Sdk\platform-tools\adb.exe"
cmd = [adb_path, "-s", "2440cbe2", "exec-out", "run-as", "com.eventengine.app.debug", "cat", "databases/event_engine.db"]

print("Running command:", " ".join(cmd))
res = subprocess.run(cmd, capture_output=True)

if res.returncode == 0:
    with open("phone_db.db", "wb") as f:
        f.write(res.stdout)
    print(f"Successfully wrote {len(res.stdout)} bytes to phone_db.db")
else:
    print("Error:", res.stderr.decode("utf-8", errors="replace"))
