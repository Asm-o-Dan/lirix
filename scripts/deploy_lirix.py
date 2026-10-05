import os
import sys
import paramiko
import time

def deploy():
    apk_local = os.path.abspath("lirix-v1.1.2.apk")
    if not os.path.exists(apk_local):
        print(f"Error: {apk_local} does not exist!")
        return

    apk_remote = "/data/data/com.termux/files/home/lirix-v1.1.2.apk"

    print(f"1. Connecting SSH/SFTP to phone...")
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect("127.0.0.1", port=8822, username="u0_a389", password="20060305dan", timeout=15)

    sftp = client.open_sftp()
    print(f"2. Uploading {apk_local} ({os.path.getsize(apk_local)} bytes) -> {apk_remote}...")
    sftp.put(apk_local, apk_remote)
    sftp.close()
    print("   Upload finished successfully.")

    def exec_cmd(cmd):
        print(f"--> {cmd}")
        stdin, stdout, stderr = client.exec_command(cmd)
        out = stdout.read().decode().strip()
        err = stderr.read().decode().strip()
        if out:
            print(f"    {out}")
        if err:
            print(f"    [ERR] {err}")
        return out

    # Check ADB status
    exec_cmd("adb devices")

    # Install APK
    print("3. Installing APK via ADB...")
    exec_cmd(f"adb install -r -g {apk_remote}")

    # Verify package
    print("4. Verifying package com.lirix.app...")
    exec_cmd("adb shell pm list packages | grep lirix")

    # Grant permissions
    print("5. Granting permissions...")
    exec_cmd("adb shell pm grant com.lirix.app android.permission.POST_NOTIFICATIONS")
    exec_cmd("adb shell cmd notification set_listener_access_granted com.lirix.app/com.lirix.app.ingestion.NotificationListener true")

    # Start MainActivity
    print("6. Launching com.lirix.app/.MainActivity...")
    exec_cmd("adb shell am start -n com.lirix.app/.MainActivity")

    # Wait 2 seconds and take screenshot
    time.sleep(2)
    print("7. Taking screenshot of device screen...")
    exec_cmd("adb shell screencap -p /sdcard/screen_lirix.png")

    sftp = client.open_sftp()
    sftp.get("/sdcard/screen_lirix.png", os.path.abspath("screen_lirix.png"))
    sftp.close()
    print("   Screenshot saved to screen_lirix.png")

    client.close()
    print("\n[SUCCESS] Lirix v1.1.2 deployed, permissions granted, and started!")

if __name__ == "__main__":
    deploy()
