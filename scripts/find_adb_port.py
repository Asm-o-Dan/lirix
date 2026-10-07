import paramiko

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect('127.0.0.1', port=8822, username='u0_a389', password='20060305dan', timeout=15)

cmd = 'for p in $(seq 35000 45000); do (echo >/dev/tcp/127.0.0.1/$p) 2>/dev/null && echo "PORT $p"; done'
stdin, stdout, stderr = client.exec_command(cmd)
print(stdout.read().decode())

client.close()
