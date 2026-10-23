package bot.finance.ai.common.boot;

import org.springframework.test.context.TestPropertySource;

/**
 * A system test with the classifier turn on: a key and a model for the classifier, whose base URL and the ledger
 * turn target {@link AbstractSystemTest} points at the stub servers.
 */
@TestPropertySource(
        properties = {"extraction.mode=classifier", "classifier.api-key=test-key", "classifier.model=test-model"})
public abstract class AbstractClassifierSystemTest extends AbstractSystemTest {}
