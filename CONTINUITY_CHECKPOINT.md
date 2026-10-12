# VectorAgentPhone / Astra — Continuity Checkpoint

**Last verified:** 2026-10-12  
**Purpose:** durable handoff so implementation survives chat limits. Re-check live repository state before changing code.

## Current verified state

- Repository: https://github.com/peacefulman52-hub/VectorAgentPhone
- Active PR #16: https://github.com/peacefulman52-hub/VectorAgentPhone/pull/16
- Title: “Add persistent autonomous initiative loop and research agenda”
- Branch: `agent/initiative-loop-1`
- Latest PR head: `ede81115cb823fbff5b89554d90752aa241e7ed5`
- PR status: **open, not merged**.
- Latest CI run #182: https://github.com/peacefulman52-hub/VectorAgentPhone/actions/runs/38255633151
- CI outcome: **success**. GitHub Actions completed `assembleDebug` and uploaded the artifact. Publishing a release was skipped as expected for a PR run.
- Artifact name: `VectorAgentPhone-3.6.0-apk`, artifact ID `11750566071`.
- Artifact ZIP SHA-256: `206c35645fbf5a9aaf1f7048f0d553e52289d6874c51c09a408e0ba9e95e4786` (2,922,751 bytes).
- Extracted APK in this session: `/mnt/data/VectorAgentPhone-3.6.0-research.apk`; SHA-256 `abee77f38ce1b18856d189aaf27af4faaac81fc784188fa3eb1097f33e9f9b9b` (3,005,691 bytes).
- **Compile/build is verified; installation and runtime behavior on a physical Android device are not yet verified.**

## What PR #16 now adds

- `InitiativeEngine.java`: examines MemoryStore, prioritizes unresolved conflicts/candidates/hypotheses and falls back to the prepared research agenda when local memory has no open task.
- `InitiativeResearchAgenda.java`: persistent seven-topic starter programme, custom questions, progress state and a bounded report/URL history. Completed identical questions are skipped to avoid repeatedly spending API quota.
- `OpenAIWebTeacher.java`: initiative-specific `researchQuestion` path uses OpenAI Responses API with the `web_search` tool and model `gpt-5.5`. This path sends the selected question, not the full local fact memory. The legacy teacher method remains separate.
- `InitiativeActivity.java`: shows the next concrete question, the agenda and progress, allows adding a custom question, can start one research now, shows saved reports and clickable source URLs, and explicitly describes API usage.
- `InitiativeScheduler.java` / `InitiativeWorker.java`: user opt-in for daily background research (one task per scheduled run) and notifications. Requires a saved OpenAI API key; consumes API quota. It does not mutate fact memory or run browser/phone commands. Disable turns off the daily consent flag.
- Main screen entry point, WorkManager dependency, Gradle versionCode 360/versionName 3.6.0, manifest, workflow and README were updated. Release publishing runs only on a push to `main`, not on a pull request.

## Starter research agenda

1. Continuity across chats and sessions: checkpoints, event logs and retrieval.
2. A useful autonomous agent loop: choose, plan, act, verify, learn.
3. Android background execution limits, WorkManager and notifications.
4. Reliable web research: source independence, claim-level citations and contradictions.
5. Measurable agent autonomy and long-horizon evaluation.
6. Human-positive behavioral rules: honesty, humility, curiosity, care and accountability.
7. Safety controls for browser/phone automation.

For a new install, the first initiative should be “Преемственность между чатами”. More topics are queued; users can add custom questions after the starter agenda.

## Required first device test

1. Install `VectorAgentPhone-3.6.0-research.apk`.
2. In VECTOR → Settings, add a valid OpenAI API key that has API access. A ChatGPT subscription is not the same as API credit.
3. Open **Самостоятельная инициатива**. Confirm that it shows the next starter topic and all seven research goals, rather than an empty queue.
4. Press **Исследовать выбранный вопрос сейчас**. Confirm a report is saved, source URLs are visible/clickable, the topic is marked complete, and the next topic becomes selected.
5. Confirm the initiative research request sends only the selected question, not all local MemoryStore facts. Do not put passwords, tokens or secrets in research questions.
6. If enabling daily web research, confirm the UI explicitly mentions API quota, notifications permission behavior and the ability to disable. Test the next scheduled execution best-effort; Android may defer periodic work.
7. Verify that research never automatically promotes facts or resolves conflicts, and that the same successfully researched question is not requested every day.
8. Fix any device/runtime defect before merging PR #16. Do not call runtime verified until this test is actually performed.

## Limitations to keep visible

- The seven-topic backlog and web researcher create useful starting work; they are not proof of general autonomous intelligence or consciousness.
- Daily execution is opt-in and best-effort. ChatGPT itself still does not gain a persistent process that thinks between chats or starts a new ChatGPT conversation on its own.
- API research may use paid API quota. The initiative research path only sends its question, but the separate legacy IИ-учитель path can still include a bounded local-memory summary.
- PR #16 remains unmerged as of the last live check.

## Core product requirements

- Compare new knowledge with old; discover relations beyond literal text matches.
- Form hypotheses and test them against observations; learn from acceptance/rejection without silent overwrites.
- Preserve provenance, conflicts, experiment logs and cycles across restarts and chat handoffs.
- Separate answer generation from factual MemoryStore writes.
- Keep the main chat clean and detailed memory/reasoning in separate views.
- Astra Bridge: bounded, auditable OPEN_URL / CLICK / TYPE / READ_SCREEN / STOP / RESUME; show latest result by default, older logs behind a toggle; preserve the task-level click limit and handle the intended/latest Chrome window.
- Favor independent evidence, holdout tests and calibrated confidence; avoid toy demos, hard-coded experiments and inflated claims.
- User wants productive, implemented progress rather than more architectures described in prose.

## Behavioral principles for the assistant/project

Demonstrate initiative as observable behavior: identify a useful unsolved problem, choose a concrete next step, test it, record the outcome and update the plan. Apply honesty about uncertainty, intellectual humility, curiosity, care for consequences, accountability for mistakes, and reasoned disagreement. Do not claim human feelings, consciousness, continuous thought between chats, or unsolicited contact unless the capability is actually present.
