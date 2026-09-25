# Vector Agent Phone 0.2.0

Android prototype focused on controlled memory rather than neural-weight training.

## Memory model
RAW input is preserved as a memory item and enters CANDIDATE unless auto-learning is enabled and the confidence threshold is met. Candidates can be promoted to ACTIVE or moved to REJECTED.

Features: local encrypted memory, confidence threshold, auto-learning switch, snapshot/rollback, JSON import/export, Android Keystore-backed secret storage, offline-by-default design.

OpenAI-compatible networking is intentionally not enabled in this build. Local GGUF/llama.cpp is a planned next layer.

Build: GitHub Actions produces an installable debug APK.
