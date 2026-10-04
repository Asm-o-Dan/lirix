#!/usr/bin/env python3
"""
Master Marketing Bundle Generator for Lirix
Assembles staged ready-to-review promotional drafts for Habr, 4PDA, Reddit, and Telegram.
"""

import os
import sys
import json
from pathlib import Path

# Add script directory to sys.path
SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[1]
sys.path.insert(0, str(SCRIPT_DIR))

from release_meta import collect_release_meta

TEMPLATES_DIR = SCRIPT_DIR / "templates"
STAGE_DIR = REPO_ROOT / ".marketing" / "staged"

def render_template(template_file, data):
    content = template_file.read_text(encoding="utf-8")
    
    # Format placeholders
    content = content.replace("{version}", data.get("version", "v1.1.0"))
    content = content.replace("{app_name}", data.get("app_name", "Lirix"))
    content = content.replace("{repo_url}", data.get("repo_url", "https://github.com/Asm-o-Dan/lirix"))
    
    apk_info = data.get("apk", {})
    content = content.replace("{size_mb}", str(apk_info.get("size_mb", 16.5)))
    content = content.replace("{sha256}", apk_info.get("sha256", "pending_build"))
    
    return content

def generate_marketing_bundle(version="v1.1.0"):
    print(f"[*] Collecting metadata for release {version}...")
    meta = collect_release_meta(version)
    
    target_dir = STAGE_DIR / version
    target_dir.mkdir(parents=True, exist_ok=True)
    
    # Save metadata.json
    (target_dir / "metadata.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    
    # 1. Habr
    habr_tpl = TEMPLATES_DIR / "habr_article.md"
    if habr_tpl.exists():
        rendered = render_template(habr_tpl, meta)
        (target_dir / "habr_article.md").write_text(rendered, encoding="utf-8")
        print(f"  [+] Generated Habr draft: {target_dir / 'habr_article.md'}")
        
    # 2. 4PDA
    pda_tpl = TEMPLATES_DIR / "pda_topic.bbcode"
    if pda_tpl.exists():
        rendered = render_template(pda_tpl, meta)
        (target_dir / "4pda_release.bbcode").write_text(rendered, encoding="utf-8")
        print(f"  [+] Generated 4PDA BBCode: {target_dir / '4pda_release.bbcode'}")
        
    # 3. Reddit
    reddit_tpl = TEMPLATES_DIR / "reddit_posts.md"
    if reddit_tpl.exists():
        rendered = render_template(reddit_tpl, meta)
        (target_dir / "reddit_posts.md").write_text(rendered, encoding="utf-8")
        print(f"  [+] Generated Reddit pitches: {target_dir / 'reddit_posts.md'}")
        
    # 4. Telegram
    tg_tpl = TEMPLATES_DIR / "telegram_post.md"
    if tg_tpl.exists():
        rendered = render_template(tg_tpl, meta)
        (target_dir / "telegram_post.html").write_text(rendered, encoding="utf-8")
        print(f"  [+] Generated Telegram post: {target_dir / 'telegram_post.html'}")
        
    print(f"\n[SUCCESS] Staged marketing bundle generated at: {target_dir}")
    return target_dir

if __name__ == "__main__":
    version_arg = sys.argv[1] if len(sys.argv) > 1 else "v1.1.0"
    generate_marketing_bundle(version_arg)
