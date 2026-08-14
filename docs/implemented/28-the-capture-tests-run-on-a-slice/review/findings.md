# Review: the capture adapter tests boot the capture adapter, not the application

**1 refactoring candidate open. No open bugs.** Every entry is `ledger-service`. Every invariant under **What
must stay true** was kept, and all 17 scenarios were found running again by name.

## Refactoring candidate

| #  | Status | What                                                                   | Why the rework left it                                                                                                                                                                                                                                                                                                     |
|----|--------|------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1 | open   | Every boot annotation spells out the roles its neighbours already name | `@Testcontainers(disabledWithoutDocker)` with `@ImportTestcontainers(PostgresContainers)` is written out in five annotations; `@ActiveProfiles("test")` with those two and `@SpringBootTest(LedgerServiceApplication, RANDOM_PORT)` in three; `@DataJdbcTest` with `@AutoConfigureTestDatabase(replace = NONE)` in two. Naming each role would leave every annotation as a role plus its one distinguishing thing. This rework added the third of those pairs rather than inventing the grouping mid-step, and the change reaches annotations no step here touched. Approved as its own rework. **`@Transactional(NOT_SUPPORTED)` is not one of the roles** — it has one user, `PersistenceAdapterTest` wants the opposite, and it is the most surprising thing about a capture test, so it stays spelled out. **A shared containerized-database role must not carry `@ActiveProfiles("test")`**: `PersistenceAdapterTest` carries none today, and ten persistence classes would start loading `application-test.yaml` if it did. |

The measured saving was smaller than the context shrinking suggests — 85.1 s to 72.3 s across the three
classes. These are dominated by starting a real engine, burning 25 MB of WAL and awaiting entries off a real
Redis stream, none of which the slice touches. What the rework bought is isolation: a capture test can no
longer fail for something in the Telegram adapter.
