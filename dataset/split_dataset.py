#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Stratified Train/Holdout Dataset Splitter for Android Event Engine (Sprint 1).
Splits dataset/golden_dataset.json into:
  - dataset/train.json (80% of each category)
  - dataset/holdout_eval.json (20% of each category)
Uses fixed random_seed=42.
Ensures zero data leakage: train and holdout sample IDs are mutually exclusive.
"""

import json
import os
import random
import sys
from collections import defaultdict

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

def split_dataset(golden_path, train_ratio=0.8, seed=42):
    random.seed(seed)
    
    if not os.path.exists(golden_path):
        print(f"[FAIL] Golden dataset file not found: {golden_path}")
        return False
        
    with open(golden_path, "r", encoding="utf-8") as f:
        data = json.load(f)
        
    samples = data.get("samples", [])
    categories = data.get("target_categories", [])
    
    print(f"=== Stratified Train/Holdout Dataset Splitter ===")
    print(f"Total samples: {len(samples)}")
    print(f"Train ratio: {train_ratio*100:.0f}% | Holdout ratio: {(1-train_ratio)*100:.0f}% | Seed: {seed}")
    
    # Group samples by category
    by_category = defaultdict(list)
    for s in samples:
        by_category[s["category"]].append(s)
        
    train_samples = []
    holdout_samples = []
    
    # Mandatory holdout samples specified by evaluation / device validation requirements
    mandatory_holdout_ids = {"poco_sms_001", "poco_prisbank_001"}

    print("\n[Per-Category Stratification]")
    for cat in categories:
        cat_samples = by_category[cat]
        # Separate mandatory holdout items if present
        forced_holdout = [s for s in cat_samples if s["id"] in mandatory_holdout_ids]
        remaining = [s for s in cat_samples if s["id"] not in mandatory_holdout_ids]

        # Shuffle remaining deterministically
        shuffled = list(remaining)
        random.shuffle(shuffled)
        
        target_holdout_count = int(round(len(cat_samples) * (1 - train_ratio)))
        needed_for_holdout = max(0, target_holdout_count - len(forced_holdout))
        
        cat_holdout = forced_holdout + shuffled[:needed_for_holdout]
        cat_train = shuffled[needed_for_holdout:]
        
        train_samples.extend(cat_train)
        holdout_samples.extend(cat_holdout)
        
        print(f"  {cat:<26}: Total {len(cat_samples):>3} -> Train {len(cat_train):>3} | Holdout {len(cat_holdout):>3}")
        
    # Check zero data leakage
    train_ids = set(s["id"] for s in train_samples)
    holdout_ids = set(s["id"] for s in holdout_samples)
    intersection = train_ids.intersection(holdout_ids)
    
    if intersection:
        print(f"\n[FAIL] Critical Data Leakage! {len(intersection)} IDs found in both train and holdout: {intersection}")
        return False
        
    print(f"\nZero Data Leakage confirmed: Intersection = 0 IDs.")
    print(f"Final Train Set:   {len(train_samples)} samples ({len(train_samples)/len(samples)*100:.1f}%)")
    print(f"Final Holdout Set: {len(holdout_samples)} samples ({len(holdout_samples)/len(samples)*100:.1f}%)")
    
    base_dir = os.path.dirname(golden_path)
    train_file = os.path.join(base_dir, "train.json")
    holdout_file = os.path.join(base_dir, "holdout_eval.json")
    
    train_obj = {
        "version": data.get("version", "1.1.0"),
        "description": "Android Event Engine Sprint 1 Train Set (80% Stratified)",
        "target_categories": categories,
        "samples": train_samples
    }
    
    holdout_obj = {
        "version": data.get("version", "1.1.0"),
        "description": "Android Event Engine Sprint 1 Holdout Evaluation Set (20% Stratified)",
        "target_categories": categories,
        "samples": holdout_samples
    }
    
    with open(train_file, "w", encoding="utf-8") as f:
        json.dump(train_obj, f, ensure_ascii=False, indent=2)
        
    with open(holdout_file, "w", encoding="utf-8") as f:
        json.dump(holdout_obj, f, ensure_ascii=False, indent=2)
        
    print(f"\n[SUCCESS] Train set written to: {train_file}")
    print(f"[SUCCESS] Holdout set written to: {holdout_file}")
    return True

if __name__ == "__main__":
    base_dir = os.path.dirname(os.path.abspath(__file__))
    golden_file = os.path.join(base_dir, "golden_dataset.json")
    success = split_dataset(golden_file)
    sys.exit(0 if success else 1)
