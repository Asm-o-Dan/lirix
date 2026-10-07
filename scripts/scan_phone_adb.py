import paramiko

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect('192.168.88.233', port=8022, username='u0_a389', password='20060305dan', timeout=5)

scan_code = """
import socket
for p in range(30000, 50000):
    s = socket.socket()
    s.settimeout(0.005)
    if s.connect_ex(('127.0.0.1', p)) == 0:
        print('OPEN:', p)
    s.close()
"""

stdin, stdout, stderr = client.exec_command(f"python3 -c \"{scan_code}\"")
print("RESULT:", stdout.read().decode())
print("STDERR:", stderr.read().decode())
client.close()
