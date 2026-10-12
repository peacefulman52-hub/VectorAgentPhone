# VectorAgentPhone / Astra — Continuity Checkpoint

**Last updated:** 2026-10-12  
**Purpose:** durable technical handoff so progress survives chat limits. Re-check live GitHub status before further changes.

## Current state

- Repository: https://github.com/peacefulman52-hub/VectorAgentPhone
- Active work: PR #16 — https://github.com/peacefulman52-hub/VectorAgentPhone/pull/16
- Title: “Add persistent autonomous initiative loop”
- Branch: `agent/initiative-loop-1`
- Latest known head SHA: `8c811ab1531264525cc77e77ac36e319edc8e38e`
- Status at checkpoint: PR open, not merged.
- CI run #168: https://github.com/peacefulman52-hub/VectorAgentPhone/actions/runs/38253862975 — completed successfully. `assembleDebug` and artifact upload succeeded; release publishing was skipped because this was a PR build.
- GitHub artifact: `VectorAgentPhone-3.6.0-apk`, ID `11749363304`.
- Downloaded APK SHA-256: `a9210856b54400c471f2a64140dd164de294c55509700885e030a75eab5daa28` (2,994,495 bytes).
- Archive SHA-256: `09d8e77519564a27eb240167f423b03a9f2530c3f00d49f97e75fd145e3443e2`.
- Build is verified; **on-device behavior is not yet verified**.

## What PR #16 adds

1. `InitiativeEngine.java` scans local MemoryStore, prioritizes conflicts, then candidates, then open hypotheses, and saves the latest report.
2. `InitiativeScheduler.java` enables/disables a user-controlled periodic WorkManager scan and supports scan-now.
3. `InitiativeWorker.java` scans local state and may notify about a changed/overdue initiative. It does not mutate facts or act in other apps.
4. `InitiativeActivity.java` presents report and enable/disable/scan controls.
5. Main screen gets an entry button. Gradle adds WorkManager and bumps metadata to v3.6.0. Manifest declares notification permission/activity. README documents the feature. Workflow names the artifact v3.6.0 and only publishes releases on push to `main`.

## Known limitations

- This is a rule-based queue for choosing unresolved tasks, not yet a general autonomous research agent.
- No independent web research is performed in the background.
- No external phone/browser actions are executed by the worker.
- WorkManager is best-effort; Android may delay scheduled execution for battery management.
- Notifications require user permission on Android 13+.
- PR remains unmerged.

## Next steps

1. Inspect the diff in PR #16.
2. Install the APK on the Android test phone and open “Самостоятельная инициатива”.
3. Press “Проверить сейчас”; verify the selected task and timestamp.
4. Enable daily checks; grant notification permission if appropriate; inspect behavior, then verify disable works.
5. Test selection priority with disposable records and verify no automatic fact promotion / no external actions.
6. Fix any defects before merging. Only describe runtime behavior as verified after device tests.
7. Update this file and the private chat handoff at each meaningful milestone.

## Core product requirements

- Compare new information with existing knowledge; detect relations beyond literal text matches.
- Form and test hypotheses; learn from accepted/rejected outcomes without silently overwriting facts.
- Preserve provenance, contradictions, experiment logs and cycles across restarts.
- Keep answer generation separate from writes to factual MemoryStore.
- Keep chat UI clean and detailed memory/reasoning in separate views.
- Astra Bridge should control the phone/browser through bounded, auditable commands such as OPEN_URL, CLICK, TYPE, READ_SCREEN, STOP/RESUME; latest result shown by default, full log behind a toggle.
- Prefer out-of-sample tests and independent datasets; avoid toy examples, hard-coded experiments, and overstated confidence.
- User wants actual shipped/verified progress, not plans described as accomplishments.

## Agent behavior principles

Treat initiative as an observable capability: locate a useful unresolved problem, choose a concrete next step, test it, record the outcome and revise the plan. Apply honesty about uncertainty, intellectual humility, curiosity, care for consequences, accountability for mistakes and reasoned disagreement. Do not claim human feelings, consciousness, continuous thinking between chats, or unsolicited chat initiation unless those capabilities are actually available.
