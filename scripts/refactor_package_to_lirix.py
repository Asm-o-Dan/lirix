import os
import subprocess
import shutil

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

def run_cmd(cmd):
    print(f"Running: {' '.join(cmd)}")
    res = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"STDERR: {res.stderr}")
        raise RuntimeError(f"Command failed: {cmd}")
    return res.stdout

def main():
    print(f"Refactoring package from com.eventengine.app to com.lirix.app in {ROOT}...")

    # 1. Ensure target dirs exist
    os.makedirs(os.path.join(ROOT, "app/src/main/java/com/lirix"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "app/src/test/java/com/lirix"), exist_ok=True)

    # 2. git mv main sources
    main_src = os.path.join(ROOT, "app/src/main/java/com/eventengine/app")
    main_dst = os.path.join(ROOT, "app/src/main/java/com/lirix/app")
    if os.path.exists(main_src) and not os.path.exists(main_dst):
        run_cmd(["git", "mv", "app/src/main/java/com/eventengine/app", "app/src/main/java/com/lirix/app"])
        # remove old empty com/eventengine if empty
        old_parent = os.path.join(ROOT, "app/src/main/java/com/eventengine")
        if os.path.exists(old_parent) and not os.listdir(old_parent):
            os.rmdir(old_parent)

    # 3. git mv test sources
    test_src = os.path.join(ROOT, "app/src/test/java/com/eventengine/app")
    test_dst = os.path.join(ROOT, "app/src/test/java/com/lirix/app")
    if os.path.exists(test_src) and not os.path.exists(test_dst):
        run_cmd(["git", "mv", "app/src/test/java/com/eventengine/app", "app/src/test/java/com/lirix/app"])
        old_test_parent = os.path.join(ROOT, "app/src/test/java/com/eventengine")
        if os.path.exists(old_test_parent) and not os.listdir(old_test_parent):
            os.rmdir(old_test_parent)

    # 4. Rename EventEngineApp.kt -> LirixApp.kt
    app_kt = os.path.join(ROOT, "app/src/main/java/com/lirix/app/EventEngineApp.kt")
    lirix_kt = os.path.join(ROOT, "app/src/main/java/com/lirix/app/LirixApp.kt")
    if os.path.exists(app_kt) and not os.path.exists(lirix_kt):
        run_cmd(["git", "mv", "app/src/main/java/com/lirix/app/EventEngineApp.kt", "app/src/main/java/com/lirix/app/LirixApp.kt"])

    # 5. Replace occurrences in all files
    # Get all git tracked files
    tracked = run_cmd(["git", "ls-files"]).splitlines()

    text_extensions = (
        ".kt", ".java", ".xml", ".gradle", ".kts", ".pro", ".md", ".json", ".py", ".ps1", ".sh", ".yml", ".yaml", ".txt"
    )

    count_replaced = 0
    for rel_path in tracked:
        if not any(rel_path.endswith(ext) for ext in text_extensions):
            continue
        full_path = os.path.join(ROOT, rel_path)
        if not os.path.exists(full_path):
            continue
        
        try:
            with open(full_path, "r", encoding="utf-8") as f:
                content = f.read()
        except UnicodeDecodeError:
            continue

        new_content = content
        new_content = new_content.replace("com.eventengine.app", "com.lirix.app")
        new_content = new_content.replace("EventEngineApp", "LirixApp")
        new_content = new_content.replace("Theme.AndroidEventEngine", "Theme.Lirix")
        new_content = new_content.replace("Android Event Engine", "Lirix")

        if new_content != content:
            with open(full_path, "w", encoding="utf-8") as f:
                f.write(new_content)
            count_replaced += 1
            print(f"Updated {rel_path}")

    print(f"\nSuccessfully replaced package references in {count_replaced} files.")

if __name__ == "__main__":
    main()
