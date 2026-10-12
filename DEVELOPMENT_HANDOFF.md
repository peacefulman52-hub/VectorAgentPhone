# Astra / VectorAgentPhone — Development Handoff
Updated: 2026-10-12

This file is the durable handoff for work that must survive conversation limits. Update it at meaningful milestones; do not treat successful compilation as proof of runtime behavior.

## Current state

- Active repository: https://github.com/peacefulman52-hub/VectorAgentPhone
- Active work: PR #16, "Harden free autonomous web research and initiative loop (v3.6.3)"
- PR URL: https://github.com/peacefulman52-hub/VectorAgentPhone/pull/16
- Working branch: `agent/initiative-loop-1`
- App version being built: `3.6.3`, `versionCode 363` so it can upgrade `3.6.2`.
- Latest source commit at checkpoint creation: `6d6a510da9af3b92206409ea7d6435884a7b729c`.
- CI run #204 is in progress at checkpoint creation: https://github.com/peacefulman52-hub/VectorAgentPhone/actions/runs/38261684143
- Last confirmed successful build before the latest hardening was run #202, an Actions artifact from the PR branch, not a published v3.6.2 release.
- **The new search code has not been verified on the physical phone. Do not call the issue fixed until a real-device test succeeds.**

## Latest bug and code changes

The user installed/tested the free web-research build and reported: "Проверил. Не работает." The previous attempt depended on parsing public DuckDuckGo HTML, with fallback providers added later. CI success did not imply that web access works from Android.

Changes committed to PR #16:
1. Convert the long agenda brief into a compact keyword query before calling public search providers.
2. Decide whether to query fallback providers based on the count of hits with usable snippets, not just the number of parsed links.
3. Do not save/complete a research topic when the only results are empty links and no readable page text or nonempty snippets were extracted.
4. Keep diagnostics from the attempted providers in failure messages.
5. Bump version to 3.6.3 / versionCode 363; update artifact/release labels and README.

Important mismatch to remember:
- The new branch's failure message should start with "Бесплатное веб-исследование не удалось:" and include provider diagnostics such as DDG HTML, DDG Lite, Bing, Wikipedia RU, Wikipedia EN.
- The older generic message "Бесплатный поиск не вернул доступных результатов..." is not present in the current PR branch's `FreeWebResearch.java`. If that exact old message appears on the phone, first verify which APK was installed; do not assume the latest artifact was used.

## Next actions

1. Check the latest GitHub Actions run after commit `6d6a510...`; confirm both the build and artifact upload succeeded.
2. Download `VectorAgentPhone-3.6.3-apk` from that exact workflow run, not the Releases page. The PR is still open and has not been merged.
3. Install over 3.6.2 and test the same Initiative question on the phone.
4. Record the exact status/error and provider diagnostics. If all sources report HTTP/network errors, investigate the phone's network/VPN/filtering. If providers return zero parsed results with HTTP 200, inspect parser/markup handling. Do not make another blind source-parser patch without this distinction.
5. Only after device verification decide whether PR #16 is ready to merge and publish a release.

## Long-term project goals (do not lose)

- Astra / VectorAgentPhone should pursue useful tasks, not merely echo user input.
- Compare incoming information against stored knowledge; discover relations, form hypotheses, and test them.
- Learn from user confirmation/rejection while keeping factual MemoryStore protected from the answer generator.
- Keep the main chat simple and put detailed reasoning/memory in separate views.
- Persist research reports, source URLs, experiment cycles, and project decisions across app launches and chat sessions.
- No paid API requirement for this free research path. Be explicit about limits: local extractive snippets are not LLM-level synthesis and do not prove truth or source independence.
- Failed/empty research must never be marked completed, promoted to fact, or used to resolve a conflict automatically.
- Prefer checkpoints and reproducible tests over relying on conversation history alone.


## Research-to-memory bridge checkpoint (2026-10-12)

A companion branch, `agent/research-evidence-cluster-bridge`, is being used to add the missing integration without overwriting PR #16's working branch.

Design:
- Parse only the source-specific `Фрагменты страницы` / `Фрагмент поисковой выдачи` blocks from a successful report.
- Store each extractive finding as a low-confidence `CANDIDATE` with its source URL, question and timestamp.
- Attach exact/near duplicate findings as additional evidence to an existing item, without changing that item's status or support score.
- Add explicit `SIMILAR_TO` / `POSSIBLE_CONTRADICTION_TO` cluster links for related but non-identical findings.
- Distinct domains are not assumed independent and never auto-promote facts.
- Manual and scheduled research use the same bridge and report summary counts.

Verification still required:
1. Ensure GitHub Actions builds the bridge branch successfully.
2. Exercise one successful research report, one exact duplicate, one related non-identical finding, one possible contradiction, and an empty/error search on a physical Android device.
3. Verify no research finding changes an existing ACTIVE item to another status and that all stored claims remain traceable to source URLs.
4. Do not call web research fixed until the real-device network test succeeds.
