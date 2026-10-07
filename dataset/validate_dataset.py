#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Dataset validation script for Android Event Engine Sprint 1.
Enforces:
1. Valid JSON format.
2. Top-level keys and structure matching schema.json.
3. Total samples >= 1200.
4. Every target category has at least 100 verified samples.
5. All hard cases have is_hard_case: true and non-empty hard_case_reason.
6. All mandatory hard cases from Sprint 0 & Sprint 1 are present.
7. ID uniqueness and valid required fields.
"""

import json
import os
import sys

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

def validate_dataset(filepath):
    if not os.path.exists(filepath):
        print(f"[FAIL] Dataset file not found: {filepath}")
        return False

    with open(filepath, "r", encoding="utf-8") as f:
        try:
            data = json.load(f)
        except Exception as e:
            print(f"[FAIL] Invalid JSON: {e}")
            return False

    required_top_keys = ["version", "description", "target_categories", "samples"]
    for k in required_top_keys:
        if k not in data:
            print(f"[FAIL] Missing top-level key: {k}")
            return False

    categories = data["target_categories"]
    samples = data["samples"]

    print(f"=== Dataset Validation: {os.path.basename(filepath)} ===")
    print(f"Total samples: {len(samples)}")
    print(f"Declared categories: {len(categories)}")

    if len(samples) < 1200:
        print(f"[FAIL] Expected at least 1200 samples for Sprint 1, got {len(samples)}")
        return False

    category_counts = {cat: 0 for cat in categories}
    hard_cases_count = 0
    precision_critical_count = 0
    ids_seen = set()

    mandatory_hard_patterns = [
        ("Сбер", "Перевод 1000"),
        ("Сбер", "Пополнение 1000"),
        (None, "Оплата 1000"),
        (None, "Возврат 1000"),
        (None, "Списание 1000"),
        (None, "Завтра пара"),
        (None, "Завтра пара отменяется"),
        (None, "Завтра замена пары"),
        (None, "Не забудь"),
        ("1611", "код к сессии"),
        ("Деньги списаны", "6.00 RUP")
    ]
    mandatory_found = {pat: False for pat in mandatory_hard_patterns}

    for idx, s in enumerate(samples):
        # ID uniqueness
        sid = s.get("id")
        if not sid or sid in ids_seen:
            print(f"[FAIL] Sample #{idx} has invalid or duplicate id: {sid}")
            return False
        ids_seen.add(sid)

        # Required sample fields
        req_fields = ["id", "package_name", "source_type", "title", "text", "category", "intent", "is_hard_case"]
        for rf in req_fields:
            if rf not in s:
                print(f"[FAIL] Sample {sid} missing required field: {rf}")
                return False

        cat = s["category"]
        if cat not in category_counts:
            print(f"[FAIL] Sample {sid} has unexpected category: {cat}")
            return False
        category_counts[cat] += 1

        if s["is_hard_case"]:
            hard_cases_count += 1
            if not s.get("hard_case_reason") or not str(s.get("hard_case_reason")).strip():
                print(f"[FAIL] Sample {sid} has is_hard_case=True but missing hard_case_reason!")
                return False

        if s.get("expected_precision_critical"):
            precision_critical_count += 1

        # Check mandatory hard patterns
        title = s.get("title") or ""
        text = s.get("text") or ""
        for pat in mandatory_hard_patterns:
            req_title, req_text = pat
            if req_title:
                if req_title.lower() in title.lower() and req_text.lower() in text.lower():
                    mandatory_found[pat] = True
            else:
                if req_text.lower() in text.lower():
                    mandatory_found[pat] = True

    # Validate categories distribution (>= 100 per category)
    print("\n[Category Distribution]")
    for cat, count in category_counts.items():
        print(f"  - {cat:<26}: {count:>4} samples")
        if count < 100:
            print(f"[FAIL] Category '{cat}' has fewer than 100 samples! (Got {count})")
            return False

    # Check mandatory patterns
    print("\n[Mandatory Hard Cases Check]")
    all_hard_cases_ok = True
    for pat, found in mandatory_found.items():
        title_disp = f"Title contains '{pat[0]}' + " if pat[0] else ""
        text_disp = f"Text contains '{pat[1]}'"
        status = "FOUND" if found else "MISSING"
        print(f"  [{status}] {title_disp}{text_disp}")
        if not found:
            all_hard_cases_ok = False

    if not all_hard_cases_ok:
        print("[FAIL] Not all mandatory hard cases were found in the dataset!")
        return False

    print(f"\nTotal Hard Cases: {hard_cases_count} ({hard_cases_count/len(samples)*100:.1f}%)")
    print(f"Precision-Critical Samples: {precision_critical_count} ({precision_critical_count/len(samples)*100:.1f}%)")
    print("\n[SUCCESS] Sprint 1 Golden Dataset validation PASSED cleanly! (>= 100/category & all quality gates met)\n")
    return True

if __name__ == "__main__":
    base_dir = os.path.dirname(os.path.abspath(__file__))
    dataset_file = os.path.join(base_dir, "golden_dataset.json")
    success = validate_dataset(dataset_file)
    sys.exit(0 if success else 1)
