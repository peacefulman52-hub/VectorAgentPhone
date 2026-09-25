# Vector Agent Phone 0.9.2

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
