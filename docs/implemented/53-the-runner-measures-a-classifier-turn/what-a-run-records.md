# What a Run Records

Every value is read in the window the runner already opens around a target turn: one scrape of each service
before the message is filed, one after the turn is inspected. A series absent from a scrape counts as zero.

## An Attempt

| Value              | Read from                                                                                                                                          | Unreadable source leaves |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------|
| mode               | which `mode` label of the connector's `ai_turns_total` grew in the window                                                                          | no mode                  |
| classifier calls   | growth of the connector's `ai_classifier_seconds` count, every outcome                                                                             | no value (AC16)          |
| classifier seconds | growth of the connector's `ai_classifier_seconds` sum, every outcome                                                                               | no value (AC16)          |
| ledger calls       | growth of the ledger's `ledger_mcp_tool_calls_total`, plus growth of its `grpc_server_seconds_count` for `bot.finance.ledger.v1.LedgerTurnService` | no value (AC16)          |

A window in which no `mode` label grew (AC14), or more than one did, leaves no mode.

## A Run

The run's mode sits in its summary.

| The modes its recorded attempts hold | The run's mode |
|--------------------------------------|----------------|
| one mode, with or without no-mode    | that mode      |
| two different modes                  | mixed          |
| none                                 | no mode        |

A call expectation is evaluated whatever the attempt's mode, as today.
