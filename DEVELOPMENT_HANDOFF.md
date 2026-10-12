# Astra / VectorAgentPhone — Development Handoff
Updated: 2026-10-12

This file is the durable handoff for work that must survive conversation limits. Update it at meaningful milestones; do not treat successful compilation as proof of runtime behavior.

## Current state

- Active repository: https://github.com/peacefulman52-hub/VectorAgentPhone
- Active work: PR #16, "Free web research with persistent preliminary finding clusters (v3.6.5)"
- PR URL: https://github.com/peacefulman52-hub/VectorAgentPhone/pull/16
- Working branch: `agent/initiative-loop-1`
- App version: `3.6.5`, `versionCode 365` so it can upgrade an installed `3.6.4` build.
- Code changes include compact search queries, provider fallback, a persistent source-attributed research store, a cautious extractive takeaway per successful report, basis links to source-observation IDs, and a UI panel showing topical clusters and confidence.
- Validated v3.6.5 candidate SHA: `af7e36b667510aa7bc7b13b0a4bf5726d407a755`.
- GitHub Actions run #260 passed `testDebugUnitTest`, `assembleDebug`, and artifact upload for that exact SHA: https://github.com/peacefulman52-hub/VectorAgentPhone/actions/runs/38265896060
- Artifact: `VectorAgentPhone-3.6.5-apk`, 2,947,074 bytes, artifact ID `11755136084`, SHA-256 `0f5a56bd080baa1bd2a86fb8106f5b7d0b60755bccd600e1626cbb5d358973b3`, available from the run’s Artifacts section.
- This validated SHA includes source-cluster takeaway capture, heuristic-score UI wording, and stable-ID `LOCAL_SYMBOLIC_HYPOTHESIS` upsert from MemoryStore with tests for no fake URLs and repeated-scan idempotency.
- Physical-device verification is still outstanding. This APK is ready for the phone smoke test, but the live free-search path has not been verified on-device.

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
8. The Initiative UI has a visible panel for accumulated preliminary clusters, showing status, heuristic score (explicitly not a probability of truth), relation type, source domain, and basis IDs.
9. After successful source-attributed extraction, `InitiativeResearchAgenda.saveResult()` records one modest extractive takeaway using `draftConservativeConclusion(...)` + `addPreliminaryConclusion(...)`. The takeaway quotes a real snippet, links to a raw source observation, and is never an independent evidence anchor. This is not free-form or LLM-level reasoning.
10. `ResearchKnowledgeStore.upsertLocalHypothesis(...)` mirrors `MemoryStore.discoverHypotheses()` output into the same graph using stable IDs `local-hypothesis:<id>`; repeated scans update the same item in place. Hypothesis records include local rule/evidence/status/support/version as basis, set `sourceUrl=""` and `evidenceUrls=[]` absent real source provenance, and cannot become evidence anchors because `findCluster()` only matches `FREE_WEB_EXTRACT`.
11. App version is 3.6.5 / versionCode 365. CI runs `testDebugUnitTest` before `assembleDebug`. Robolectric tests cover footer and source URL attribution/fallback, exact duplicate idempotency, related-but-distinct clustering, numeric/negation warnings, empty extraction, takeaway basis links, and stable local-hypothesis upsert/rejection/repeated scans without invented URLs.

Important mismatch to remember:
- The new branch's failure message should start with "Бесплатное веб-исследование не удалось:" and include provider diagnostics such as DDG HTML, DDG Lite, Bing, Wikipedia RU, Wikipedia EN.
- The older generic message "Бесплатный поиск не вернул доступных результатов..." is not present in the current PR branch's `FreeWebResearch.java`. If that exact old message appears on the phone, first verify which APK was installed; do not assume the latest artifact was used.

## Next actions

1. The current v3.6.5 candidate passed offline regression tests, APK assembly, and artifact upload on exact SHA `af7e36b667510aa7bc7b13b0a4bf5726d407a755`: https://github.com/peacefulman52-hub/VectorAgentPhone/actions/runs/38265896060.
2. Download `VectorAgentPhone-3.6.5-apk` from run #260’s Artifacts section (artifact ID `11755136084`, SHA-256 `0f5a56bd080baa1bd2a86fb8106f5b7d0b60755bccd600e1626cbb5d358973b3`); don’t use an older SHA’s artifact.
3. Install over the prior app on a physical Android phone and run a concrete Initiative research question.
4. Confirm the report contains usable source excerpts, each excerpt has the correct per-block URL, related-but-distinct observations remain separate cluster members, and the derived takeaway’s `basisObservationId` points to a raw source observation rather than another takeaway.
5. Verify the local symbolic hypothesis appears with stable `basisHypothesisId=local-hypothesis:<id>`, no invented source/evidence URLs, and that changing its status/repeated scans update the same record without growing observation count or repeatCount.
6. Close/reopen the app and verify cluster IDs, observation IDs and basis links persist. Test an empty/unattributed result: observation count must be zero and the topic must remain incomplete. Record exact provider diagnostics; network errors suggest phone network/VPN/filtering, while empty parsing after HTTP 200 suggests parser/markup issues.
7. This CI candidate is ready for device validation; do not call live web search runtime-verified until the smoke test is recorded.

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
- The current takeaway is deliberately extractive and meta-level, not free-form reasoning. A richer local rule/hypothesis stage is still a later milestone; keep its derived outputs separate from independent web evidence.
- Avoid duplicate ingestion: all manual and background callers must use `InitiativeResearchAgenda.saveResult` as the sole gateway to `ResearchKnowledgeStore`.
- Prefer checkpoints and reproducible tests over relying on conversation history alone.
