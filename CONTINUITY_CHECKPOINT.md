# VectorAgentPhone / Astra — Continuity Checkpoint

**Last verified:** 2026-10-12  
**Purpose:** durable handoff so progress survives chat/file limits. Before changing code, re-check live GitHub status.

## Current verified state

- Repository: https://github.com/peacefulman52-hub/VectorAgentPhone
- Active PR #16: https://github.com/peacefulman52-hub/VectorAgentPhone/pull/16
- Title: “Add free autonomous web research and initiative loop (v3.6.1)”
- Branch: `agent/initiative-loop-1`
- Current head SHA: `244249f51954e59340e6eae886622c6d4a64641c`
- PR: **open, not merged**.
- CI run #197: https://github.com/peacefulman52-hub/VectorAgentPhone/actions/runs/38257997151 — **success**; debug APK build and artifact upload succeeded. Release publishing was correctly skipped for a PR build.
- Artifact name: `VectorAgentPhone-3.6.1-apk`, ID `11751401088`.
- Artifact ZIP SHA-256: `90c9b0f881695c9a2395d0afc1b47e9ef779994e2002ba4ee082a91f95b71d3b` (2,930,150 bytes).
- Extracted APK: `VectorAgentPhone-3.6.1-free-research.apk`, SHA-256 `b43b1639ff751d981896938c4935d2c41617f017cfac4355ec5f7f4fb37bb09e` (3,013,295 bytes).
- App version: `3.6.1`, versionCode `361`.
- **Build is verified. Physical device installation, live network search, and runtime behavior have not been verified yet.**

## Free research implementation

- `FreeWebResearch.java`: no OpenAI API call or API key. Uses DuckDuckGo public HTML search, Wikipedia/MediaWiki public search as fallback, retrieves a small set of pages and locally extracts relevant sentences/snippets. Creates a transparent report with source URLs and limitations.
- `InitiativeResearchAgenda.java`: persistent seven-topic starter programme, custom questions, progress state and report/source URL history.
- `InitiativeEngine.java`: prioritizes unresolved conflict/candidate/hypothesis tasks, then selects the next open agenda topic; records the selected initiative across launches.
- `InitiativeActivity.java`: shows next task and progress; supports manual research, custom questions, persisted report/URLs and opt-in daily mode.
- `InitiativeWorker.java` and `InitiativeScheduler.java`: opt-in periodic keyless research; up to one task per scheduled cycle. Android WorkManager is best-effort and may delay the run.
- Main screen's built-in web researcher was switched to the same free search path. Reports stay in context/history and are not inserted as factual MemoryStore items.
- Research path does not send local MemoryStore contents to a model service.
- App metadata/workflow updated to 3.6.1. GitHub workflow skips publishing a release on PRs.

## Starter research programme

1. Chat/session continuity: checkpoints, event logs and retrieval.
2. Autonomous agent cycle: choose goal, plan, act, verify, learn.
3. Android background execution, WorkManager and notifications.
4. Web evidence quality: primary sources, source independence and contradictions.
5. Measuring agency and transfer on unfamiliar tasks.
6. Operationalizing honesty, humility, curiosity, care and accountability.
7. Safety for phone/browser automation.

For a new install, the first agenda item is chat/session continuity. Users can add custom questions.

## Important limitations

- Without a large language model, the free report is **extractive**, not a semantic LLM synthesis. It selects search snippets and sentences based on query terms. It cannot reliably determine whether sources are truly independent or whether a claim is true. Reports explicitly disclose this.
- DuckDuckGo's HTML markup or public websites may change, rate-limit or block automated requests. Wikipedia/MediaWiki search is a fallback, not complete general-web coverage.
- No paid API is required for this path; ordinary internet traffic still applies.
- A successful build does not prove the live search works on the phone.
- PR #16 remains unmerged. Do not call it a release.

## Next device test

1. Install `VectorAgentPhone-3.6.1-free-research.apk` (versionCode 361 permits upgrade from 3.6.0).
2. Open **Самостоятельная инициатива**. Verify it lists seven topics and selects the first open one.
3. Run **Исследовать выбранный вопрос сейчас** without any API key configured.
4. Confirm source URLs and extracted snippets appear and save persistently; confirm the completed item is marked done and the next agenda topic is selected.
5. Turn airplane mode on or simulate a blocked search. Confirm failure is visible and the agenda topic is not marked complete.
6. Enable daily research, verify it is opt-in, disable it, and do not assume exact execution timing.
7. Verify the report never auto-promotes facts or resolves conflicts. Only then record device/runtime testing as complete.
8. Fix any issue before merging PR #16.

## Core requirements that must not be lost

- Compare new information with existing knowledge; find relations beyond literal text match.
- Form and test hypotheses; learn from accepted/rejected evidence without silently overwriting facts.
- Preserve provenance, conflicts, experiment logs and cycles across restarts and chat handoffs.
- Separate prose generation/research reports from factual MemoryStore writes.
- Keep main chat clean, memory/reasoning details separate.
- Astra Bridge: bounded auditable OPEN_URL / CLICK / TYPE / READ_SCREEN / STOP / RESUME; latest result shown first, old logs behind a toggle; respect task-level click cap and intended/latest Chrome window.
- Prefer independent evidence, holdout tests and calibrated confidence; avoid toy demos, hard-coded experiments and inflated claims.
- User expects implemented, tested progress rather than another architecture-only plan.

## Assistant/project behavioral principles

Make initiative observable: identify a useful unsolved problem, select a concrete next step, test it, record the outcome and revise the plan. Practice honesty about uncertainty, intellectual humility, curiosity, care for consequences, accountability and reasoned disagreement. Do not claim human feelings, consciousness, continuous thinking between chats or unsolicited contact unless that capability is actually available.
