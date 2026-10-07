import paramiko

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect('192.168.88.233', port=8022, username='u0_a389', password='20060305dan', timeout=5)

cmd = """
for p in $(seq 30000 45000); do
  (echo >/dev/tcp/127.0.0.1/$p) 2>/dev/null && echo "PORT $p"
done
"""
stdin, stdout, stderr = client.exec_command(cmd)
print("STDOUT:", stdout.read().decode())
print("STDERR:", stderr.read().decode())
client.close()
