# Vector Agent Phone 1.6.2

Android prototype for controlled, provenance-aware memory.

## v0.3.0

The memory model now keeps more than the text itself:

- RAW input with source, creation time and version;
- confidence and lifecycle: CANDIDATE → ACTIVE / REJECTED / CONFLICT;
- relations between memory items;
- explicit provenance metadata instead of silent replacement;
- version/history records for status and metadata changes;
- conflict detection is advisory and never overwrites existing memory;
- a small Test Lab for checking contradiction handling;
- encrypted local storage, snapshot/rollback and JSON import/export;
- Android Keystore-backed secret storage;
- offline-by-default design.

v0.9.2 adds a controlled self-learning loop: repeated similar non-conflicting observations accumulate support; after three supporting observations a candidate can become ACTIVE automatically. Direct contradictions remain CONFLICT until resolved explicitly. This is symbolic state learning, not neural-weight training.

## Build

GitHub Actions produces an installable debug APK.

## v3.6.2 — More resilient free initiative research

- An opt-in Android WorkManager cycle stores and notifies about the next useful task.
- A persistent starter programme includes seven topics: chat/session continuity, agent loops, Android background limits, evidence quality, agent evaluation, measurable human-positive behavior, and phone-automation safety.
- The Initiative screen lists goals and progress, accepts custom questions, runs a research task on demand, and saves reports plus source URLs.
- Research tries DuckDuckGo HTML, DuckDuckGo Lite, Bing, and Russian/English Wikipedia public search APIs, falling back when a provider returns no results. The app shows provider diagnostics when all fail. **No OpenAI API key, paid API plan, or API credit is required.** Normal internet traffic still applies; public sites may rate-limit or change their markup.
- The phone downloads a limited number of pages and builds an extractive report locally from relevant snippets/sentences. It is not an LLM-generated synthesis and cannot reliably establish source independence or truth; sources must be checked manually.
- Initiative research sends no local MemoryStore contents to an AI service. Reports are stored separately and never auto-promote facts or resolve conflicts.
- Successfully researched identical questions are skipped to avoid repeated requests. Daily mode is opt-in and runs at most one task per scheduled WorkManager cycle.
- WorkManager is best-effort and Android may delay periodic execution. Notifications require permission on Android 13+.

## v1.5.0

Adds a separate persistent learning model alongside MemoryStore:

- prediction of whether two observations are contradictory;
- error-driven weight updates;
- persistent learned weights between launches;
- blind-transfer test on previously unseen entities;
- learned model state participates in Snapshot/Rollback;
- learning weights remain separate from the fact memory and text generator.

## v1.6.0

The generator now has a controlled exploration branch. It creates synthetic mutations from ACTIVE memory (negation flips, numeric perturbations, word-order noise and uncertainty prefixes), records them in a separate persistent exploration buffer, and uses them as labeled training experiences for the local learner.

Synthetic exploration is never treated as independent evidence and never auto-promotes a factual memory item. Snapshot/Rollback covers the exploration buffer together with memory and learned weights.

## v1.6.2

Fixes the clean blind-transfer experiment UI so its results remain visible after the test completes. The test evaluates holdout pairs without training on them and restores the previous persistent model afterward.


Vector Lab 2.0 CI trigger.


## Astra Bridge 3.2
Controlled Android execution layer: AccessibilityService, explicit OPEN_URL/CLICK/TYPE/READ_SCREEN commands, audit log, STOP/RESUME. Commands are executed only from the Astra Bridge UI in this prototype.

## CI trigger 3.3.1


CI trigger for Astra Bridge 3.3.3.
