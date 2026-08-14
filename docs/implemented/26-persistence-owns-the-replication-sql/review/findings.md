# Review: every statement change capture runs moves to the persistence adapter

**Nothing open.** Every invariant under **What must stay true** was kept, and no step was abandoned.

Two assertions this run found proving nothing were repaired inside the step that wrote them, so neither is
carried forward:

- The two `underSlotLock` release scenarios took the lock a second time to show the first had let go. An advisory
  lock is re-entrant inside the session that holds it and the pool hands the same connection back, so both passed
  with the release removed. They read `pg_locks` for the advisory key now.
- `DatabaseConnectionDetailsTest`'s URL scenario asserted a prefix and a substring, which a fabricated URL also
  satisfies. It compares against the container's own URL now.
