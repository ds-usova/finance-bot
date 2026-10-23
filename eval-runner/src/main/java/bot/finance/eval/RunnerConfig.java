package bot.finance.eval;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one run is pointed at: arguments choose the cases and the output, the environment names the services and
 * carries the secrets, which never reach an artifact.
 */
public record RunnerConfig(
        String label,
        int repetitions,
        List<String> casePrefixes,
        boolean budgetEnforced,
        Path corpusDir,
        Path outDir,
        String ledgerUrl,
        String ledgerMetricsUrl,
        String connectorMetricsUrl,
        String botToken,
        String aiDbUrl,
        String aiDbUser,
        String aiDbPassword,
        Path ledgerLog,
        Duration terminalTimeout,
        Duration readinessTimeout) {

    public static RunnerConfig from(String[] args, Map<String, String> env) {
        Map<String, String> options = new LinkedHashMap<>();
        boolean budgetEnforced = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--budget-enforced")) {
                budgetEnforced = true;
            } else if (arg.startsWith("--") && i + 1 < args.length) {
                options.put(arg.substring(2), args[++i]);
            } else {
                throw new IllegalArgumentException("unexpected argument " + arg);
            }
        }

        List<String> prefixes = new ArrayList<>();
        for (String prefix : options.getOrDefault("cases", "").split(",")) {
            if (!prefix.isBlank()) {
                prefixes.add(prefix.strip());
            }
        }

        String botToken = env.get("EVAL_TELEGRAM_BOT_TOKEN");
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException(
                    "EVAL_TELEGRAM_BOT_TOKEN must hold the TELEGRAM_BOT_TOKEN the ledger verifies sign-ins with");
        }
        String ledgerLog = env.getOrDefault("EVAL_LEDGER_LOG", "");

        return new RunnerConfig(
                options.getOrDefault("label", "run"),
                Integer.parseInt(options.getOrDefault("repetitions", "3")),
                prefixes,
                budgetEnforced,
                Path.of(options.getOrDefault("corpus", "docs/eval/corpus")),
                Path.of(options.getOrDefault("out", "build/eval")),
                env.getOrDefault("EVAL_LEDGER_URL", "http://localhost:1000"),
                env.getOrDefault("EVAL_LEDGER_METRICS_URL", "http://localhost:1010/actuator/prometheus"),
                env.getOrDefault("EVAL_CONNECTOR_METRICS_URL", "http://localhost:1011/actuator/prometheus"),
                botToken,
                env.getOrDefault("EVAL_AI_DB_URL", "jdbc:postgresql://localhost:5432/finance_ai"),
                env.getOrDefault("EVAL_AI_DB_USER", ""),
                env.getOrDefault("EVAL_AI_DB_PASSWORD", ""),
                ledgerLog.isBlank() ? null : Path.of(ledgerLog),
                Duration.ofSeconds(Long.parseLong(env.getOrDefault("EVAL_TERMINAL_TIMEOUT_SECONDS", "180"))),
                Duration.ofSeconds(Long.parseLong(env.getOrDefault("EVAL_READINESS_TIMEOUT_SECONDS", "120"))));
    }

    /** The run's settings without its secrets, as the output records them. */
    public Map<String, Object> redacted() {
        Map<String, Object> redacted = new LinkedHashMap<>();
        redacted.put("label", label);
        redacted.put("repetitions", repetitions);
        redacted.put("casePrefixes", casePrefixes);
        redacted.put("budgetEnforced", budgetEnforced);
        redacted.put("corpusDir", corpusDir.toString());
        redacted.put("ledgerUrl", ledgerUrl);
        redacted.put("ledgerMetricsUrl", ledgerMetricsUrl);
        redacted.put("connectorMetricsUrl", connectorMetricsUrl);
        redacted.put("aiDbUrl", aiDbUrl);
        redacted.put("ledgerLog", ledgerLog == null ? null : ledgerLog.toString());
        redacted.put("terminalTimeoutSeconds", terminalTimeout.toSeconds());
        redacted.put("readinessTimeoutSeconds", readinessTimeout.toSeconds());
        return redacted;
    }
}
