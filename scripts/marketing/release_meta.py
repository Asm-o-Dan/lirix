#!/usr/bin/env python3
"""
Lirix Release Metadata Extractor
Extracts git info, version tags, APK binary size, and SHA-256 for promotional drafts.
"""

import os
import sys
import json
import hashlib
import subprocess
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
APK_PATH = REPO_ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"

def get_git_info():
    def run_cmd(cmd):
        try:
            return subprocess.check_output(cmd, cwd=REPO_ROOT, shell=True, text=True).strip()
        except Exception:
            return ""

    commit_hash = run_cmd("git rev-parse --short HEAD") or "local"
    commit_count = run_cmd("git rev-list --count HEAD") or "100"
    last_tag = run_cmd("git describe --tags --abbrev=0") or "v1.0.1"
    recent_commits = run_cmd(f"git log -n 5 --pretty=format:\"* %s (%h)\"")
    
    return {
        "commit_hash": commit_hash,
        "commit_count": commit_count,
        "last_tag": last_tag,
        "recent_commits": recent_commits
    }

def get_apk_info():
    if not APK_PATH.exists():
        return {
            "exists": False,
            "path": str(APK_PATH),
            "size_mb": 0.0,
            "sha256": ""
        }
    
    size_bytes = APK_PATH.stat().st_size
    size_mb = round(size_bytes / (1024 * 1024), 2)
    
    h = hashlib.sha256()
    with open(APK_PATH, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    
    return {
        "exists": True,
        "path": str(APK_PATH),
        "size_mb": size_mb,
        "size_bytes": size_bytes,
        "sha256": h.hexdigest()
    }

def collect_release_meta(version="v1.1.0"):
    git_info = get_git_info()
    apk_info = get_apk_info()
    
    data = {
        "app_name": "Lirix",
        "version": version,
        "package_name": "com.eventengine.app",
        "repo_url": "https://github.com/Asm-o-Dan/lirix",
        "git": git_info,
        "apk": apk_info,
        "key_features": [
            "Плавающий оверлей поверх Spotify, VK и YouTube (SYSTEM_ALERT_WINDOW) с магнитным прилипанием к краям",
            "Сворачиваемый винил (+200dp места под текст) и кинетический тонарм с иглой на Compose Canvas",
            "Парсер аккордов с независимым транспонированием (±1..±11 полутонов) и интерактивными аппликатурами на грифе",
            "Автономный офлайн-кэш текстов и умная автопрокрутка по BPM и длине строк",
            "Scraper Community: экспорт и импорт правил извлечения текстов в JSON с проверкой SHA-256",
            "Zero-telemetry, zero-ads, AMOLED Black интерфейс"
        ]
    }
    return data

if __name__ == "__main__":
    meta = collect_release_meta()
    print(json.dumps(meta, ensure_ascii=False, indent=2))
