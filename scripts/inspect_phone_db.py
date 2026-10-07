import sqlite3

conn = sqlite3.connect('phone_event_engine.db')
cursor = conn.cursor()

print("=== TABLES ===")
cursor.execute("SELECT name FROM sqlite_master WHERE type='table';")
for row in cursor.fetchall():
    print(row[0])

print("\n=== TOTAL EVENTS COUNT ===")
cursor.execute("SELECT COUNT(*) FROM events")
print(cursor.fetchone()[0])

print("\n=== TOTAL TRANSACTIONS COUNT ===")
cursor.execute("SELECT COUNT(*) FROM financial_transactions")
print(cursor.fetchone()[0])

print("\n=== SAMPLE RECENT 20 EVENTS ===")
cursor.execute("SELECT sourcePackage, title, text, category, structuredData FROM events ORDER BY timestamp DESC LIMIT 20")
for r in cursor.fetchall():
    print(f"PKG: {r[0]} | TITLE: {r[1]} | TEXT: {r[2]} | CAT: {r[3]}")

print("\n=== SBERBANK / BANKING / HOLD EVENTS ===")
cursor.execute("SELECT sourcePackage, title, text, category, structuredData FROM events WHERE lower(title || ' ' || text || ' ' || sourcePackage) LIKE '%сбер%' OR lower(title || ' ' || text) LIKE '%900%' OR lower(title || ' ' || text) LIKE '%банк%' OR lower(title || ' ' || text) LIKE '%удерж%' OR lower(title || ' ' || text) LIKE '%холд%' OR lower(title || ' ' || text) LIKE '%заблок%' OR lower(title || ' ' || text) LIKE '%оплат%' LIMIT 30")
rows = cursor.fetchall()
print(f"Found {len(rows)} matching banking/hold events:")
for r in rows:
    print(f"[{r[3]}] PKG: {r[0]} | TITLE: {r[1]} | TEXT: {r[2]}")
