# The Red Exit Check

What the orchestrator verifies once every red-phase item is ticked or recorded as blocked, before any green step
is spawned. Run the module's **full test suite** once and compare it against the step agents' reports. The suite
must fail in **exactly the expected places**:

- **every pre-existing test still passes** — a pre-existing test now failing means a red agent's changes (an
  `update:` edit gone wrong, a broken shared fixture) caused a regression; re-delegate to the owning step;
- **the failing tests are exactly the new ones** the reports claim fail — a reported-red test that actually
  passes, and is not listed as a negative-assertion expected pass, asserts nothing real; re-delegate it as a
  defect;
- **the reported expected passes pass**, and nothing else about the new tests deviates from the reports;
- **the skipped count is back to the baseline** — every test stabilization disabled has been reworked by the
  step named in its reason. A test still skipped here is a step that silently skipped its own `update:` bullets.
  Compare against the baseline's number rather than against zero: a module whose infrastructure skips on its own
  (no container runtime, say) starts above zero and must return there, not below it;
- **nothing left the tree that no bullet authorized** — the total is the baseline, plus what the red steps added,
  less exactly the methods an `update: … — delete` bullet named. A total that does not reconcile is a red agent
  that dropped a test instead of reworking it.

Green does not start until this holds for every non-blocked item.
