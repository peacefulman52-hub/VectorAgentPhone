#!/usr/bin/env python3
"""Exploratory causal activation-calibration pilot; not a consciousness test."""
import json
import math
import os
import random
import statistics
import time
from datetime import datetime, timezone
from pathlib import Path

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "results"
OUT.mkdir(parents=True, exist_ok=True)
SEED = 7319
MODEL_ID = os.environ.get("ENTROPY_MODEL_ID", "distilgpt2")
LAYER_INDEX = 4
random.seed(SEED)
torch.manual_seed(SEED)
torch.set_num_threads(2)

def make_tasks(n=80):
    """Fixed-seed arithmetic two-choice task, balanced correct-option position."""
    rng = random.Random(SEED)
    tasks = []
    for i in range(n):
        a = rng.randint(2, 18)
        b = rng.randint(2, 18)
        answer = a + b
        wrong = answer + (1 if rng.random() < 0.5 else -1)
        correct_is_a = (i % 2 == 0)
        option_a, option_b = (answer, wrong) if correct_is_a else (wrong, answer)
        prompt = (
            f"Answer this arithmetic question. Choose the correct option.\n"
            f"Question: {a} + {b} = ?\n"
            f"A) {option_a}\nB) {option_b}\nAnswer:"
        )
        tasks.append({
            "id": i, "a": a, "b": b, "answer": answer,
            "option_a": option_a, "option_b": option_b,
            "correct_label": "A" if correct_is_a else "B",
            "prompt": prompt
        })
    return tasks

def percentile(values, q):
    s = sorted(values)
    if not s:
        return None
    pos = (len(s) - 1) * q
    lo, hi = math.floor(pos), math.ceil(pos)
    if lo == hi:
        return float(s[lo])
    return float(s[lo] * (hi - pos) + s[hi] * (pos - lo))

def bootstrap_mean_ci(values, seed=SEED, reps=2000):
    if not values:
        return [None, None]
    rng = random.Random(seed)
    n = len(values)
    means = []
    for _ in range(reps):
        means.append(statistics.mean(values[rng.randrange(n)] for _ in range(n)))
    return [percentile(means, 0.025), percentile(means, 0.975)]

def summarize(rows):
    n = len(rows)
    accuracy = statistics.mean(float(r["correct"]) for r in rows)
    mean_p_selected = statistics.mean(r["p_selected"] for r in rows)
    brier = statistics.mean((r["p_a"] - float(r["correct_label"] == "A")) ** 2 for r in rows)
    ece = 0.0
    for bin_idx in range(5):
        lo, hi = bin_idx / 5, (bin_idx + 1) / 5
        bucket = [r for r in rows if lo <= r["p_selected"] < hi or (bin_idx == 4 and r["p_selected"] == 1.0)]
        if bucket:
            ece += len(bucket) / n * abs(
                statistics.mean(float(x["correct"]) for x in bucket)
                - statistics.mean(x["p_selected"] for x in bucket)
            )
    return {
        "n": n, "accuracy": accuracy,
        "mean_probability_of_selected_option": mean_p_selected,
        "brier_score_for_A_vs_B": brier,
        "expected_calibration_error_5_bins": ece
    }

def main():
    started = time.time()
    device = "cpu"
    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
    model = AutoModelForCausalLM.from_pretrained(MODEL_ID).to(device)
    model.eval()
    token_a = tokenizer.encode(" A", add_special_tokens=False)
    token_b = tokenizer.encode(" B", add_special_tokens=False)
    if len(token_a) != 1 or len(token_b) != 1:
        raise RuntimeError(f"Expected single-token A/B choices; got A={token_a}, B={token_b}")
    id_a, id_b = token_a[0], token_b[0]
    if not hasattr(model, "transformer") or len(model.transformer.h) <= LAYER_INDEX:
        raise RuntimeError("Unexpected model architecture; expected GPT-2 style transformer blocks")

    # The target direction is explicit and task-specific: the unembedding contrast
    # between the next-token choices ' A' and ' B'. It is not a learned introspection vector.
    direction = (model.lm_head.weight[id_a] - model.lm_head.weight[id_b]).detach().float()
    direction = direction / direction.norm().clamp_min(1e-12)

    tasks = make_tasks()
    encoded = []
    activation_norms = []
    with torch.no_grad():
        for task in tasks:
            ids = tokenizer(task["prompt"], return_tensors="pt").input_ids.to(device)
            outputs = model(input_ids=ids)
            h = outputs.logits.new_tensor(0.0)
            # Measure the block output norm for a reproducible relative perturbation scale.
            def capture(module, inputs, output):
                out = output[0] if isinstance(output, tuple) else output
                activation_norms.append(float(out[0, -1, :].float().norm().item()))
            handle = model.transformer.h[LAYER_INDEX].register_forward_hook(capture)
            model(input_ids=ids)
            handle.remove()
            encoded.append((task, ids))
    mean_activation_norm = statistics.mean(activation_norms)
    delta_norm = 0.10 * mean_activation_norm
    target_delta = (direction * delta_norm).to(dtype=model.transformer.h[LAYER_INDEX].ln_1.weight.dtype if hasattr(model.transformer.h[LAYER_INDEX], "ln_1") else model.dtype)
    generator = torch.Generator(device="cpu").manual_seed(SEED + 1)
    random_delta = torch.randn(direction.shape, generator=generator, dtype=torch.float32)
    random_delta = (random_delta / random_delta.norm().clamp_min(1e-12) * delta_norm).to(target_delta.dtype)

    conditions = {
        "no_intervention": None,
        "targeted_activation_bias_to_option_A": target_delta,
        "random_same_norm_activation_intervention": random_delta,
        "prompt_only_warning": None,
    }
    all_rows = {}
    raw = []
    for condition, delta in conditions.items():
        rows = []
        for task, ids in encoded:
            input_ids = ids
            if condition == "prompt_only_warning":
                warning = "\nNote: an internal processing perturbation may bias the next choice."
                extra = tokenizer(warning, return_tensors="pt", add_special_tokens=False).input_ids
                input_ids = torch.cat([ids, extra], dim=1)
            def intervene(module, inputs, output):
                if delta is None:
                    return output
                is_tuple = isinstance(output, tuple)
                hidden = output[0] if is_tuple else output
                changed = hidden.clone()
                changed[:, -1, :] += delta.to(device=changed.device, dtype=changed.dtype)
                if is_tuple:
                    return (changed,) + output[1:]
                return changed
            handle = None
            if delta is not None:
                handle = model.transformer.h[LAYER_INDEX].register_forward_hook(intervene)
            with torch.no_grad():
                logits = model(input_ids=input_ids).logits[0, -1, :].float()
                pair_logits = torch.stack([logits[id_a], logits[id_b]])
                probs = torch.softmax(pair_logits, dim=0)
            if handle is not None:
                handle.remove()
            p_a, p_b = float(probs[0].item()), float(probs[1].item())
            predicted = "A" if p_a >= p_b else "B"
            p_selected = max(p_a, p_b)
            row = {
                "task_id": task["id"], "condition": condition,
                "correct_label": task["correct_label"], "predicted_label": predicted,
                "correct": predicted == task["correct_label"],
                "p_a": p_a, "p_b": p_b, "p_selected": p_selected,
                "question": f'{task["a"]} + {task["b"]} = {task["answer"]}',
                "option_a": task["option_a"], "option_b": task["option_b"]
            }
            rows.append(row)
            raw.append(row)
        all_rows[condition] = rows

    summaries = {name: summarize(rows) for name, rows in all_rows.items()}
    paired = {}
    base_by_id = {r["task_id"]: r for r in all_rows["no_intervention"]}
    for name, rows in all_rows.items():
        if name == "no_intervention":
            continue
        diffs = [float(r["correct"]) - float(base_by_id[r["task_id"]]["correct"]) for r in rows]
        paired[name + "_accuracy_difference_vs_baseline"] = {
            "mean_paired_difference": statistics.mean(diffs),
            "bootstrap_95_percentile_CI": bootstrap_mean_ci(diffs)
        }

    report = {
        "protocol_id": "entropy-introspection-pilot-001",
        "run_status": "COMPLETED_EXPLORATORY_PILOT",
        "scientific_claim": "No claim about consciousness or general introspection is supported by this pilot.",
        "interpretation_limit": (
            "This tests a narrow arithmetic choice task and a task-specific A/B activation bias. "
            "The probability assigned to A/B is a limited confidence proxy, not a verbal self-report. "
            "Results can reveal calibration changes under this intervention but cannot establish subjective experience."
        ),
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "model": MODEL_ID,
        "device": device,
        "seed": SEED,
        "layer_index_zero_based": LAYER_INDEX,
        "intervention_norm": delta_norm,
        "mean_layer_activation_norm": mean_activation_norm,
        "conditions": summaries,
        "paired_accuracy_differences": paired,
        "raw_results": raw,
        "elapsed_seconds": round(time.time() - started, 2),
        "runtime": {"torch": torch.__version__, "transformers": __import__("transformers").__version__}
    }
    out_file = OUT / "model_experiment.json"
    out_file.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "raw_results"}, ensure_ascii=False, indent=2))
    print(f"Raw per-item data: {out_file}")

if __name__ == "__main__":
    main()
