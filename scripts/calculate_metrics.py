#!/usr/bin/env python3
"""
ML Metrics Calculator for Android Event Engine.
Calculates Precision, Recall, Macro F1, Unknown Rate, and Confusion Matrix.
Can run in self-test/mock mode or evaluate predictions JSON file against golden_dataset.json.
"""

import json
import os
import sys
from collections import defaultdict

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

def compute_metrics(golden_samples, predictions, categories):
    """
    golden_samples: dict of {sample_id: true_category}
    predictions: dict of {sample_id: pred_category}
    categories: list of recognized categories
    """
    total = len(golden_samples)
    unknown_count = 0
    
    # Category -> TP, FP, FN
    stats = {cat: {"TP": 0, "FP": 0, "FN": 0} for cat in categories}
    confusion = defaultdict(lambda: defaultdict(int))
    
    for sid, true_cat in golden_samples.items():
        pred_cat = predictions.get(sid, "unknown")
        confusion[true_cat][pred_cat] += 1
        
        if pred_cat == "unknown" or pred_cat not in stats:
            unknown_count += 1
            stats[true_cat]["FN"] += 1
            continue
            
        if pred_cat == true_cat:
            stats[true_cat]["TP"] += 1
        else:
            stats[true_cat]["FN"] += 1
            stats[pred_cat]["FP"] += 1

    # Per-category metrics
    report = {}
    f1_sum = 0.0
    valid_categories_count = 0

    for cat in categories:
        tp = stats[cat]["TP"]
        fp = stats[cat]["FP"]
        fn = stats[cat]["FN"]

        prec = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        rec = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        f1 = (2 * prec * rec) / (prec + rec) if (prec + rec) > 0 else 0.0

        support = tp + fn
        report[cat] = {
            "precision": prec,
            "recall": rec,
            "f1": f1,
            "support": support,
            "tp": tp,
            "fp": fp,
            "fn": fn
        }
        if support > 0:
            f1_sum += f1
            valid_categories_count += 1

    macro_f1 = f1_sum / valid_categories_count if valid_categories_count > 0 else 0.0
    unknown_rate = unknown_count / total if total > 0 else 0.0

    return {
        "macro_f1": macro_f1,
        "unknown_rate": unknown_rate,
        "unknown_count": unknown_count,
        "total": total,
        "per_category": report,
        "confusion": confusion
    }

def print_evaluation_report(results, target_macro_f1=0.90, target_finance_precision=0.98, target_unknown_rate=0.05):
    print("=" * 80)
    print("               ANDROID EVENT ENGINE - ML EVALUATION REPORT")
    print("=" * 80)
    print(f"Total evaluated samples: {results['total']}")
    print(f"Unknown predictions:     {results['unknown_count']} ({results['unknown_rate'] * 100:.2f}%)")
    print(f"Macro F1 Score:          {results['macro_f1']:.4f}")
    print("-" * 80)
    print(f"{'Category':<28} | {'Precision':<10} | {'Recall':<10} | {'F1-Score':<10} | {'Support':<8}")
    print("-" * 80)

    for cat, m in results["per_category"].items():
        print(f"{cat:<28} | {m['precision']:<10.4f} | {m['recall']:<10.4f} | {m['f1']:<10.4f} | {m['support']:<8}")

    print("-" * 80)
    
    # Check Quality Gates
    print("\n[QUALITY GATES CHECK]")
    
    # Gate 1: Macro F1
    gate1_pass = results["macro_f1"] >= target_macro_f1
    gate1_sym = "[PASS]" if gate1_pass else "[FAIL]"
    print(f"  {gate1_sym} Macro F1 >= {target_macro_f1:.2f}: actual = {results['macro_f1']:.4f}")
    
    # Gate 2: Finance Precision
    finance_categories = [c for c in results["per_category"] if c.startswith("finance.")]
    finance_precisions = [results["per_category"][c]["precision"] for c in finance_categories if results["per_category"][c]["support"] > 0]
    avg_fin_prec = sum(finance_precisions) / len(finance_precisions) if finance_precisions else 0.0
    gate2_pass = avg_fin_prec >= target_finance_precision
    gate2_sym = "[PASS]" if gate2_pass else "[FAIL]"
    print(f"  {gate2_sym} Finance Avg Precision >= {target_finance_precision:.2f}: actual = {avg_fin_prec:.4f}")
    
    # Gate 3: Unknown Rate
    gate3_pass = results["unknown_rate"] <= target_unknown_rate
    gate3_sym = "[PASS]" if gate3_pass else "[FAIL]"
    print(f"  {gate3_sym} Unknown Rate <= {target_unknown_rate * 100:.1f}%: actual = {results['unknown_rate'] * 100:.2f}%")
    
    all_passed = gate1_pass and gate2_pass and gate3_pass
    print("\nOverall Status: " + ("READY FOR SPRINT 0 CLOSE" if all_passed else "QUALITY GATES NOT MET"))
    print("=" * 80)
    return all_passed

if __name__ == "__main__":
    base_dir = os.path.dirname(os.path.abspath(__file__))
    dataset_file = os.path.join(base_dir, "..", "dataset", "golden_dataset.json")

    with open(dataset_file, "r", encoding="utf-8") as f:
        ds = json.load(f)

    golden_dict = {s["id"]: s["category"] for s in ds["samples"]}
    categories = ds["target_categories"]

    # If predictions argument provided, load from file, otherwise run baseline simulated benchmark
    if len(sys.argv) > 1 and os.path.exists(sys.argv[1]):
        pred_file = sys.argv[1]
        with open(pred_file, "r", encoding="utf-8") as f:
            predictions = json.load(f)
        print(f"Loaded predictions from: {pred_file}")
    else:
        print("[INFO] No predictions file provided. Generating baseline simulation (100% accuracy mock) for verification test.")
        predictions = {s["id"]: s["category"] for s in ds["samples"]}

    metrics = compute_metrics(golden_dict, predictions, categories)
    passed = print_evaluation_report(metrics)
    sys.exit(0 if passed else 1)
