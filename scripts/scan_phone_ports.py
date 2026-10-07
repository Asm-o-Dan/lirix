import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
try:
    ssh.connect('192.168.88.233', port=8022, username='u0_a389', password='20060305dan', timeout=5)
    script = """
import socket
for p in range(30000, 48000):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(0.003)
    if s.connect_ex(('127.0.0.1', p)) == 0:
        print(f"PORT:{p}")
    s.close()
"""
    stdin, stdout, stderr = ssh.exec_command("python3 -c " + repr(script))
    out = stdout.read().decode()
    print("OUTPUT:", out)
    ssh.close()
except Exception as e:
    print("ERROR:", e)
