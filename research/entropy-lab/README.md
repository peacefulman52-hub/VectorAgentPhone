# Entropy Research Lab

This directory is the isolated research area for Entropy. It does not change the Android app or its factual MemoryStore.

## Current stage

**Stage 0 — reproducibility and preregistration.** The workflow validates the experiment specification and stores a machine-readable run manifest. This is infrastructure validation, **not evidence that a model is introspective**.

## Run locally

```bash
python research/entropy-lab/validate_protocol.py
```

The script writes `research/entropy-lab/results/protocol_validation.json`.

## Rules

- Separate observed measurements, interpretations, and hypotheses.
- Do not call a model's verbal self-description proof of introspection or consciousness.
- Compare the intervention against prompt-only, random same-norm, and no-intervention controls.
- Predefine outcomes and exclusion rules before inspecting results.
- Keep raw measurements, model/version identifiers, seeds, and failures.
- A successful CI run means the protocol file passed validation only.
- Never auto-promote experiment conclusions to established facts in the app.

## Next stage

Implement the model-backed intervention experiment only after the controls, outcomes, and success criteria are reviewed. No model-backed result is claimed by this initial setup.
