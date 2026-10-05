import sys
import paramiko

def run_on_phone(cmd: str):
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        client.connect("127.0.0.1", port=8822, username="u0_a389", password="20060305dan", timeout=10)
        stdin, stdout, stderr = client.exec_command(cmd)
        out = stdout.read().decode()
        err = stderr.read().decode()
        if out:
            print(out, end="")
        if err:
            print(err, file=sys.stderr, end="")
        client.close()
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) > 1:
        run_on_phone(" ".join(sys.argv[1:]))
    else:
        run_on_phone("uname -a")
