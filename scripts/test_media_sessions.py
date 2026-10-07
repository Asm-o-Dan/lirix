import paramiko

def check():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect('127.0.0.1', port=8822, username='u0_a389', password='20060305dan', timeout=15)
    
    stdin, stdout, stderr = client.exec_command('adb shell "dumpsys media_session"')
    full = stdout.read().decode()
    for line in full.splitlines():
        if any(k in line for k in ["package=", "state=PlaybackState", "description="]):
            print(line.strip())
            
    client.close()

if __name__ == "__main__":
    check()
