import paramiko

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect('127.0.0.1', port=8822, username='u0_a389', password='20060305dan', timeout=15)

# Fast scan using nc in batches
cmd = '''
for start in $(seq 35000 100 45000); do
    for p in $(seq $start $((start+99))); do
        nc -z -w 1 127.0.0.1 $p 2>/dev/null && echo "OPEN $p" &
    done
    wait
done
'''
stdin, stdout, stderr = client.exec_command(cmd)
print("Scanning result:")
print(stdout.read().decode())
client.close()
