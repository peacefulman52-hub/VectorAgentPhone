#!/usr/bin/env python3
"""Exploratory confidence-calibration test with causal residual-stream perturbations."""
import json, math, os, random, statistics, time
from datetime import datetime, timezone
from pathlib import Path
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "results"
OUT.mkdir(parents=True, exist_ok=True)
SEED = 7319
MODEL_ID = os.environ.get("ENTROPY_MODEL_ID", "distilgpt2")
LAYER_INDEX = 5  # final GPT-2 block, so the intervention is close to next-token logits
random.seed(SEED)
torch.manual_seed(SEED)
torch.set_num_threads(2)

ANTONYMS = [
    ("hot", "cold"), ("up", "down"), ("dark", "light"), ("night", "day"),
    ("good", "bad"), ("big", "small"), ("old", "new"), ("happy", "sad"),
    ("hard", "soft"), ("fast", "slow"), ("high", "low"), ("left", "right"),
    ("wet", "dry"), ("full", "empty"), ("rich", "poor"), ("clean", "dirty"),
    ("early", "late"), ("loud", "quiet"), ("open", "closed"), ("strong", "weak"),
    ("long", "short"), ("warm", "cool"), ("near", "far"), ("young", "old"),
]

def percentile(values, q):
    s = sorted(values)
    if not s: return None
    pos = (len(s)-1)*q
    lo, hi = math.floor(pos), math.ceil(pos)
    return float(s[lo]) if lo == hi else float(s[lo]*(hi-pos)+s[hi]*(pos-lo))

def bootstrap_ci(values, seed=SEED, reps=2000):
    if not values: return [None, None]
    rng = random.Random(seed)
    n = len(values)
    means = [statistics.mean(values[rng.randrange(n)] for _ in range(n)) for _ in range(reps)]
    return [percentile(means, .025), percentile(means, .975)]

def summarize(rows):
    n = len(rows)
    accuracy = statistics.mean(float(r["correct"]) for r in rows)
    mean_p_correct = statistics.mean(r["p_correct"] for r in rows)
    brier = statistics.mean((r["p_correct"] - float(r["correct"]))**2 for r in rows)
    ece = 0.0
    for i in range(10):
        bucket = [r for r in rows if i/10 <= r["p_correct"] < (i+1)/10 or (i == 9 and r["p_correct"] == 1.0)]
        if bucket:
            ece += len(bucket)/n * abs(statistics.mean(float(r["correct"]) for r in bucket) - statistics.mean(r["p_correct"] for r in bucket))
    return {"n": n, "accuracy": accuracy, "mean_probability_correct": mean_p_correct,
            "brier_score": brier, "expected_calibration_error_10_bins": ece}

def main():
    started = time.time()
    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
    model = AutoModelForCausalLM.from_pretrained(MODEL_ID).to("cpu")
    model.eval()

    tasks, skipped = [], []
    for left, right in ANTONYMS:
        for word, correct in ((left, right), (right, left)):
            wrong_ids = tokenizer.encode(" " + word, add_special_tokens=False)
            correct_ids = tokenizer.encode(" " + correct, add_special_tokens=False)
            if len(wrong_ids) != 1 or len(correct_ids) != 1:
                skipped.append({"word": word, "candidate": correct, "reason": "candidate is not a single token"})
                continue
            prompt = f"The opposite of {word} is"
            ids = tokenizer(prompt, return_tensors="pt").input_ids
            tasks.append({"id": len(tasks), "word": word, "correct_word": correct,
                          "wrong_word": word, "correct_id": correct_ids[0],
                          "wrong_id": wrong_ids[0], "prompt": prompt, "input_ids": ids})

    if len(tasks) < 20:
        raise RuntimeError(f"Too few single-token antonym tasks: {len(tasks)}")
    random.Random(SEED).shuffle(tasks)
    tasks = tasks[:40]
    hidden_norms = []
    baseline_cache = {}

    with torch.no_grad():
        for task in tasks:
            captured = []
            def capture(module, inputs, output):
                h = output[0] if isinstance(output, tuple) else output
                captured.append(h[0, -1, :].detach().float())
            handle = model.transformer.h[LAYER_INDEX].register_forward_hook(capture)
            logits = model(input_ids=task["input_ids"]).logits[0, -1, :].float()
            handle.remove()
            p = torch.softmax(torch.stack([logits[task["correct_id"]], logits[task["wrong_id"]]]), dim=0)
            task["baseline_p_correct"] = float(p[0].item())
            task["baseline_correct"] = bool(p[0] > p[1])
            task["baseline_top_id"] = task["correct_id"] if p[0] >= p[1] else task["wrong_id"]
            task["baseline_other_id"] = task["wrong_id"] if p[0] >= p[1] else task["correct_id"]
            task["activation_norm"] = float(captured[0].norm().item())
            hidden_norms.append(task["activation_norm"])
            baseline_cache[task["id"]] = (task["baseline_p_correct"], task["baseline_correct"])

    mean_activation_norm = statistics.mean(hidden_norms)
    delta_norm = 0.05 * mean_activation_norm
    rows_by_condition = {k: [] for k in (
        "no_intervention", "targeted_confidence_reduction",
        "random_same_norm_activation_intervention", "prompt_only_warning")}
    raw = []

    for task in tasks:
        ids = task["input_ids"]
        c_id, w_id = task["correct_id"], task["wrong_id"]
        top_id, other_id = task["baseline_top_id"], task["baseline_other_id"]
        # Target the baseline winner without using the ground-truth label: push against its
        # unembedding direction. This is a task-specific adversarial perturbation.
        direction = (model.lm_head.weight[top_id] - model.lm_head.weight[other_id]).detach().float()
        direction = direction / direction.norm().clamp_min(1e-12)
        target_delta = (-direction * delta_norm).to(dtype=model.transformer.h[LAYER_INDEX].ln_1.weight.dtype)
        gen = torch.Generator(device="cpu").manual_seed(SEED + task["id"] + 1)
        random_delta = torch.randn(direction.shape, generator=gen, dtype=torch.float32)
        random_delta = (random_delta / random_delta.norm().clamp_min(1e-12) * delta_norm).to(target_delta.dtype)

        conditions = [
            ("no_intervention", None, ids),
            ("targeted_confidence_reduction", target_delta, ids),
            ("random_same_norm_activation_intervention", random_delta, ids),
        ]
        warned = task["prompt"].replace("The opposite of", "The opposite of")
        warned += " (the model may be uncertain about this answer)"
        conditions.append(("prompt_only_warning", None, tokenizer(warned, return_tensors="pt").input_ids))

        for condition, delta, input_ids in conditions:
            def intervene(module, inputs, output):
                if delta is None: return output
                is_tuple = isinstance(output, tuple)
                h = output[0] if is_tuple else output
                changed = h.clone()
                changed[:, -1, :] += delta.to(device=changed.device, dtype=changed.dtype)
                return (changed,) + output[1:] if is_tuple else changed
            handle = model.transformer.h[LAYER_INDEX].register_forward_hook(intervene) if delta is not None else None
            with torch.no_grad():
                logits = model(input_ids=input_ids).logits[0, -1, :].float()
                pair = torch.stack([logits[c_id], logits[w_id]])
                probs = torch.softmax(pair, dim=0)
            if handle is not None: handle.remove()
            p_correct = float(probs[0].item())
            row = {
                "task_id": task["id"], "prompt": task["prompt"], "word": task["word"],
                "correct_candidate": task["correct_word"], "incorrect_candidate": task["wrong_word"],
                "condition": condition, "p_correct": p_correct, "correct": p_correct >= 0.5,
                "baseline_p_correct": task["baseline_p_correct"],
                "activation_perturbation_norm": delta_norm if delta is not None else 0.0
            }
            rows_by_condition[condition].append(row)
            raw.append(row)

    summaries = {name: summarize(rows) for name, rows in rows_by_condition.items()}
    base = {r["task_id"]: r for r in rows_by_condition["no_intervention"]}
    paired = {}
    for name, rows in rows_by_condition.items():
        if name == "no_intervention": continue
        diffs = [float(r["correct"]) - float(base[r["task_id"]]["correct"]) for r in rows]
        paired[name + "_accuracy_difference_vs_baseline"] = {
            "mean_paired_difference": statistics.mean(diffs),
            "bootstrap_95_percentile_CI": bootstrap_ci(diffs)
        }

    report = {
        "protocol_id": "entropy-introspection-pilot-001",
        "run_status": "COMPLETED_EXPLORATORY_PILOT",
        "model": MODEL_ID, "device": "cpu", "seed": SEED,
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "task": "next-token likelihood comparison on antonym completions",
        "layer_index_zero_based": LAYER_INDEX,
        "mean_layer_activation_norm": mean_activation_norm,
        "targeted_perturbation_norm": delta_norm,
        "conditions": summaries, "paired_accuracy_differences": paired,
        "n_tasks": len(tasks), "skipped_candidates": skipped,
        "raw_results": raw,
        "interpretation_limit": (
            "This measures probability calibration on a narrow lexical completion task. "
            "The targeted perturbation pushes against the model's baseline winning candidate without using the correct label. "
            "It is not a direct measure of verbal self-report, subjective experience, or general introspection."
        ),
        "runtime": {"elapsed_seconds": round(time.time()-started, 2),
                    "torch": torch.__version__, "transformers": __import__("transformers").__version__}
    }
    out = OUT / "model_experiment.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k:v for k,v in report.items() if k != "raw_results"}, ensure_ascii=False, indent=2))
    print(f"Raw per-item data: {out}")

if __name__ == "__main__":
    main()
