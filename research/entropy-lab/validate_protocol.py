#!/usr/bin/env python3
"""Validate the preregistered Entropy pilot protocol; this does not run a model."""
import json
from pathlib import Path
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parent
protocol_path = ROOT / "protocol.json"
out_path = ROOT / "results" / "protocol_validation.json"

protocol = json.loads(protocol_path.read_text(encoding="utf-8"))
required = {
    "protocol_id", "title", "status", "research_question", "hypotheses",
    "conditions", "primary_outcomes", "controls", "analysis_plan", "exclusions"
}
missing = sorted(required - protocol.keys())
assert not missing, f"Missing protocol fields: {missing}"
assert protocol["status"] == "PREREGISTERED_NOT_YET_RUN", "Unexpected status; review before proceeding"
assert len(protocol["conditions"]) >= 3, "Need multiple comparison conditions"
assert "no_intervention" in protocol["conditions"], "Missing baseline control"
assert any("random" in condition for condition in protocol["conditions"]), "Missing random-intervention control"
assert protocol["hypotheses"], "At least one hypothesis is required"
assert protocol["primary_outcomes"], "At least one primary outcome is required"
assert protocol["controls"], "Controls must be documented"
assert protocol["analysis_plan"], "Analysis plan must be documented"
assert protocol["exclusions"], "Exclusion rules must be documented"

out_path.parent.mkdir(parents=True, exist_ok=True)
result = {
    "protocol_id": protocol["protocol_id"],
    "validation": "PASS",
    "validation_scope": "protocol_structure_only",
    "model_experiment_run": False,
    "scientific_result": None,
    "note": "A passing validator is not evidence for or against introspection.",
    "validated_at_utc": datetime.now(timezone.utc).isoformat(),
    "conditions": protocol["conditions"],
    "primary_outcomes": protocol["primary_outcomes"],
}
out_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(result, ensure_ascii=False, indent=2))
