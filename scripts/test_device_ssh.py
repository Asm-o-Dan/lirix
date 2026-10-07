import paramiko
import sys

def test_connection():
    hostname = "127.0.0.1"
    port = 8822
    username = "u0_a389"
    password = "20060305dan"

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())

    print(f"Connecting to {username}@{hostname}:{port}...")
    try:
        client.connect(hostname, port=port, username=username, password=password, timeout=15)
        print("Connected successfully!")
        
        commands = [
            "whoami",
            "uname -a",
            "getprop ro.product.model",
            "getprop ro.build.version.release",
            "uptime"
        ]
        for cmd in commands:
            stdin, stdout, stderr = client.exec_command(cmd)
            out = stdout.read().decode().strip()
            err = stderr.read().decode().strip()
            print(f"$ {cmd}\n  {out if out else err}")
        
        client.close()
        return True
    except Exception as e:
        print(f"Failed to connect: {e}", file=sys.stderr)
        return False

if __name__ == "__main__":
    test_connection()
