import sqlite3
import sys
import os

# Force UTF-8 output
sys.stdout.reconfigure(encoding='utf-8')

conn = sqlite3.connect("event_engine_py.db")
cur = conn.cursor()

# List tables
cur.execute("SELECT name FROM sqlite_master WHERE type='table'")
tables = [t[0] for t in cur.fetchall()]
print("TABLES:", tables)
print()

# Events count
cur.execute("SELECT COUNT(*) FROM events")
print("Events total:", cur.fetchone()[0])

cur.execute("SELECT COUNT(*) FROM financial_transactions")
print("Transactions total:", cur.fetchone()[0])
print()

def safe(x, maxlen=120):
    if x is None: return "NULL"
    s = str(x)
    # Replace non-printable or problematic chars
    return s[:maxlen]

# Events by category
print("=== EVENTS BY CATEGORY ===")
cur.execute("SELECT category, COUNT(*) as cnt FROM events GROUP BY category ORDER BY cnt DESC")
for r in cur.fetchall():
    print(f"  {r[0]}: {r[1]}")

print()
print("=== RECENT EVENTS (last 50) ===")
cur.execute("""
SELECT datetime(timestamp/1000,'unixepoch','localtime'),
       source_package, title, substr(text,1,100), category, event_type, confidence
FROM events ORDER BY timestamp DESC LIMIT 50
""")
for r in cur.fetchall():
    print(" | ".join(safe(x) for x in r))

print()
print("=== FINANCIAL TRANSACTIONS (all) ===")
cur.execute("""
SELECT datetime(timestamp/1000,'unixepoch','localtime'),
       direction, amountCents, currencyCode, rawMerchant, normalizedMerchant, category, sourcePackage, accountMask
FROM financial_transactions ORDER BY timestamp DESC
""")
rows = cur.fetchall()
print(f"Count: {len(rows)}")
for r in rows:
    print(" | ".join(safe(x) for x in r))

print()
print("=== FINANCE EVENTS (raw events with finance category) ===")
cur.execute("""
SELECT datetime(timestamp/1000,'unixepoch','localtime'),
       source_package, title, substr(text,1,150), category, normalized_text
FROM events WHERE category LIKE '%finance%' OR category LIKE '%FINANCE%'
   OR source_package LIKE '%sber%' OR source_package LIKE '%bank%'
   OR title LIKE '%900%' OR title LIKE '%сбер%' OR title LIKE '%банк%'
ORDER BY timestamp DESC LIMIT 40
""")
for r in cur.fetchall():
    print(" | ".join(safe(x) for x in r))

print()
print("=== ALL SOURCE PACKAGES ===")
cur.execute("SELECT DISTINCT source_package, COUNT(*) as cnt FROM events GROUP BY source_package ORDER BY cnt DESC")
for r in cur.fetchall():
    print(f"  {safe(r[0])}: {r[1]}")

conn.close()
print("\nDone.")
