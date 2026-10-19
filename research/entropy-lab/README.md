# Entropy Research Lab

This directory is the isolated research area for Entropy. It does not change the Android app or its factual MemoryStore.

## Current experiment

The exploratory CPU pilot uses `distilgpt2` on a fixed-seed antonym-completion task. For prompts such as “The opposite of hot is”, it compares next-token likelihood for the correct antonym against the source word. Conditions are: no intervention; a residual-stream perturbation pushing against the model's own baseline winner; a random perturbation with the same norm; and a prompt-only uncertainty warning.

The run records per-item probabilities and correctness, accuracy, Brier score, expected calibration error, paired accuracy differences, model/runtime versions, and skipped multi-token candidates. GitHub Actions uploads the JSON results artifact.

## Important limits

This is a narrow test of probability-based calibration, not a direct verbal self-report and not a test of subjective experience. The targeted perturbation is task-specific and pushes against the baseline winning candidate; it is not a general-purpose introspection vector. Results cannot establish consciousness or general introspection. If baseline accuracy is near chance, that limits what can be concluded.

## Run locally

Install PyTorch and Transformers, then run:

```bash
python research/entropy-lab/validate_protocol.py
python research/entropy-lab/run_model_pilot.py
```

The model downloads from Hugging Face. GitHub Actions runs on CPU and retains the raw JSON artifact for 90 days.

## Rules

- Separate observations, interpretations, and hypotheses.
- Preserve raw measurements, seeds, model versions, and failures.
- Compare against no-intervention, random same-norm, and prompt-only controls.
- Do not treat model self-description or a successful workflow as proof of introspection.
- Never auto-promote experiment conclusions to established facts in the app.
