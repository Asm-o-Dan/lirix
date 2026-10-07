import sqlite3

conn = sqlite3.connect("event_engine_py.db")
cursor = conn.cursor()
cursor.execute("SELECT id, timestamp, title, text, category, structured_data, raw_payload FROM events WHERE source_package = 'com.google.android.apps.messaging'")
rows = cursor.fetchall()
print(f"Total Google Messaging events: {len(rows)}")
for r in rows:
    print("---------------------------------")
    print("ID:", r[0])
    print("TITLE:", repr(r[2]))
    print("TEXT:", repr(r[3]))
    print("CAT:", r[4])
    print("DATA:", r[5])
    print("RAW:", repr(r[6]))
