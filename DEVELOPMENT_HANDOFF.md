# Astra / VectorAgentPhone — Development Handoff
Updated: 2026-10-12

This file is the durable handoff for work that must survive conversation limits. Update it at meaningful milestones; do not treat successful compilation as proof of runtime behavior.

## Current state

- Active repository: https://github.com/peacefulman52-hub/VectorAgentPhone
- Active work: PR #16, "Harden free research and cluster preliminary findings (v3.6.4)"
- PR URL: https://github.com/peacefulman52-hub/VectorAgentPhone/pull/16
- Working branch: `agent/initiative-loop-1`
- App version: `3.6.4`, `versionCode 364` so it can upgrade `3.6.3`/earlier installs.
- Code changes include compact search queries, provider fallback, a persistent source-attributed research store, and a UI panel showing topical clusters and confidence.
- Latest code commit in this checkpoint: `d9acec20d8a284d3cd12aca8b7b2adb0e84a928b`; app version 3.6.4 / versionCode 364.
- CI run #228 for the cluster UI commit was queued at checkpoint creation: https://github.com/peacefulman52-hub/VectorAgentPhone/actions/runs/38263612292. Earlier run #226 succeeded but predates the new agent-conclusion API: https://github.com/peacefulman52-hub/VectorAgentPhone/actions/runs/38263179986.
- **The new build has not been verified on the physical phone. Do not call web research fixed until a real-device test succeeds.**

## Latest bug and code changes

The user installed/tested the free web-research build and reported: "Проверил. Не работает." The previous attempt depended on parsing public DuckDuckGo HTML, with fallback providers added later. CI success did not imply that web access works from Android.

Changes committed to PR #16:
1. Convert the long agenda brief into a compact keyword query before calling public search providers.
2. Use fallback providers when there are too few results with usable snippets.
3. Keep provider diagnostics visible on search failure.
4. Save every extractable source statement as a separate `PRELIMINARY` observation with URL, source domain, question, time, and heuristic confidence 0.25.
5. Cluster similar-but-not-identical observations as separate members. Numeric/negation variants can be labelled `POSSIBLE_CONTRADICTION_OR_CONDITION`; clustering never proves truth or merges claims.
6. `ResearchKnowledgeStore` is the only ingestion path. `InitiativeResearchAgenda.saveResult` invokes it exactly once; Activity and Worker must not call `ingestReport` again directly.
7. A report with no usable extracted finding can be saved as a diagnostic, but has observation count zero and cannot mark the topic complete or suppress future research.
8. The Initiative UI has a visible panel for accumulated preliminary clusters, showing status, confidence, relation type, and source domain.
9. `ResearchKnowledgeStore.addPreliminaryConclusion(...)` exposes an entry point for agent-authored tentative conclusions with basis, evidence URLs, and bounded confidence. It is **not yet called by an autonomous synthesis stage**; current web research extracts source statements rather than generating free-form synthesis.
10. App version is 3.6.4 / versionCode 364; artifact/release metadata and README reflect the current version.

Important mismatch to remember:
- The new branch's failure message should start with "Бесплатное веб-исследование не удалось:" and include provider diagnostics such as DDG HTML, DDG Lite, Bing, Wikipedia RU, Wikipedia EN.
- The older generic message "Бесплатный поиск не вернул доступных результатов..." is not present in the current PR branch's `FreeWebResearch.java`. If that exact old message appears on the phone, first verify which APK was installed; do not assume the latest artifact was used.

## Next actions

1. Check the newest GitHub Actions run including commit `d9acec20...`; confirm build and artifact upload both succeed.
2. Download `VectorAgentPhone-3.6.4-apk` from the newest successful Actions run on the PR branch, not from an older artifact or Releases page.
3. Install over the previous version and repeat the same Initiative question on the phone.
4. Confirm both behaviours: (a) a usable report appears and the task advances; (b) preliminary observations appear in the cluster panel with URL/source attribution, and a similar-but-not-identical later statement joins the cluster as a separate member.
5. If no observations are extracted, confirm the task remains incomplete. Record the exact diagnostics. If all providers show network errors, investigate phone network/VPN/filtering; if providers return HTTP 200 with zero parsed results, inspect parser markup.
6. Next reasoning milestone: wire `ResearchKnowledgeStore.addPreliminaryConclusion(...)` into a real local rule/hypothesis stage; do not claim autonomous synthesis until a caller uses this method.
7. Only after physical-device verification decide whether PR #16 is ready to merge and publish a release.

## Long-term project goals (do not lose)

- Astra / VectorAgentPhone should pursue useful tasks, not merely echo user input.
- Compare incoming information against stored knowledge; discover relations, form hypotheses, and test them.
- Learn from user confirmation/rejection while keeping factual MemoryStore protected from the answer generator.
- Keep the main chat simple and put detailed reasoning/memory in separate views.
- Persist research reports, source URLs, experiment cycles, and project decisions across app launches and chat sessions.
- No paid API requirement for this free research path. Be explicit about limits: local extractive snippets are not LLM-level synthesis and do not prove truth or source independence.
- Failed/empty research must never be marked completed, promoted to fact, or used to resolve a conflict automatically.
- Keep weak research observations: `PRELIMINARY` is the intended state, not a failure. Prefer a low confidence plus provenance over discarding a plausible clue.
- Clusters organize topic similarity, not truth. Similar claims remain separate records, and potential numeric/negation contradictions remain reviewable.
- The current source-only research cycle does not yet generate novel prose conclusions. The new agent-authored conclusion API is a foundation for the next local reasoning milestone.
- Avoid duplicate ingestion: all manual and background callers must use `InitiativeResearchAgenda.saveResult` as the sole gateway to `ResearchKnowledgeStore`.
- Prefer checkpoints and reproducible tests over relying on conversation history alone.
