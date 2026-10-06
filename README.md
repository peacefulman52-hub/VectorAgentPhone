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
