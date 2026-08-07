# [Conventions](../conventions.md) > Follow-Up Work

Repository-wide, since every module is measured by the same tool and documented the same way:
[Follow-Up Work](../../../docs/conventions/follow-up.md).

Nothing about this module changes it. `plan-evidence.sh` takes `--module web-app` and measures the npm build,
its suite and its coverage the same way it measures a Gradle one, and the archiving pass writes this module's
[use cases](../usecases/) and the [contract](../contracts/out/ledger-session-api.md) it depends on, exactly as it
does for a service.
