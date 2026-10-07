import socket
import concurrent.futures

def check_ip(ip):
    for port in [5555, 8022]:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(0.2)
            if s.connect_ex((ip, port)) == 0:
                print(f"FOUND: {ip}:{port}")
                s.close()
                return (ip, port)
            s.close()
        except:
            pass
    return None

base = "192.168.88."
ips = [f"{base}{i}" for i in range(2, 255) if i != 236]
with concurrent.futures.ThreadPoolExecutor(max_workers=60) as executor:
    results = list(executor.map(check_ip, ips))

print("Scan complete.")
