# Vector Agent Phone 0.3.0

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

This release is still a memory/state prototype. It does not train neural weights on the phone and does not yet include the dialogue agent planned for a later release.

## Build

GitHub Actions produces an installable debug APK.
