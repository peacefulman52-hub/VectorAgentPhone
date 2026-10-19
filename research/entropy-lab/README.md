# Entropy Research Lab

This directory is the isolated research area for Entropy. It does not change the Android app or its factual MemoryStore.

## Current stage

An **exploratory CPU pilot** uses `distilgpt2` on fixed-seed arithmetic multiple-choice tasks. It compares no intervention, an activation-stream bias toward option A, a random same-norm activation perturbation, and a prompt-only warning. GitHub Actions records per-item choices, probabilities, accuracy, Brier score, calibration summary, runtime versions, and paired accuracy differences.

This is a narrow probe of probability-based confidence calibration under a task-specific intervention. The option-A direction is not a universal introspection vector; token probability is not a complete verbal self-report; and no result can establish consciousness.

## Run locally

Install PyTorch and Transformers, then run:

```bash
python research/entropy-lab/validate_protocol.py
python research/entropy-lab/run_model_pilot.py
```

The model experiment downloads `distilgpt2` from Hugging Face. GitHub Actions runs it on CPU and uploads `model_experiment.json` as an artifact.

## Rules

- Separate observations, interpretations, and hypotheses.
- Do not call verbal self-description proof of introspection or consciousness.
- Compare the intervention against prompt-only, random same-norm, and no-intervention controls.
- Keep raw measurements, model/version identifiers, seeds, and failures.
- A successful workflow means the code ran; scientific interpretation still requires inspecting the metrics and limitations.
- Never auto-promote experiment conclusions to established facts in the app.
