import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
adb = r'C:\Users\DaniilTuT\AppData\Local\Android\Sdk\platform-tools\adb.exe'

for fn in ['event_engine.db', 'event_engine.db-wal', 'event_engine.db-shm']:
    cmd = [adb, 'shell', 'run-as', 'com.lirix.app', 'cat', f'/data/data/com.lirix.app/databases/{fn}']
    res = subprocess.run(cmd, capture_output=True)
    if res.returncode == 0:
        with open(fn, 'wb') as f:
            f.write(res.stdout)
        print(f'Pulled {fn}, size: {len(res.stdout)}')

import sqlite3
conn = sqlite3.connect('event_engine.db')
c = conn.cursor()
for row in c.execute("SELECT trackKey, chordsAmDm FROM lyrics_cache WHERE trackKey LIKE '%Feel Good%'"):
    print('TrackKey:', row[0])
    chords = row[1]
    print('Chords is None?:', chords is None)
    if chords:
        print('Chords length:', len(chords))
        print('First 300 chars:')
        print(repr(chords[:300]))
