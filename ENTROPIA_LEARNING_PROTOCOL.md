# Entropia Learning Protocol (v3.8.0)

## Purpose
Entropia is a local-first research agent. Its goal is not merely to produce fluent answers; it should preserve observations, discover candidate relations, make testable predictions, compare predictions with outcomes, and learn from errors without silently rewriting factual memory.

## Required loop
1. **Observe** — retain the original claim, source URL/domain where available, timestamp, and acquisition method.
2. **Compare** — compare with existing observations; identify duplicates, related claims, numeric/negation differences, and unresolved contradictions. Similarity is not truth.
3. **Hypothesize** — state a rule plus its supporting observation IDs, scope, expected outcome, and known counterexamples.
4. **Predict before testing** — record the prediction before the result is known. Keep training examples separate from held-out tests.
5. **Experiment safely** — prefer read-only observation, simulations, or explicitly authorized test devices. Do not propagate code, exploit devices, evade access controls, or perform destructive actions.
6. **Evaluate** — record observed outcome, prediction error, repeatability, and limitations. A failed experiment is useful evidence, not a reason to hide the run.
7. **Update cautiously** — revise hypothesis support/confidence from evidence; keep contradictory claims visible until resolved by appropriate evidence or user decision.
8. **Checkpoint** — persist the task, last completed step, observations, hypotheses, experiments, unresolved questions, and next action so a later app session or ChatGPT conversation can resume.
9. **Report** — distinguish VERIFIED, PRELIMINARY, HYPOTHESIS, SYNTHETIC, and FAILED/INCOMPLETE. Never present synthetic mutations or model-generated text as independent evidence.

## Memory boundaries
- Factual MemoryStore remains protected from the answer generator and unverified mentor text.
- ChatGPT mentor replies are advisory/preliminary until a person explicitly accepts a decision; accepting a decision does not itself verify an external-world fact.
- Every derived conclusion links to its underlying source-observation IDs. Derived conclusions are not independent evidence anchors.
- Empty search results, network errors, and parser failures do not mark a research task complete.
- Snapshots and exports should include model/learner state, exploration examples, checkpoints, decisions, and event history where supported.
- Record the exact test split and avoid training on holdout examples before scoring them.

## Autonomy and safety boundaries
- Autonomy means planning and evaluating bounded research steps, not unbounded control over a device or network.
- Device exploration must be confined to an isolated simulator or a device explicitly owned/authorized for testing.
- No self-replication, credential theft, stealth, persistence against an owner's wishes, or uncontrolled lateral movement.
- For browser/phone actions, keep explicit task scope, visible action logs, stop control, and conservative action limits.

## Acceptance criteria for the next implementation stage
- A repeatable research cycle has explicit stages: question → plan → source observations → relation/hypothesis → prediction → test → outcome → checkpoint.
- Reopening the app restores the checkpoint and does not duplicate observations or events.
- At least one regression test covers each: empty research remains incomplete; generated/synthetic text is not promoted to fact; a contradictory observation does not silently overwrite an existing one; a holdout is not used for training; checkpoint resume is idempotent.
- CI passing confirms code/tests/build only; a physical Android test is still required for browser, clipboard, sharing, and persistence behavior.
