import re

with open("logcat_analyst_dump.txt", "r", encoding="utf-8", errors="replace") as f:
    lines = f.readlines()

print(f"Total lines in logcat dump: {len(lines)}")

sber_lines = []
banking_lines = []
hold_lines = []
notif_lines = []

for line in lines:
    lower = line.lower()
    if "sber" in lower or "сбер" in lower or " 900" in lower:
        sber_lines.append(line.strip())
    if "notificationlistener" in lower or "eventengine" in lower or "financefeatureengine" in lower:
        notif_lines.append(line.strip())
    if "hold" in lower or "удерж" in lower or "заблок" in lower:
        hold_lines.append(line.strip())
    if "com.apb" in lower or "prisbank" in lower or "maib" in lower or "tinkoff" in lower:
        banking_lines.append(line.strip())

print(f"\nSberbank lines ({len(sber_lines)}):")
for l in sber_lines[:20]:
    print("  ", l)

print(f"\nEventEngine / NotificationListener lines ({len(notif_lines)}):")
for l in notif_lines[:20]:
    print("  ", l)

print(f"\nHOLD / Freeze lines ({len(hold_lines)}):")
for l in hold_lines[:20]:
    print("  ", l)

print(f"\nOther banking lines ({len(banking_lines)}):")
for l in banking_lines[:20]:
    print("  ", l)
