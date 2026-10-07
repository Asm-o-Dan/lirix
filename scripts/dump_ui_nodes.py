import paramiko
import re

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect('127.0.0.1', port=8822, username='u0_a389', password='20060305dan', timeout=15)
_, stdout, _ = client.exec_command('adb shell uiautomator dump /sdcard/dump.xml && adb shell cat /sdcard/dump.xml')
content = stdout.read().decode('utf-8', errors='ignore')

nodes = re.findall(r'<node [^>]+>', content)
for node in nodes:
    desc = re.search(r'content-desc="([^"]*)"', node)
    text = re.search(r'text="([^"]*)"', node)
    bounds = re.search(r'bounds="([^"]*)"', node)
    
    d_val = desc.group(1) if desc else ""
    t_val = text.group(1) if text else ""
    b_val = bounds.group(1) if bounds else ""
    
    if d_val or t_val:
        print(f"text='{t_val}' desc='{d_val}' bounds='{b_val}'")

client.close()
