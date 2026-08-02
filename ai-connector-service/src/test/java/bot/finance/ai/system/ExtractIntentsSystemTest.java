package bot.finance.ai.system;

import bot.finance.ai.common.AbstractSystemTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

/**
 * Entered over the real Netty channel {@link AbstractSystemTest} binds, so a passing happy path also proves the
 * server binds and serves — the in-process transport {@code @GrpcAdapterTest} uses would have hidden that.
 */
class ExtractIntentsSystemTest extends AbstractSystemTest {

    @Nested
    @DisplayName("happy path")
    class HappyPath {

    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

    }

}
