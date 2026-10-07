import re

with open("dump.xml", "r", encoding="utf-8") as f:
    content = f.read()

nodes = re.findall(r'<node [^>]+>', content)
for node in nodes:
    desc = re.search(r'content-desc="([^"]*)"', node)
    text = re.search(r'text="([^"]*)"', node)
    bounds = re.search(r'bounds="([^"]*)"', node)
    d = desc.group(1) if desc else ""
    t = text.group(1) if text else ""
    b = bounds.group(1) if bounds else ""
    if d or t:
        print(f"[{b}] text='{t}' desc='{d}'")
