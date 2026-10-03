import sqlite3
import json

db_path = ".sdd/dumps/event_engine.db"
con = sqlite3.connect(db_path)
cur = con.cursor()

cur.execute("SELECT name FROM sqlite_master WHERE type='table';")
tables = [r[0] for r in cur.fetchall()]
print("Tables in event_engine.db:", tables)

for table in tables:
    cur.execute(f"SELECT COUNT(*) FROM `{table}`;")
    count = cur.fetchone()[0]
    print(f"Table {table}: {count} rows")
    cur.execute(f"PRAGMA table_info(`{table}`);")
    columns = [col[1] for col in cur.fetchall()]
    print(f"  Columns: {columns}")
    cur.execute(f"SELECT * FROM `{table}` ORDER BY rowid DESC LIMIT 2;")
    sample = cur.fetchall()
    print(f"  Sample row (latest): {sample[:1]}")

con.close()
