package bot.finance.ai.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageStorePort;
import bot.finance.ai.common.MockedLoggerUtils;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PurgeMessagesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    private static final Duration MAX_AGE = Duration.ofDays(30);
    private static final int BATCH = 100;

    private MessageStorePort messageStorePort;
    private Clock clock;
    private Logger log;
    private LoggerFactory loggerFactory;

    @BeforeEach
    void setUp() {
        messageStorePort = mock(MessageStorePort.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        loggerFactory = mock(LoggerFactory.class);
        log = mock(Logger.class);
        when(loggerFactory.getLogger(any())).thenReturn(log);
    }

    private List<String> loggedWarnLines() {
        return MockedLoggerUtils.warnLines(log);
    }

    @Nested
    @DisplayName("constructing a PurgeMessagesUseCase")
    class Constructor {

        static Stream<Arguments> invalidMaxAgeOrBatch() {
            return Stream.of(
                    Arguments.of(Duration.ZERO, BATCH),
                    Arguments.of(Duration.ofMinutes(-1), BATCH),
                    Arguments.of(MAX_AGE, 0),
                    Arguments.of(MAX_AGE, -1));
        }

        @ParameterizedTest
        @MethodSource("invalidMaxAgeOrBatch")
        @DisplayName("when maxAge or batch is zero or negative - then throws InvalidValueException")
        void whenMaxAgeOrBatchIsZeroOrNegative_thenThrowsInvalidValueException(Duration maxAge, int batch) {
            assertThatThrownBy(() -> new PurgeMessagesUseCase(messageStorePort, clock, maxAge, batch, loggerFactory))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("purging old messages")
    class Purge {

        @Test
        @DisplayName("when batches shrink to zero - then every delete uses the cut clock minus maxAge, batch "
                + "size, and stops at zero")
        void whenStoreAnswersFullBatchesThenFewerThenZero_thenEveryDeleteCarriesCutAndBatchAndStopsAtZero() {
            when(messageStorePort.deleteReceivedBefore(any(), anyInt())).thenReturn(BATCH, BATCH, 3, 0);
            PurgeMessagesUseCase useCase =
                    new PurgeMessagesUseCase(messageStorePort, clock, MAX_AGE, BATCH, loggerFactory);

            useCase.purge();

            Instant expectedCut = NOW.minus(MAX_AGE);
            verify(messageStorePort, times(4)).deleteReceivedBefore(eq(expectedCut), eq(BATCH));
        }

        @Test
        @DisplayName("when the store answers zero at once - then exactly one delete is asked for")
        void whenStoreAnswersZeroAtOnce_thenExactlyOneDeleteIsAskedFor() {
            when(messageStorePort.deleteReceivedBefore(any(), anyInt())).thenReturn(0);
            PurgeMessagesUseCase useCase =
                    new PurgeMessagesUseCase(messageStorePort, clock, MAX_AGE, BATCH, loggerFactory);

            useCase.purge();

            verify(messageStorePort, times(1)).deleteReceivedBefore(any(), anyInt());
        }

        @Test
        @DisplayName("when the store throws MessageStoreFailedException - then WARN logs, nothing propagates, "
                + "and purge retries later")
        void whenStoreThrowsMessageStoreFailedException_thenWarnLoggedNothingPropagatesAndNextPurgeDeletesAgain() {
            when(messageStorePort.deleteReceivedBefore(any(), anyInt()))
                    .thenThrow(new MessageStoreFailedException("store unreachable"))
                    .thenReturn(0);
            PurgeMessagesUseCase useCase =
                    new PurgeMessagesUseCase(messageStorePort, clock, MAX_AGE, BATCH, loggerFactory);

            assertThatCode(useCase::purge).doesNotThrowAnyException();

            assertThat(loggedWarnLines()).hasSize(1);

            useCase.purge();

            verify(messageStorePort, times(2)).deleteReceivedBefore(any(), anyInt());
        }
    }
}
