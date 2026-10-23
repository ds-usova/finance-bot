package bot.finance.eval;

import bot.finance.eval.corpus.CaseSelection;
import bot.finance.eval.corpus.Corpus;
import bot.finance.eval.execution.CaseExecutor;
import bot.finance.eval.observe.MemoryProbe;
import bot.finance.eval.observe.Prometheus;
import bot.finance.eval.observe.TargetMetrics;
import bot.finance.eval.observe.TurnCallLog;
import bot.finance.eval.report.RunOutput;
import java.net.http.HttpClient;
import java.time.Duration;

/** Builds a corpus run from its configuration: what to run, what reaches the services, and where it is written. */
final class Wiring {

    private Wiring() {}

    static CorpusRun corpusRun(RunnerConfig config) {
        CaseSelection selection =
                CaseSelection.of(Corpus.load(config.corpusDir()), config.casePrefixes(), config.budgetEnforced());
        RunOutput output = RunOutput.open(config.outDir(), config.label(), config.redacted(), selection.blocked());
        return new CorpusRun(selection, config.repetitions(), executor(config), output);
    }

    private static CaseExecutor executor(RunnerConfig config) {
        HttpClient http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return new CaseExecutor(
                config,
                http,
                new TargetMetrics(new Prometheus(http), config.ledgerMetricsUrl(), config.connectorMetricsUrl()),
                new MemoryProbe(config.aiDbUrl(), config.aiDbUser(), config.aiDbPassword()),
                config.ledgerLog() == null ? null : new TurnCallLog(config.ledgerLog()));
    }
}
